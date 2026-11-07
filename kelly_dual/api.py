"""API local autenticada para a interface Kelly (stdlib, sem dependências externas).

O executor real é injetado por KELLY_EXECUTOR=modulo:funcao. A API não executa
shell diretamente e não oferece executor fictício. Sem adaptador, tarefas são
recusadas com HTTP 503 em vez de simular trabalho.
"""
from __future__ import annotations

import importlib
import json
import os
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse
from typing import Callable

from .orchestrator import TaskCoordinator, Task, TaskEvent

MAX_BODY = 64 * 1024
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
            if urlparse(self.path).path != "/api/tasks":
                self._json(404, {"error": "Rota não encontrada."})
                return
            try:
                body = self._body()
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
        raise SystemExit("Por segurança, a API só pode escutar em localhost nesta versão.")
    token = os.environ.get("KELLY_API_TOKEN", "")
    if len(token) < 32:
        raise SystemExit("Defina KELLY_API_TOKEN com pelo menos 32 caracteres.")
    root = Path(os.environ.get("OVEROS_PROJECT_ROOT", str(Path.cwd()))).expanduser().resolve()
    root.mkdir(parents=True, exist_ok=True)
    executor = load_executor()
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
