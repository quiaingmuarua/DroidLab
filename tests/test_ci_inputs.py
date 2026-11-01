from __future__ import annotations

import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts import ci_inputs


class CiInputsTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.apk = self.root / "detect-env-debug.apk"
        self.manifest = self.root / "apk-manifest.json"
        for relative in (Path("XA_MASS_RELEASE.lock.json"), ci_inputs.duck_sdk.LOCK_PATH):
            output = self.root / relative
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes((ci_inputs.ROOT / relative).read_bytes())
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("lib/x86_64/libdetect_sample.so", b"fixture-native")
        identity = patch.object(ci_inputs, "commit", return_value="a" * 40)
        identity.start()
        self.addCleanup(identity.stop)

    def record(self):
        return ci_inputs.record_apk(self.root, self.apk, self.manifest)

    def test_current_artifact_round_trip_is_read_only(self):
        expected = self.record()
        before = self.manifest.read_bytes()
        with patch.object(ci_inputs.urllib.request, "urlopen", side_effect=AssertionError("network")):
            self.assertEqual(expected, ci_inputs.verify_apk(self.root, self.apk, self.manifest))
        self.assertEqual(before, self.manifest.read_bytes())

    def test_wrong_source_commit_and_unknown_manifest_fields_are_rejected(self):
        for change in ({"droidLabCommit": "b" * 40}, {"schemaVersion": 2}, {"extra": "untrusted"}):
            with self.subTest(change=change):
                value = self.record()
                value.update(change)
                self.manifest.write_text(json.dumps(value), encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "manifest differs"):
                    ci_inputs.verify_apk(self.root, self.apk, self.manifest)

    def test_tampered_apk_is_rejected(self):
        self.record()
        with self.apk.open("ab") as stream:
            stream.write(b"tampered")
        with self.assertRaisesRegex(ValueError, "manifest differs"):
            ci_inputs.verify_apk(self.root, self.apk, self.manifest)

    def test_changed_xa_or_duck_lock_does_not_match_an_old_apk(self):
        for relative, key in ((Path("XA_MASS_RELEASE.lock.json"), "gitCommit"),
                              (ci_inputs.duck_sdk.LOCK_PATH, "commit")):
            with self.subTest(lock=relative):
                self.record()
                path = self.root / relative
                previous = path.read_bytes()
                value = json.loads(previous)
                value[key] = "b" * 40
                path.write_text(json.dumps(value), encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "manifest differs"):
                    ci_inputs.verify_apk(self.root, self.apk, self.manifest)
                path.write_bytes(previous)

    def test_wrong_native_abi_cannot_be_recorded_as_x86(self):
        with zipfile.ZipFile(self.apk, "a") as archive:
            archive.writestr("lib/arm64-v8a/libother.so", b"arm")
        with self.assertRaisesRegex(ValueError, "only the x86_64"):
            self.record()
        self.assertFalse(self.manifest.exists())

    def test_fixture_lock_retains_historical_source_and_rejects_bad_coordinates(self):
        source = ci_inputs.ROOT / "hookforge/test/api33/fixture.json"
        original = json.loads(source.read_text(encoding="utf-8"))
        self.assertEqual(original, ci_inputs.fixture_lock(source))
        local = self.root / "fixture.json"
        for change in ({"sha256": "wrong"}, {"asset": "../escape.apk"},
                       {"repository": "https://example.com"}, {"source": {"commit": None}}):
            with self.subTest(change=change):
                local.write_text(json.dumps({**original, **change}), encoding="utf-8")
                with self.assertRaises(ValueError):
                    ci_inputs.fixture_lock(local)

    def test_corrupt_fixture_download_never_publishes_output(self):
        lock = ci_inputs.ROOT / "hookforge/test/api33/fixture.json"
        output = self.root / "fixture.apk"
        with patch.object(ci_inputs.urllib.request, "urlopen", return_value=io.BytesIO(b"wrong")):
            with self.assertRaisesRegex(ValueError, "SHA256 differs"):
                ci_inputs.download_fixture(lock, output)
        self.assertFalse(output.exists())
        self.assertEqual([], list(self.root.glob("*.download")))

    def test_device_evidence_rejects_another_apk_or_production_bundle(self):
        digest = ci_inputs.sha256(self.apk)
        for host in ("python", "node"):
            for mode in ("attach", "spawn"):
                path = self.root / f"{host}-{mode}/result.json"
                path.parent.mkdir()
                path.write_text(json.dumps({"apk": {"sha256": digest}, "installedApk": {"sha256": digest}}))
        beer = self.root / "rootbeer"
        beer.mkdir()
        result = {"reuse": {"apkSha256": digest}, "apiAgentSha256": "c" * 64}
        (beer / "result.json").write_text(json.dumps(result))
        production = {"files": {"dist/android-api.js": "c" * 64}}
        self.manifest.write_text(json.dumps(production))
        (beer / "production-manifest.json").write_text(json.dumps(production))
        ci_inputs.verify_results(self.apk, self.root, self.manifest)
        result["reuse"]["apkSha256"] = "d" * 64
        (beer / "result.json").write_text(json.dumps(result))
        with self.assertRaisesRegex(ValueError, "selected APK"):
            ci_inputs.verify_results(self.apk, self.root, self.manifest)
        result["reuse"]["apkSha256"] = digest
        result["apiAgentSha256"] = "d" * 64
        (beer / "result.json").write_text(json.dumps(result))
        with self.assertRaisesRegex(ValueError, "same production"):
            ci_inputs.verify_results(self.apk, self.root, self.manifest)


if __name__ == "__main__":
    unittest.main()
