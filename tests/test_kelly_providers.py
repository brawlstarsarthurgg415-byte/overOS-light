import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from kelly_dual import providers


class ProviderConfigTests(unittest.TestCase):
    def test_api_key_is_not_returned_to_browser(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "providers.json"
            with patch.object(providers, "CONFIG", path):
                result = providers.save_config({
                    "provider": "openai-compatible",
                    "model": "gpt-4o-mini",
                    "base_url": "https://api.openai.com/v1",
                    "api_key": "test-secret-key",
                })
                self.assertTrue(result["has_api_key"])
                self.assertNotIn("api_key", result)
                self.assertEqual(json.loads(path.read_text())["api_key"], "test-secret-key")
                if hasattr(os, "stat"):
                    self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_empty_key_keeps_existing_secret(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "providers.json"
            with patch.object(providers, "CONFIG", path):
                providers.save_config({"provider":"gemini","model":"gemini-2.5-flash","api_key":"secret"})
                providers.save_config({"provider":"gemini","model":"gemini-2.5-flash","api_key":""})
                self.assertEqual(json.loads(path.read_text())["api_key"], "secret")

    def test_insecure_provider_url_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(providers, "CONFIG", Path(tmp) / "providers.json"):
                with self.assertRaisesRegex(ValueError, "HTTPS"):
                    providers.save_config({
                        "provider":"openai-compatible","model":"test",
                        "base_url":"http://example.com/v1","api_key":"secret"
                    })


    def test_switching_provider_does_not_reuse_previous_secret(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "providers.json"
            with patch.object(providers, "CONFIG", path):
                providers.save_config({"provider":"openai-compatible","model":"gpt-4o-mini","base_url":"https://api.openai.com/v1","api_key":"openai-secret"})
                result = providers.save_config({"provider":"gemini","model":"gemini-2.5-flash"})
                self.assertFalse(result["has_api_key"])
                self.assertEqual(json.loads(path.read_text())["api_key"], "")


    def test_openai_compatible_provider_receives_history(self):
        cfg = {"provider":"openai-compatible","model":"gpt-4o-mini","base_url":"https://api.openai.com/v1","api_key":"secret"}
        response = {"choices":[{"message":{"content":"resposta"}}]}
        with patch.object(providers, "_read", return_value=cfg), patch.object(providers, "_request", return_value=response) as request:
            answer = providers.complete("segunda pergunta", "Kelly", [
                {"role":"user","content":"primeira pergunta"},
                {"role":"assistant","content":"primeira resposta"},
            ])
        self.assertEqual(answer, "resposta")
        sent = request.call_args.args[1]["messages"]
        self.assertEqual([m["role"] for m in sent], ["system","user","assistant","user"])
        self.assertEqual(sent[-2]["content"], "primeira resposta")

    def test_gemini_provider_uses_model_role_for_assistant_history(self):
        cfg = {"provider":"gemini","model":"gemini-2.5-flash","api_key":"secret"}
        response = {"candidates":[{"content":{"parts":[{"text":"resposta"}]}}]}
        with patch.object(providers, "_read", return_value=cfg), patch.object(providers, "_request", return_value=response) as request:
            answer = providers.complete("nova pergunta", "Kelly", [
                {"role":"user","content":"oi"},
                {"role":"assistant","content":"olá"},
            ])
        self.assertEqual(answer, "resposta")
        payload = request.call_args.args[1]
        self.assertEqual(payload["contents"][1]["role"], "model")
        self.assertIn("systemInstruction", payload)


if __name__ == "__main__":
    unittest.main()
