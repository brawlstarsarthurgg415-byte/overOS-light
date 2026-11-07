import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from kelly_dual.executor import _parse, _safe_path, _tool


class ExecutorSafetyTests(unittest.TestCase):
    def test_path_traversal_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "project"
            root.mkdir()
            with self.assertRaises(ValueError):
                _safe_path(root, "../outside.txt")

    def test_secret_and_git_paths_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in (".env", ".git/config", "private.pem"):
                with self.subTest(name=name), self.assertRaises(ValueError):
                    _safe_path(root, name)

    def test_write_file_creates_checkpoint_before_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "project"
            home = Path(tmp) / "home"
            root.mkdir()
            home.mkdir()
            target = root / "app.py"
            target.write_text("old", encoding="utf-8")
            with patch("kelly_dual.executor.Path.home", return_value=home):
                message, metadata = _tool(
                    {"tool": "write_file", "path": "app.py", "content": "new"},
                    root, "a" * 32
                )
            self.assertEqual(target.read_text(encoding="utf-8"), "new")
            self.assertTrue(Path(metadata["backup"]).is_file())
            self.assertEqual(Path(metadata["backup"]).read_text(encoding="utf-8"), "old")
            self.assertIn("checkpoint", message)

    def test_unknown_shell_or_test_tool_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                _tool({"tool": "run_shell", "command": "rm -rf /"}, Path(tmp), "b" * 32)
            with self.assertRaises(ValueError):
                _tool({"tool": "run_test", "test_id": "arbitrary-shell"}, Path(tmp), "b" * 32)

    def test_model_response_parser_requires_json_object(self):
        self.assertEqual(_parse('{"final":"ok"}'), {"final": "ok"})
        self.assertIsNone(_parse("I did some work, trust me"))


if __name__ == "__main__":
    unittest.main()
