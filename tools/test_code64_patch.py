import hashlib
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIR = ROOT / "patches" / "code64-one-click-play"
ALLOWED = ("android-tv/", ".github/workflows/review6", "tools/tv-first/check_contract.py", "REVIEW64.md")


class Code64PatchIsWhatTheReadmeSays(unittest.TestCase):
    def setUp(self):
        self.patch = (DIR / "code64-one-click-play.patch").read_bytes()
        self.readme = (DIR / "README.md").read_text()

    def test_recorded_sha256_matches(self):
        self.assertIn(hashlib.sha256(self.patch).hexdigest(), self.readme)

    def test_touches_only_app_source_tests_and_review_workflows(self):
        files = re.findall(r"^diff --git a/(\S+) b/", self.patch.decode(), re.M)
        self.assertTrue(files)
        for name in files:
            self.assertTrue(name.startswith(ALLOWED), name)

    def test_never_touches_the_update_feed_or_keys(self):
        text = self.patch.decode().lower()
        for needle in ("update/latest.json", ".jks", "keystore", "password"):
            self.assertNotIn(needle, text)

    def test_declares_code64(self):
        self.assertRegex(self.patch.decode(), r"(?m)^\+\s+versionCode = 64$")


if __name__ == "__main__":
    unittest.main()
