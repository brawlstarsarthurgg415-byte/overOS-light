"""Orquestrador inicial da Kelly Dual Intelligence.

A Kelly Light permanece livre para conversar. Tarefas delegadas são colocadas
numa fila SQLite persistente e executadas por um worker Kelly Code injetado.
O worker não tem timeout artificial; cancelamento é cooperativo.

Este módulo NÃO executa comandos de shell por conta própria. A integração com
o motor Overcode existente deve fornecer um executor com as mesmas verificações
de permissão e confirmação humana já usadas pelo motor.
"""
from __future__ import annotations

import json
import os
import queue
import sqlite3
import threading
import time
import uuid
from contextlib import contextmanager
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Callable, Optional, Protocol


class TaskStatus(str, Enum):
    QUEUED = "queued"
    WORKING = "working"
    TESTING = "testing"
    AWAITING_APPROVAL = "awaiting_approval"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCEL_REQUESTED = "cancel_requested"
    CANCELLED = "cancelled"


TERMINAL = {TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED}


@dataclass
class TaskEvent:
    at: float
    kind: str
    message: str
    data: dict = field(default_factory=dict)


@dataclass
class Task:
    id: str
    prompt: str
    project_dir: Optional[str]
    status: TaskStatus
    created_at: float
    updated_at: float
    result: Optional[str] = None
    error: Optional[str] = None


class CodeExecutor(Protocol):
    def __call__(
        self,
        task: Task,
        emit: Callable[[str, str, dict], None],
        cancelled: threading.Event,
    ) -> str:
        """Executa uma tarefa usando o motor autorizado do Overcode."""


class TaskStore:
    """Persistência transacional de tarefas e eventos em SQLite."""

    def __init__(self, path: str):
        self.path = str(Path(path).expanduser())
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()
        with self._connect() as db:
            db.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY, prompt TEXT NOT NULL,
                    project_dir TEXT, status TEXT NOT NULL,
                    created_at REAL NOT NULL, updated_at REAL NOT NULL,
                    result TEXT, error TEXT
                )
            """)
            db.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    seq INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id TEXT NOT NULL, at REAL NOT NULL,
                    kind TEXT NOT NULL, message TEXT NOT NULL,
                    data TEXT NOT NULL,
                    FOREIGN KEY(task_id) REFERENCES tasks(id)
                )
            """)
            db.execute("CREATE INDEX IF NOT EXISTS events_task_seq ON events(task_id, seq)")

    @contextmanager
    def _connect(self):
        db = sqlite3.connect(self.path, timeout=30)
        try:
            db.row_factory = sqlite3.Row
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("PRAGMA foreign_keys=ON")
            yield db
            db.commit()
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()

    def create(self, task: Task):
        with self._lock, self._connect() as db:
            db.execute(
                "INSERT INTO tasks VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (task.id, task.prompt, task.project_dir, task.status.value,
                 task.created_at, task.updated_at, task.result, task.error),
            )

    def update(self, task: Task):
        task.updated_at = time.time()
        with self._lock, self._connect() as db:
            db.execute(
                """UPDATE tasks SET status=?, updated_at=?, result=?, error=?
                   WHERE id=?""",
                (task.status.value, task.updated_at, task.result, task.error, task.id),
            )

    def event(self, task_id: str, event: TaskEvent):
        with self._lock, self._connect() as db:
            db.execute(
                "INSERT INTO events(task_id, at, kind, message, data) VALUES (?, ?, ?, ?, ?)",
                (task_id, event.at, event.kind, event.message,
                 json.dumps(event.data, ensure_ascii=False)),
            )

    def get(self, task_id: str) -> Optional[Task]:
        with self._lock, self._connect() as db:
            row = db.execute("SELECT * FROM tasks WHERE id=?", (task_id,)).fetchone()
        return self._task(row) if row else None

    def list_events(self, task_id: str, after: int = 0) -> list[dict]:
        with self._lock, self._connect() as db:
            rows = db.execute(
                "SELECT seq, at, kind, message, data FROM events "
                "WHERE task_id=? AND seq>? ORDER BY seq",
                (task_id, after),
            ).fetchall()
        return [
            {"seq": r["seq"], "at": r["at"], "kind": r["kind"],
             "message": r["message"], "data": json.loads(r["data"])}
            for r in rows
        ]

    def recoverable(self) -> list[Task]:
        """Tarefas interrompidas pelo encerramento voltam à fila."""
        with self._lock, self._connect() as db:
            rows = db.execute(
                "SELECT * FROM tasks WHERE status IN (?, ?, ?)",
                (TaskStatus.QUEUED.value, TaskStatus.WORKING.value,
                 TaskStatus.TESTING.value),
            ).fetchall()
        return [self._task(r) for r in rows]

    @staticmethod
    def _task(row) -> Task:
        return Task(
            id=row["id"], prompt=row["prompt"], project_dir=row["project_dir"],
            status=TaskStatus(row["status"]), created_at=row["created_at"],
            updated_at=row["updated_at"], result=row["result"], error=row["error"],
        )


class TaskCoordinator:
    """Fila sem limite artificial de duração; um worker executa uma tarefa por vez."""

    def __init__(self, executor: CodeExecutor, db_path: Optional[str] = None,
                 autostart: bool = True):
        self.executor = executor
        default_db = Path.home() / ".overos" / "kelly" / "tasks.sqlite3"
        self.store = TaskStore(db_path or os.environ.get("KELLY_TASK_DB", str(default_db)))
        self._queue: queue.Queue[str] = queue.Queue()
        self._cancel: dict[str, threading.Event] = {}
        self._tasks_lock = threading.RLock()
        self._listeners: list[Callable[[str, TaskEvent], None]] = []
        self._stopping = threading.Event()
        self._thread: Optional[threading.Thread] = None

        for task in self.store.recoverable():
            task.status = TaskStatus.QUEUED
            self.store.update(task)
            self._queue.put(task.id)
            self._event(task.id, "recovered", "Tarefa recuperada após reinício.")

        if autostart:
            self.start()

    def start(self):
        if self._thread and self._thread.is_alive():
            return
        self._stopping.clear()
        self._thread = threading.Thread(target=self._run, name="kelly-code-worker", daemon=True)
        self._thread.start()

    def subscribe(self, callback: Callable[[str, TaskEvent], None]):
        with self._tasks_lock:
            self._listeners.append(callback)

    def submit(self, prompt: str, project_dir: Optional[str] = None) -> Task:
        prompt = str(prompt).strip()
        if not prompt:
            raise ValueError("A tarefa não pode estar vazia.")
        now = time.time()
        task = Task(
            id=uuid.uuid4().hex, prompt=prompt, project_dir=project_dir,
            status=TaskStatus.QUEUED, created_at=now, updated_at=now,
        )
        self.store.create(task)
        with self._tasks_lock:
            self._cancel[task.id] = threading.Event()
        self._event(task.id, "queued", "Tarefa enviada para Kelly Code.")
        self._queue.put(task.id)
        return task

    def get(self, task_id: str) -> Optional[Task]:
        return self.store.get(task_id)

    def events(self, task_id: str, after: int = 0) -> list[dict]:
        return self.store.list_events(task_id, after)

    def cancel(self, task_id: str) -> bool:
        task = self.store.get(task_id)
        if not task or task.status in TERMINAL:
            return False
        with self._tasks_lock:
            token = self._cancel.setdefault(task_id, threading.Event())
            token.set()
        task.status = TaskStatus.CANCEL_REQUESTED
        self.store.update(task)
        self._event(task_id, "cancel_requested", "Cancelamento solicitado; aguardando parada segura.")
        return True

    def close(self, wait: bool = True):
        self._stopping.set()
        self._queue.put("")
        if wait and self._thread:
            self._thread.join()

    def _event(self, task_id: str, kind: str, message: str, data: Optional[dict] = None):
        event = TaskEvent(time.time(), kind, message, data or {})
        self.store.event(task_id, event)
        with self._tasks_lock:
            listeners = list(self._listeners)
        for listener in listeners:
            try:
                listener(task_id, event)
            except Exception:
                pass

    def _run(self):
        while not self._stopping.is_set():
            task_id = self._queue.get()
            if not task_id:
                continue
            task = self.store.get(task_id)
            if not task or task.status in TERMINAL:
                continue
            with self._tasks_lock:
                token = self._cancel.setdefault(task_id, threading.Event())
            if token.is_set():
                task.status = TaskStatus.CANCELLED
                self.store.update(task)
                self._event(task_id, "cancelled", "Tarefa cancelada antes de começar.")
                continue

            task.status = TaskStatus.WORKING
            self.store.update(task)
            self._event(task_id, "working", "Kelly Code iniciou a tarefa.")

            def emit(kind: str, message: str, data: Optional[dict] = None):
                current = self.store.get(task_id)
                if current and kind == "testing":
                    current.status = TaskStatus.TESTING
                    self.store.update(current)
                elif current and kind == "awaiting_approval":
                    current.status = TaskStatus.AWAITING_APPROVAL
                    self.store.update(current)
                elif current and kind == "working":
                    current.status = TaskStatus.WORKING
                    self.store.update(current)
                self._event(task_id, kind, message, data)

            try:
                result = self.executor(task, emit, token)
                current = self.store.get(task_id) or task
                if token.is_set():
                    current.status = TaskStatus.CANCELLED
                    self.store.update(current)
                    self._event(task_id, "cancelled", "Tarefa interrompida com segurança.")
                else:
                    current.status = TaskStatus.COMPLETED
                    current.result = str(result or "")
                    self.store.update(current)
                    self._event(task_id, "completed", "Kelly Code concluiu e devolveu o resultado.")
            except Exception as exc:
                current = self.store.get(task_id) or task
                current.status = TaskStatus.CANCELLED if token.is_set() else TaskStatus.FAILED
                current.error = f"{type(exc).__name__}: {exc}"
                self.store.update(current)
                kind = "cancelled" if token.is_set() else "failed"
                self._event(task_id, kind, current.error)


def should_delegate_to_code(message: str) -> bool:
    """Heurística conservadora; o roteador por modelo pode substituí-la depois."""
    text = (message or "").casefold()
    cues = (
        "crie um projeto", "cria um projeto", "programe", "implemente",
        "corrija o código", "corrige o código", "edite os arquivos",
        "instale", "configure o servidor", "analise esse repositório",
        "construa", "desenvolva", "faça um aplicativo", "faz um aplicativo",
        "faça um site", "faz um site", "execute os testes", "@code", "kelly code",
    )
    return any(cue in text for cue in cues)
