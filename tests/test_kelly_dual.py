import tempfile
import threading
import time
import unittest
from pathlib import Path

from kelly_dual.orchestrator import (
    TaskCoordinator, TaskStatus, should_delegate_to_code,
)


class CoordinatorTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db = str(Path(self.tmp.name) / "tasks.sqlite3")

    def tearDown(self):
        self.tmp.cleanup()

    def test_submit_runs_and_persists_result_and_events(self):
        finished = threading.Event()

        def executor(task, emit, cancelled):
            emit("planning", "Plano criado.")
            emit("testing", "Executando testes.")
            finished.set()
            return "Projeto verificado."

        coordinator = TaskCoordinator(executor, self.db)
        try:
            task = coordinator.submit("Crie um projeto de teste")
            self.assertTrue(finished.wait(3), "worker não concluiu a tarefa")
            deadline = time.time() + 3
            current = coordinator.get(task.id)
            while current and current.status != TaskStatus.COMPLETED and time.time() < deadline:
                time.sleep(0.01)
                current = coordinator.get(task.id)
            self.assertEqual(current.status, TaskStatus.COMPLETED)
            self.assertEqual(current.result, "Projeto verificado.")
            kinds = [event["kind"] for event in coordinator.events(task.id)]
            self.assertIn("queued", kinds)
            self.assertIn("working", kinds)
            self.assertIn("testing", kinds)
            self.assertIn("completed", kinds)
        finally:
            coordinator.close()

    def test_cancel_is_cooperative(self):
        started = threading.Event()

        def executor(task, emit, cancelled):
            started.set()
            while not cancelled.wait(0.01):
                pass
            return "interrompido"

        coordinator = TaskCoordinator(executor, self.db)
        try:
            task = coordinator.submit("Tarefa longa")
            self.assertTrue(started.wait(3))
            self.assertTrue(coordinator.cancel(task.id))
            deadline = time.time() + 3
            current = coordinator.get(task.id)
            while current and current.status != TaskStatus.CANCELLED and time.time() < deadline:
                time.sleep(0.01)
                current = coordinator.get(task.id)
            self.assertEqual(current.status, TaskStatus.CANCELLED)
        finally:
            coordinator.close()

    def test_pending_task_is_recovered_after_restart(self):
        first = TaskCoordinator(lambda *_: "unused", self.db, autostart=False)
        task = first.submit("Aguardar worker")
        recovered = TaskCoordinator(lambda *_: "ok", self.db, autostart=False)
        try:
            current = recovered.get(task.id)
            self.assertEqual(current.status, TaskStatus.QUEUED)
            self.assertTrue(any(e["kind"] == "recovered" for e in recovered.events(task.id)))
        finally:
            first.close()
            recovered.close()

    def test_delegation_heuristic(self):
        self.assertTrue(should_delegate_to_code("Crie um projeto em Rust"))
        self.assertTrue(should_delegate_to_code("Corrija o código do navegador"))
        self.assertFalse(should_delegate_to_code("Oi, como você está?"))


if __name__ == "__main__":
    unittest.main()
