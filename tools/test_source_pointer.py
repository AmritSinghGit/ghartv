import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


class SourcePointerMatchesFeed(unittest.TestCase):
    def setUp(self):
        self.feed = json.loads((ROOT / "update" / "latest.json").read_text())
        self.note = (ROOT / "PUBLISHED_SOURCE_POINTER.md").read_text()

    def test_feed_values_appear_in_pointer(self):
        for key in ("sourceCommit", "sha256", "artifactTag", "versionName", "distributionMode"):
            self.assertIn(self.feed[key], self.note, key)

    def test_version_code_appears_in_pointer(self):
        self.assertIn(f"{self.feed['versionCode']} /", self.note)

    def test_pointer_has_no_secrets_or_hosts(self):
        for needle in ("token", "password", "://192.", "://10."):
            self.assertNotIn(needle, self.note.lower())


if __name__ == "__main__":
    unittest.main()
