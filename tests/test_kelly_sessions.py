import tempfile
import unittest
from pathlib import Path

from kelly_dual.sessions import SessionStore, run_slash_command


class SessionStoreTests(unittest.TestCase):
    def test_session_persists_messages_and_lists_preview(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = SessionStore(tmp)
            session_id = store.create()
            store.append(session_id, "user", "Olá Kelly")
            store.append(session_id, "assistant", "Olá! Como posso ajudar?")
            messages = store.get(session_id)
            self.assertEqual([m["role"] for m in messages], ["user", "assistant"])
            self.assertEqual(store.list()[0]["id"], session_id)
            self.assertEqual(store.list()[0]["message_count"], 2)

    def test_invalid_session_id_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = SessionStore(tmp)
            with self.assertRaises(ValueError):
                store.get("../../outside")

    def test_plan_only_asks_provider_for_a_plan(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = SessionStore(tmp)
            session_id = store.create()
            calls = []
            def complete(message, system, history):
                calls.append((message, system, history))
                return "1. Inspecionar\n2. Testar"
            answer = run_slash_command(
                "/plan revisar o backend", [], Path(tmp), session_id, store,
                complete, lambda: {"provider": "test", "model": "mock", "has_api_key": True}
            )
            self.assertEqual(answer, "1. Inspecionar\n2. Testar")
            self.assertIn("NÃO execute", calls[0][1])

    def test_unknown_command_is_not_consumed(self):
        with tempfile.TemporaryDirectory() as tmp:
            store = SessionStore(tmp)
            session_id = store.create()
            answer = run_slash_command(
                "/not-a-command", [], Path(tmp), session_id, store,
                lambda *_: "unused", lambda: {}
            )
            self.assertIsNone(answer)


if __name__ == "__main__":
    unittest.main()
