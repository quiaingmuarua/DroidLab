from __future__ import annotations

import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from agentforge_tools import duck_sdk


class DuckSdkResolverTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.payload = b"original AAR fixture bytes"
        self.lock = duck_sdk.load_lock()
        self.lock["sha256"] = hashlib.sha256(self.payload).hexdigest()
        lock_file = self.root / duck_sdk.LOCK_PATH
        lock_file.parent.mkdir(parents=True)
        lock_file.write_text(json.dumps(self.lock))

    def test_downloads_original_bytes_and_checks_cache_on_every_use(self):
        def download(url, target):
            self.assertIn(self.lock["tag"], url)
            target.write_bytes(self.payload)
        with patch.object(duck_sdk, "download", side_effect=download) as transport:
            first = duck_sdk.resolve(self.root)
            self.assertEqual(first, duck_sdk.resolve(self.root))
            transport.assert_called_once()
            Path(first["path"]).write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "SHA256"):
                duck_sdk.resolve(self.root)
            transport.assert_called_once()

    def test_corrupt_download_does_not_become_a_cache_entry(self):
        with patch.object(duck_sdk, "download", side_effect=lambda url, target: target.write_bytes(b"wrong")):
            with self.assertRaisesRegex(ValueError, "SHA256"):
                duck_sdk.resolve(self.root)
        self.assertEqual([], list((self.root / ".runtime/duck-detector/sdk").iterdir()))

    def test_local_archive_uses_the_same_verifier(self):
        archive = self.root / "download.aar"
        archive.write_bytes(self.payload)
        result = duck_sdk.resolve(self.root, archive)
        self.assertEqual(self.payload, Path(result["path"]).read_bytes())


if __name__ == "__main__":
    unittest.main()
