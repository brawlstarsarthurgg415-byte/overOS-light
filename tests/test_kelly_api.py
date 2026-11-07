import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from kelly_dual.api import load_executor, safe_project_dir


class KellyApiSafetyTests(unittest.TestCase):
    def test_project_directory_defaults_to_root(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve()
            self.assertEqual(safe_project_dir(None, root), str(root))

    def test_project_directory_inside_root_is_allowed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve()
            child = root / "projeto"
            child.mkdir()
            self.assertEqual(safe_project_dir("projeto", root), str(child))

    def test_project_directory_outside_root_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve()
            outside = Path(tempfile.mkdtemp()).resolve()
            try:
                with self.assertRaises(ValueError):
                    safe_project_dir(str(outside), root)
            finally:
                outside.rmdir()

    def test_executor_is_not_fabricated_when_unconfigured(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "Executor não configurado"):
                load_executor()


if __name__ == "__main__":
    unittest.main()
