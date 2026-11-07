"""Sessões locais e comandos de barra do OverCode 2.7.

As sessões guardam apenas mensagens e metadados, nunca chaves de API.
Comandos de leitura não alteram arquivos. /plan apenas produz um plano textual.
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import time
import uuid
from pathlib import Path
from typing import Callable

SESSION_ID = re.compile(r"^[a-f0-9]{32}$")
COMMANDS = {
    "/help": "Mostra os comandos disponíveis.",
    "/commands": "Lista os comandos de barra.",
    "/plan [tarefa]": "Cria um plano sem executar alterações.",
    "/diff": "Mostra o diff Git do projeto atual (somente leitura).",
    "/context": "Estima o tamanho do histórico enviado.",
    "/usage": "Mostra uma estimativa local de contexto; o provedor não informou tokens.",
    "/status": "Mostra o estado da API, provedor e projeto.",
    "/save-session": "Confirma que a sessão atual está salva automaticamente.",
    "/sessions": "Lista as sessões salvas.",
    "/compact": "Resume o histórico atual sem apagar as mensagens originais.",
}


class SessionStore:
    def __init__(self, directory: str | None = None):
        self.directory = Path(directory or os.environ.get(
            "KELLY_SESSIONS_DIR", "~/.overos/kelly/sessions"
        )).expanduser()
        self.directory.mkdir(parents=True, exist_ok=True, mode=0o700)

    def _path(self, session_id: str) -> Path:
        if not SESSION_ID.fullmatch(session_id or ""):
            raise ValueError("ID de sessão inválido.")
        return self.directory / (session_id + ".jsonl")

    def create(self) -> str:
        session_id = uuid.uuid4().hex
        path = self._path(session_id)
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            stream.write(json.dumps({
                "type": "session", "id": session_id, "created_at": time.time()
            }) + "\n")
        return session_id

    def append(self, session_id: str, role: str, content: str) -> None:
        if role not in {"user", "assistant", "system"}:
            raise ValueError("Papel de mensagem inválido.")
        path = self._path(session_id)
        if not path.exists():
            raise ValueError("Sessão não encontrada.")
        record = {"type": "message", "role": role, "content": str(content)[:20000],
                  "created_at": time.time()}
        fd = os.open(path, os.O_WRONLY | os.O_APPEND)
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            stream.write(json.dumps(record, ensure_ascii=False) + "\n")

    def get(self, session_id: str) -> list[dict]:
        path = self._path(session_id)
        if not path.exists():
            raise ValueError("Sessão não encontrada.")
        rows = []
        for line in path.read_text(encoding="utf-8").splitlines():
            try:
                item = json.loads(line)
            except ValueError:
                continue
            if item.get("type") == "message":
                rows.append({k: item[k] for k in ("role", "content", "created_at")})
        return rows

    def list(self, limit: int = 30) -> list[dict]:
        items = []
        for path in sorted(self.directory.glob("*.jsonl"),
                           key=lambda p: p.stat().st_mtime, reverse=True)[:limit]:
            if not SESSION_ID.fullmatch(path.stem):
                continue
            try:
                messages = self.get(path.stem)
                items.append({"id": path.stem, "updated_at": path.stat().st_mtime,
                              "message_count": len(messages),
                              "preview": (messages[-1]["content"][:120] if messages else "")})
            except (OSError, ValueError):
                continue
        return items


def run_slash_command(message: str, history: list, root: Path,
                      session_id: str, sessions: SessionStore,
                      complete: Callable[[str, str, list], str],
                      provider_config: Callable[[], dict]) -> str | None:
    command, _, argument = message.strip().partition(" ")
    command = command.lower()
    if command not in {item.split(" ")[0] for item in COMMANDS}:
        return None
    if command in {"/help", "/commands"}:
        return "Comandos OverCode 2.7:\n" + "\n".join(
            f"{name} — {description}" for name, description in COMMANDS.items()
        )
    if command == "/plan":
        task = argument.strip()
        if not task:
            return "Use /plan seguido da tarefa. Exemplo: /plan revisar o backend e corrigir os testes."
        system = ("Você é Kelly, planejadora técnica do OverCode. Crie um plano numerado, "
                  "com etapas, arquivos prováveis, riscos e testes. NÃO execute, não afirme "
                  "que editou arquivos e não forneça comandos para execução automática.")
        return complete(task, system, history)
    if command == "/context":
        chars = sum(len(str(item.get("content", ""))) for item in history[-30:])
        return f"Histórico recebido: {len(history[-30:])} mensagens, {chars} caracteres (aprox. {max(1, chars // 4)} tokens de texto)."
    if command == "/usage":
        chars = sum(len(str(item.get("content", ""))) for item in history[-30:])
        return f"Estimativa local: ~{max(1, chars // 4)} tokens de texto no histórico enviado. O provedor não disponibilizou consumo real nesta resposta."
    if command == "/status":
        cfg = provider_config()
        return (f"OverCode 2.7\nProjeto: {root}\nProvedor: {cfg.get('provider', 'não configurado')}\n"
                f"Modelo: {cfg.get('model', 'não configurado')}\nChave configurada: "
                f"{'sim' if cfg.get('has_api_key') else 'não'}\nSessão: {session_id}")
    if command == "/diff":
        try:
            result = subprocess.run(
                ["git", "--no-pager", "diff", "--no-ext-diff", "--no-color", "--"],
                cwd=str(root), capture_output=True, text=True, timeout=8, check=False,
                env={**os.environ, "GIT_PAGER": "cat", "GIT_TERMINAL_PROMPT": "0"},
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            return f"Não foi possível consultar o diff: {exc}"
        output = result.stdout.strip()
        if result.returncode != 0:
            return "Git não conseguiu gerar o diff: " + result.stderr[:1000]
        return output[:12000] if output else "Nenhuma alteração rastreada no working tree."
    if command == "/save-session":
        return f"Sessão {session_id} salva automaticamente em armazenamento local protegido."
    if command == "/sessions":
        items = sessions.list()
        if not items:
            return "Ainda não há sessões salvas."
        return "Sessões recentes:\n" + "\n".join(
            f"- {x['id']} · {x['message_count']} mensagens · {x['preview']}" for x in items
        )
    if command == "/compact":
        if not history:
            return "Não há histórico para resumir."
        source = "\n".join(f"{x.get('role')}: {x.get('content', '')}" for x in history[-30:])
        return complete("Resuma o histórico preservando decisões, restrições e tarefas pendentes:\n" + source,
                        "Você resume sessões técnicas de desenvolvimento com precisão e concisão.", [])
    return None
