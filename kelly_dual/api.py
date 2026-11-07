"""API local autenticada para a interface Kelly (stdlib, sem dependências externas).

O executor real é injetado por KELLY_EXECUTOR=modulo:funcao. A API não executa
shell diretamente e não oferece executor fictício. Sem adaptador, tarefas são
recusadas com HTTP 503 em vez de simular trabalho.
"""
from __future__ import annotations

import importlib
import json
import mimetypes
import os
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote, urlparse
from typing import Callable

from .orchestrator import TaskCoordinator, Task, TaskEvent, should_delegate_to_code
from . import providers
from .file_store import delete_upload, get_upload, list_uploads, readable_text, save_upload
from .sessions import SessionStore, run_slash_command

MAX_BODY = 12 * 1024 * 1024
MAX_PROMPT = 20_000


def load_executor() -> Callable:
    spec = os.environ.get("KELLY_EXECUTOR", "").strip()
    if not spec or ":" not in spec:
        raise RuntimeError(
            "Executor não configurado. Defina KELLY_EXECUTOR=modulo:funcao "
            "apontando para um adaptador do motor Overcode."
        )
    module_name, attr = spec.split(":", 1)
    module = importlib.import_module(module_name)
    executor = getattr(module, attr)
    if not callable(executor):
        raise TypeError("KELLY_EXECUTOR precisa apontar para uma função executora.")
    return executor


def safe_project_dir(raw: object, root: Path) -> str | None:
    if raw in (None, ""):
        return str(root)
    candidate = Path(str(raw)).expanduser()
    if not candidate.is_absolute():
        candidate = root / candidate
    resolved = candidate.resolve()
    if resolved != root and root not in resolved.parents:
        raise ValueError("A pasta do projeto precisa ficar dentro de OVEROS_PROJECT_ROOT.")
    if not resolved.is_dir():
        raise ValueError("A pasta do projeto não existe.")
    return str(resolved)


def make_handler(coordinator: TaskCoordinator, token: str, root: Path):
    sessions = SessionStore()

    class Handler(BaseHTTPRequestHandler):
        server_version = "KellyLocalAPI/1.0"

        def log_message(self, fmt, *args):
            # Não registrar cabeçalhos, tokens ou corpos de prompts.
            print("[kelly-api]", self.address_string(), fmt % args)

        def _json(self, code: int, payload: dict):
            raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(raw)

        def _authorized(self) -> bool:
            supplied = self.headers.get("Authorization", "")
            expected = "Bearer " + token
            if not secrets.compare_digest(supplied, expected):
                self._json(401, {"error": "Não autorizado."})
                return False
            return True

        def _body(self) -> dict:
            length = int(self.headers.get("Content-Length", "0"))
            if length < 1 or length > MAX_BODY:
                raise ValueError("Corpo ausente ou maior que 64 KB.")
            value = json.loads(self.rfile.read(length))
            if not isinstance(value, dict):
                raise ValueError("O corpo precisa ser um objeto JSON.")
            return value

        def do_GET(self):
            path = urlparse(self.path).path
            static_files = {
                "/": "index.html",
                "/index.html": "index.html",
                "/styles.css": "styles.css",
                "/app.js": "app.js",
                "/settings.js": "settings.js",
            }
            if path in static_files:
                ui_root = (Path(__file__).resolve().parent.parent / "ui").resolve()
                target = (ui_root / static_files[path]).resolve()
                if ui_root not in target.parents:
                    self._json(403, {"error": "Caminho inválido."})
                    return
                try:
                    raw = target.read_bytes()
                except OSError:
                    self._json(404, {"error": "Arquivo da interface não encontrado."})
                    return
                self.send_response(200)
                self.send_header("Content-Type", mimetypes.guess_type(str(target))[0] or "application/octet-stream")
                self.send_header("Content-Length", str(len(raw)))
                self.send_header("Cache-Control", "no-cache")
                self.end_headers()
                self.wfile.write(raw)
                return
            if path == "/api/health":
                self._json(200, {
                    "ok": True,
                    "service": "kelly-local-api",
                    "executor_configured": bool(os.environ.get("KELLY_EXECUTOR")),
                    "project_root": str(root),
                })
                return
            if not self._authorized():
                return
            if path == "/api/files":
                self._json(200, {"files": list_uploads(root)})
                return
            if path.startswith("/api/files/"):
                file_id = path[len("/api/files/"):].strip("/")
                found = get_upload(root, file_id)
                if not found:
                    self._json(404, {"error": "Arquivo não encontrado."})
                    return
                meta, target, raw = found
                self.send_response(200)
                self.send_header("Content-Type", meta.get("content_type", "application/octet-stream"))
                self.send_header("Content-Length", str(len(raw)))
                self.send_header("Content-Disposition", "attachment; filename*=UTF-8''" + quote(meta.get("name", "arquivo")))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                self.wfile.write(raw)
                return
            if path == "/api/capabilities":
                cfg = providers.public_config()
                self._json(200, {
                    "api": "online",
                    "chat": True,
                    "voice": {"browser_speech_recognition": True, "browser_speech_synthesis": True, "realtime_streaming": False},
                    "files": {"upload": True, "download": True, "max_bytes": 8 * 1024 * 1024},
                    "provider": {"name": cfg.get("provider"), "model": cfg.get("model"), "configured": cfg.get("has_api_key", False)},
                    "executor_configured": bool(os.environ.get("KELLY_EXECUTOR")),
                    "tools": ["chat", "task_queue", "task_events", "session_history", "file_upload", "file_download", "provider_settings", "health_status"]
                })
                return
            if path == "/api/sessions":
                self._json(200, {"sessions": sessions.list()})
                return
            if path.startswith("/api/sessions/"):
                session_id = path[len("/api/sessions/"):].strip("/")
                try:
                    self._json(200, {"id": session_id, "messages": sessions.get(session_id)})
                except ValueError as exc:
                    self._json(404, {"error": str(exc)})
                return
            if path == "/api/providers":
                self._json(200, providers.public_config())
                return
            if path.startswith("/api/tasks/") and path.endswith("/events"):
                task_id = path[len("/api/tasks/"):-len("/events")].strip("/")
                self._stream_events(task_id)
                return
            if path.startswith("/api/tasks/"):
                task_id = path[len("/api/tasks/"):].strip("/")
                task = coordinator.get(task_id)
                if not task:
                    self._json(404, {"error": "Tarefa não encontrada."})
                    return
                self._json(200, {
                    "id": task.id, "prompt": task.prompt, "status": task.status.value,
                    "created_at": task.created_at, "updated_at": task.updated_at,
                    "result": task.result, "error": task.error,
                })
                return
            self._json(404, {"error": "Rota não encontrada."})

        def do_POST(self):
            if not self._authorized():
                return
            path = urlparse(self.path).path
            try:
                body = self._body()
                if path == "/api/files":
                    name = str(body.get("name", ""))
                    encoded = str(body.get("content_base64", ""))
                    content_type = str(body.get("content_type", ""))[:160]
                    meta = save_upload(root, name, encoded, content_type)
                    self._json(201, meta)
                    return
                if path == "/api/providers":
                    self._json(200, providers.save_config(body))
                    return
                if path == "/api/providers/test":
                    self._json(200, providers.test_connection())
                    return
                if path == "/api/chat":
                    message = str(body.get("message", "")).strip()
                    if not message or len(message) > MAX_PROMPT:
                        raise ValueError("Informe uma mensagem de 1 a 20.000 caracteres.")
                    attachment_ids = body.get("attachments", [])
                    if not isinstance(attachment_ids, list) or len(attachment_ids) > 10:
                        raise ValueError("Envie no máximo 10 anexos por mensagem.")
                    attachment_context = []
                    attachment_names = []
                    for file_id in attachment_ids:
                        found = get_upload(root, str(file_id))
                        if not found:
                            raise ValueError("Um dos anexos não existe mais.")
                        meta, _, raw_file = found
                        attachment_names.append(meta.get("name", "arquivo"))
                        extracted = readable_text(meta, raw_file)
                        if extracted is not None:
                            attachment_context.append(
                                f"\n[CONTEÚDO DO ANEXO: {meta.get('name','arquivo')}]\n{extracted}\n[FIM DO ANEXO]"
                            )
                    if attachment_names:
                        message += "\n\nArquivos anexados: " + ", ".join(attachment_names)
                    if attachment_context:
                        message += "\n" + "\n".join(attachment_context)
                    elif attachment_names:
                        message += "\n[Os anexos binários foram armazenados, mas este provedor ainda não possui leitura multimodal habilitada.]"
                    history = body.get("history", [])
                    if not isinstance(history, list) or len(history) > 30:
                        raise ValueError("O histórico precisa ser uma lista com até 30 mensagens.")
                    safe_history = []
                    for item in history:
                        if not isinstance(item, dict) or item.get("role") not in {"user", "assistant"}:
                            raise ValueError("Mensagem inválida no histórico.")
                        content = str(item.get("content", ""))[:MAX_PROMPT]
                        if content:
                            safe_history.append({"role": item["role"], "content": content})
                    session_id = str(body.get("session_id", "")).strip()
                    if not session_id:
                        session_id = sessions.create()
                    else:
                        saved_messages = sessions.get(session_id)
                        if not safe_history:
                            safe_history = [
                                {"role": item["role"], "content": item["content"]}
                                for item in saved_messages[-30:]
                                if item["role"] in {"user", "assistant"}
                            ]
                    command_answer = run_slash_command(
                        message, safe_history, root, session_id, sessions,
                        providers.complete, providers.public_config
                    )
                    task_id = None
                    if command_answer is None and should_delegate_to_code(message):
                        context = safe_history[-12:]
                        shared_context = "\\n".join(
                            f"{item['role']}: {item['content']}" for item in context
                        )
                        delegated_prompt = (
                            "CONTEXTO COMPARTILHADO DA CONVERSA:\\n"
                            + shared_context + "\\n\\nTAREFA DO USUÁRIO:\\n" + message
                        )
                        task = coordinator.submit(delegated_prompt, str(root))
                        task_id = task.id
                        answer = (
                            "Entendi. Já passei essa tarefa para a Kelly Code. "
                            "Ela continuará trabalhando em segundo plano enquanto conversamos. "
                            f"Identificador: {task_id[:8]}."
                        )
                    elif command_answer is None:
                        answer = providers.complete(
                            message,
                            "Você é Kelly, assistente leve de conversa do OverCode. Responda em português brasileiro. "
                            "Kelly Code executa tarefas delegadas em segundo plano.",
                            safe_history
                        )
                    else:
                        answer = command_answer
                    sessions.append(session_id, "user", message)
                    sessions.append(session_id, "assistant", answer)
                    payload = {"answer": answer, "session_id": session_id}
                    if task_id:
                        payload.update({"task_id": task_id, "delegated": True})
                    self._json(200, payload)
                    return
                if path != "/api/tasks":
                    self._json(404, {"error": "Rota não encontrada."})
                    return
                prompt = str(body.get("prompt", "")).strip()
                if not prompt or len(prompt) > MAX_PROMPT:
                    raise ValueError("Informe uma tarefa de 1 a 20.000 caracteres.")
                project_dir = safe_project_dir(body.get("project_dir"), root)
                task = coordinator.submit(prompt, project_dir)
                self._json(202, {"id": task.id, "status": task.status.value})
            except ValueError as exc:
                self._json(400, {"error": str(exc)})
            except Exception as exc:
                self._json(500, {"error": "Não foi possível criar a tarefa.", "detail": str(exc)})

        def do_DELETE(self):
            if not self._authorized():
                return
            path = urlparse(self.path).path
            if path.startswith("/api/files/"):
                file_id = path[len("/api/files/"):].strip("/")
                if delete_upload(root, file_id):
                    self._json(200, {"deleted": True, "id": file_id})
                else:
                    self._json(404, {"error": "Arquivo não encontrado."})
                return
            if not path.startswith("/api/tasks/"):
                self._json(404, {"error": "Rota não encontrada."})
                return
            task_id = path[len("/api/tasks/"):].strip("/")
            if coordinator.cancel(task_id):
                self._json(202, {"id": task_id, "status": "cancel_requested"})
            else:
                self._json(409, {"error": "Tarefa inexistente ou já finalizada."})

        def _stream_events(self, task_id: str):
            if not coordinator.get(task_id):
                self._json(404, {"error": "Tarefa não encontrada."})
                return
            try:
                after = max(0, int(self.headers.get("Last-Event-ID", "0")))
            except ValueError:
                after = 0
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream; charset=utf-8")
            self.send_header("Cache-Control", "no-cache, no-transform")
            self.send_header("X-Accel-Buffering", "no")
            self.end_headers()
            last_heartbeat = time.monotonic()
            try:
                while True:
                    batch = coordinator.events(task_id, after)
                    for item in batch:
                        after = item["seq"]
                        event = {
                            "schema_version": 1,
                            "task_id": task_id,
                            "event_id": str(after),
                            "sequence": after,
                            "timestamp": item["at"],
                            "type": item["kind"],
                            "status": (coordinator.get(task_id).status.value
                                       if coordinator.get(task_id) else "unknown"),
                            "message": item["message"],
                            "data": item["data"],
                        }
                        payload = json.dumps(event, ensure_ascii=False)
                        self.wfile.write(f"id: {after}\ndata: {payload}\n\n".encode())
                    self.wfile.flush()
                    task = coordinator.get(task_id)
                    if task and task.status.value in {
                        "completed", "failed", "cancelled"
                    } and not coordinator.events(task_id, after):
                        break
                    if time.monotonic() - last_heartbeat >= 15:
                        self.wfile.write(b": keep-alive\n\n")
                        self.wfile.flush()
                        last_heartbeat = time.monotonic()
                    time.sleep(0.35)
            except (BrokenPipeError, ConnectionResetError):
                return

        def do_OPTIONS(self):
            self._json(405, {"error": "CORS não está habilitado; use a interface servida na mesma origem."})

    return Handler


def main():
    host = os.environ.get("KELLY_API_HOST", "127.0.0.1")
    if host not in {"127.0.0.1", "::1", "localhost"}:
        if os.environ.get("KELLY_ALLOW_REMOTE") != "1":
            raise SystemExit("Acesso remoto desativado. Para publicar atrás de HTTPS, defina KELLY_ALLOW_REMOTE=1.")
        if host not in {"0.0.0.0", "::"}:
            raise SystemExit("Para acesso remoto, use KELLY_API_HOST=0.0.0.0 ou ::.")
    token = os.environ.get("KELLY_API_TOKEN", "")
    if len(token) < 32:
        raise SystemExit("Defina KELLY_API_TOKEN com pelo menos 32 caracteres.")
    root = Path(os.environ.get("OVEROS_PROJECT_ROOT", str(Path.cwd()))).expanduser().resolve()
    root.mkdir(parents=True, exist_ok=True)
    if not os.environ.get("KELLY_EXECUTOR", "").strip():
        os.environ["KELLY_EXECUTOR"] = "kelly_dual.executor:execute_task"
    try:
        executor = load_executor()
    except RuntimeError as exc:
        print(f"[kelly-api] Aviso: {exc}")
        def executor(task, emit, cancelled):
            raise RuntimeError("Motor Kelly Code não conectado. Configure KELLY_EXECUTOR para habilitar tarefas de código.")
    coordinator = TaskCoordinator(executor)
    server = ThreadingHTTPServer((host, int(os.environ.get("KELLY_API_PORT", "8765"))),
                                 make_handler(coordinator, token, root))
    server.daemon_threads = True
    print(f"Kelly API em http://{host}:{server.server_port}")
    print(f"Raiz de projetos: {root}")
    try:
        server.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        pass
    finally:
        server.shutdown()
        coordinator.close(wait=False)


if __name__ == "__main__":
    main()
