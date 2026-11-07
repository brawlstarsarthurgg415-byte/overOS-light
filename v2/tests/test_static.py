from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class V2StaticTests(unittest.TestCase):
    def test_core_files_exist(self):
        for rel in (
            'README.md', 'docs/ARQUITETURA.md', 'docs/DECISOES.md',
            'init/overos-init.c', 'desktop/DESIGN.md',
            'web-panel/index.html', 'web-panel/styles.css',
            'web-panel/app.js', 'web-panel/README.md', 'apps/CATALOGO.md',
        ):
            with self.subTest(path=rel):
                self.assertTrue((ROOT / rel).is_file())

    def test_panel_has_no_remote_dependencies(self):
        html = (ROOT / 'web-panel/index.html').read_text()
        self.assertNotIn('https://', html)
        self.assertNotIn('http://', html)
        self.assertIn("connect-src 'self'", html)

    def test_api_values_are_inserted_as_text(self):
        js = (ROOT / 'web-panel/app.js').read_text()
        self.assertIn('textContent', js)
        self.assertNotIn('eval(', js)
        self.assertNotIn('new Function', js)

    def test_admin_actions_are_not_enabled_without_backend(self):
        html = (ROOT / 'web-panel/index.html').read_text()
        self.assertIn('id="diagnosticButton" type="button" disabled', html)

    def test_pid1_starts_overos_session(self):
        c = (ROOT / 'init/overos-init.c').read_text()
        self.assertIn('/sbin/overos-session', c)
        self.assertIn('waitpid(-1', c)
        self.assertIn('RB_POWER_OFF', c)

if __name__ == '__main__':
    unittest.main()