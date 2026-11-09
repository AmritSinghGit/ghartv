import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


class SiteFallbackMatchesFeed(unittest.TestCase):
    """The install page rewrites its button from update/latest.json at load time. The static
    href is what a browser without script or without feed access still gets, so it must
    name the same APK the feed publishes."""

    def setUp(self):
        self.feed = json.loads((ROOT / "update" / "latest.json").read_text())
        self.index = (ROOT / "docs" / "index.html").read_text()

    def test_static_download_href_is_the_published_apk(self):
        match = re.search(r'id="download-apk"\s+href="([^"]+)"', self.index)
        self.assertIsNotNone(match, "download button not found")
        self.assertEqual(match.group(1), self.feed["apkUrl"])

    def test_feed_url_names_its_own_tag(self):
        self.assertIn(f"/download/{self.feed['artifactTag']}/", self.feed["apkUrl"])
        self.assertIn(f"code{self.feed['versionCode']}", self.feed["apkUrl"])

    def test_site_checker_expects_the_published_apk(self):
        checker = (ROOT / "tools" / "site" / "customer-check.mjs").read_text()
        self.assertIn(self.feed["apkUrl"], checker)

    def test_fallback_text_names_no_stale_review(self):
        script = (ROOT / "docs" / "assets" / "viewer-site.js").read_text()
        self.assertNotRegex(script + self.index, r"Review\d+ release")


if __name__ == "__main__":
    unittest.main()
