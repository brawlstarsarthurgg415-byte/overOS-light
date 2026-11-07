"""Executor seguro da Kelly Code: arquivos, Git e testes allowlisted.

O modelo propõe ações estruturadas; este módulo valida cada ação no backend.
Não existe execução de shell arbitrário. Arquivos substituídos recebem checkpoint.
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from . import providers

MAX_FILE = 1_000_000
MAX_OUTPUT = 10_000
MAX_STEPS = 6
MAX_ACTIONS = 5
SKIP_DIRS = {".git", ".venv", "venv", "node_modules", "__pycache__", ".gradle",
             "build", "dist", ".idea"}
SECRET_NAMES = {".env", ".env.local", ".env.production", "id_rsa", "id_ed25519",
                "credentials.json", "secrets.json"}


def _safe_path(root: Path, raw: object) -> Path:
    value = str(raw or "").strip()
    if not value or "\x00" in value:
        raise ValueError("Caminho vazio ou inválido.")
    candidate = (root / value).resolve()
    if candidate != root and root not in candidate.parents:
        raise ValueError("Caminho fora da pasta do projeto.")
    parts = {part.lower() for part in candidate.parts}
    if any(part in SECRET_NAMES or part.endswith((".pem", ".key")) for part in parts):
        raise ValueError("Acesso a arquivo de segredo bloqueado.")
    if ".git" in parts:
        raise ValueError("Acesso à pasta .git bloqueado.")
    return candidate


def _inventory(root: Path, limit: int = 180) -> list[str]:
    found = []
    for current, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS and not d.startswith(".")]
        for name in files:
            path = Path(current) / name
            rel = path.relative_to(root).as_posix()
            try:
                _safe_path(root, rel)
            except ValueError:
                continue
            found.append(rel)
            if len(found) >= limit:
                return found
    return found


def _checkpoint(path: Path, root: Path, task_id: str) -> str:
    relative = path.relative_to(root)
    backup = (Path.home() / ".overos" / "kelly" / "checkpoints" /
              task_id / relative).resolve()
    backup.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    shutil.copy2(path, backup)
    return str(backup)


def _run_test(test_id: str, root: Path, path: str = "") -> str:
    tests = {
        "python-unittest": [sys.executable, "-m", "unittest", "discover", "-s", "tests", "-v"],
        "node-app-js": ["node", "--check", "ui/app.js"],
        "node-settings-js": ["node", "--check", "ui/settings.js"],
    }
    if test_id == "shell-syntax":
        target = _safe_path(root, path)
        if not target.is_file() or target.suffix != ".sh":
            raise ValueError("shell-syntax exige o caminho de um arquivo .sh do projeto.")
        command = ["bash", "-n", str(target)]
    elif test_id in tests:
        command = tests[test_id]
    else:
        raise ValueError("Teste não permitido. Use python-unittest, node-app-js, node-settings-js ou shell-syntax.")
    if command[0] in {"node", "bash"} and shutil.which(command[0]) is None:
        raise RuntimeError(f"Dependência ausente: {command[0]}. Instalação não é automática.")
    result = subprocess.run(command, cwd=str(root), capture_output=True, text=True,
                            timeout=180, check=False,
                            env={**os.environ, "GIT_TERMINAL_PROMPT": "0"})
    output = (result.stdout + "\n" + result.stderr).strip()
    return f"exit_code={result.returncode}\n{output[-MAX_OUTPUT:] or '(sem saída)'}"


def _tool(action: dict, root: Path, task_id: str) -> tuple[str, dict]:
    name = str(action.get("tool", ""))
    if name == "list_files":
        files = _inventory(root)
        return "Arquivos do projeto:\n" + "\n".join(files), {"count": len(files)}
    if name == "read_file":
        path = _safe_path(root, action.get("path"))
        if not path.is_file():
            raise ValueError("Arquivo não encontrado.")
        if path.stat().st_size > MAX_FILE:
            raise ValueError("Arquivo maior que 1 MB; leitura bloqueada.")
        return path.read_text(encoding="utf-8", errors="replace")[:MAX_OUTPUT], {"path": str(path.relative_to(root))}
    if name == "write_file":
        path = _safe_path(root, action.get("path"))
        content = action.get("content")
        if not isinstance(content, str) or len(content.encode("utf-8")) > MAX_FILE:
            raise ValueError("Conteúdo inválido ou maior que 1 MB.")
        path.parent.mkdir(parents=True, exist_ok=True)
        backup = _checkpoint(path, root, task_id) if path.is_file() else None
        fd, temp_name = tempfile.mkstemp(prefix=".overcode-", dir=str(path.parent))
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as stream:
                stream.write(content)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temp_name, path)
        finally:
            try:
                os.unlink(temp_name)
            except FileNotFoundError:
                pass
        return f"Arquivo salvo: {path.relative_to(root)}" + (f" | checkpoint: {backup}" if backup else ""), {
            "path": str(path.relative_to(root)), "backup": backup, "bytes": len(content.encode("utf-8"))
        }
    if name == "git_status":
        if not (root / ".git").is_dir():
            return "Este diretório não é um repositório Git.", {}
        result = subprocess.run(["git", "status", "--short"], cwd=str(root),
                                capture_output=True, text=True, timeout=8, check=False)
        return (result.stdout or result.stderr or "Working tree limpa.")[:MAX_OUTPUT], {}
    if name == "run_test":
        test_id = str(action.get("test_id", ""))
        output = _run_test(test_id, root, str(action.get("path", "")))
        return output, {"test_id": test_id, "passed": output.startswith("exit_code=0")}
    raise ValueError(f"Ferramenta não permitida: {name}")


def _parse(raw: str) -> dict | None:
    text = raw.strip()
    if text.startswith("```"):
        text = re.sub(r"^\`\`\`(?:json)?\s*|\s*\`\`\`$", "", text, flags=re.I)
    try:
        value = json.loads(text)
        return value if isinstance(value, dict) else None
    except ValueError:
        match = re.search(r"\{.*\}", text, flags=re.S)
        if not match:
            return None
        try:
            value = json.loads(match.group(0))
            return value if isinstance(value, dict) else None
        except ValueError:
            return None


def execute_task(task, emit, cancelled) -> str:
    root = Path(task.project_dir or os.getcwd()).resolve()
    if not root.is_dir():
        raise RuntimeError("A pasta do projeto não existe.")
    inventory = _inventory(root)
    emit("planning", "Kelly Code está analisando o projeto e preparando um plano.",
         {"project_root": str(root), "files_seen": len(inventory)})
    system = (
        "Você é Kelly Code, agente de programação do OverCode 2.7. "
        "Responda SOMENTE com JSON válido. Formato: "
        '{"actions":[{"tool":"list_files"}]} ou {"final":"resposta"}. '
        "Ferramentas permitidas: list_files; read_file com path; write_file com path e content; "
        "git_status; run_test com test_id python-unittest, node-app-js, node-settings-js ou shell-syntax "
        "(este último exige path .sh). Faça no máximo 5 ações por rodada. "
        "Leia arquivos antes de alterá-los. Não tente shell genérico, rede, instalar pacotes, "
        "apagar arquivos, alterar permissões, ler segredos ou sair da raiz do projeto. "
        "Se algo exigir ação bloqueada, explique no campo final. Preserve arquivos não relacionados. "
        "Após receber os resultados das ferramentas, continue com novas ações ou finalize."
    )
    context = {
        "pedido": task.prompt,
        "raiz": str(root),
        "arquivos_iniciais": inventory,
        "resultado_ferramentas": [],
    }
    for step in range(MAX_STEPS):
        if cancelled.is_set():
            return "Tarefa cancelada antes da próxima etapa."
        raw = providers.complete(
            json.dumps(context, ensure_ascii=False),
            system,
            [{"role": "user", "content": "Execute a tarefa com as ferramentas seguras disponíveis. Retorne JSON."}],
        )
        payload = _parse(raw)
        if payload is None:
            emit("working", "Kelly Code recebeu uma resposta sem formato de ferramenta; tratando como resposta final.")
            return raw[:MAX_OUTPUT]
        final = payload.get("final")
        if isinstance(final, str) and final.strip():
            return final.strip()
        actions = payload.get("actions")
        if not isinstance(actions, list) or not actions:
            return "Kelly Code não retornou ações válidas nem uma resposta final. Nenhuma ação adicional foi executada."
        if len(actions) > MAX_ACTIONS:
            raise ValueError("O plano excedeu o limite de ações por rodada.")
        for action in actions:
            if cancelled.is_set():
                return "Tarefa cancelada entre ações."
            if not isinstance(action, dict):
                raise ValueError("Ação inválida retornada pelo modelo.")
            tool_name = str(action.get("tool", "ferramenta"))
            emit("working", f"Preparando ferramenta: {tool_name}.", {"tool": tool_name})
            try:
                output, metadata = _tool(action, root, task.id)
                emit("tool_completed", f"{tool_name} concluída.", {"tool": tool_name, **metadata, "output": output[:MAX_OUTPUT]})
                context["resultado_ferramentas"].append({"tool": tool_name, "ok": True, "output": output[:MAX_OUTPUT]})
            except Exception as exc:
                emit("tool_failed", f"{tool_name} bloqueada ou falhou: {exc}", {"tool": tool_name})
                context["resultado_ferramentas"].append({"tool": tool_name, "ok": False, "error": str(exc)})
    return "Kelly Code atingiu o limite de etapas desta execução. O progresso e os eventos foram preservados; delegue uma continuação para prosseguir."
