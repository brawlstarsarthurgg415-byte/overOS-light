import base64
import tempfile
import unittest
from pathlib import Path

from kelly_dual.file_store import delete_upload, get_project_file, get_upload, list_project_files, list_uploads, readable_text, save_upload


class FileStoreTests(unittest.TestCase):
    def test_upload_list_read_download_and_delete(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw = b"print('Kelly')"
            meta = save_upload(root, "../../hello.py", base64.b64encode(raw).decode(), "text/x-python")
            self.assertEqual(meta["name"], "hello.py")
            self.assertEqual(list_uploads(root)[0]["id"], meta["id"])
            found = get_upload(root, meta["id"])
            self.assertIsNotNone(found)
            self.assertEqual(found[2], raw)
            self.assertIn("print('Kelly')", readable_text(found[0], found[2]))
            self.assertTrue(delete_upload(root, meta["id"]))
            self.assertIsNone(get_upload(root, meta["id"]))

    def test_rejects_empty_or_invalid_upload(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaises(ValueError):
                save_upload(root, "empty.txt", "", "text/plain")
            with self.assertRaises(ValueError):
                save_upload(root, "bad.txt", "not base64!", "text/plain")

    def test_project_file_listing_and_safe_download(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            project = root / "hello.py"
            project.write_text("print('download')", encoding="utf-8")
            (root / ".hidden").mkdir()
            (root / ".hidden" / "secret.txt").write_text("secret", encoding="utf-8")
            listed = list_project_files(root)
            self.assertEqual([item["path"] for item in listed], ["hello.py"])
            found = get_project_file(root, "hello.py")
            self.assertIsNotNone(found)
            self.assertEqual(found[1], b"print('download')")
            self.assertIsNone(get_project_file(root, "../outside.txt"))

    def test_rejects_path_traversal_ids(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertIsNone(get_upload(Path(tmp), "../../etc/passwd"))


if __name__ == "__main__":
    unittest.main()
