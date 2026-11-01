from __future__ import annotations

import hashlib
import json
import stat
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path
from unittest.mock import patch


from agentforge_tools import release as resolver


COMMIT = "a" * 40


class ReleaseResolverTest(unittest.TestCase):

    @staticmethod
    def archive_cache(
        root: Path, lock: dict[str, object], component: str
    ) -> Path:
        return resolver._archive_cache_path(
            root / ".runtime", lock, component
        )

    def fixture(
        self,
        root: Path,
        *,
        version: str = "0.3.0",
        commit: str = COMMIT,
        runtime_schema: int = 6,
        extra_server_entry: tuple[str, bytes] | None = None,
    ) -> tuple[Path, Path, Path, dict[str, object]]:
        server = root / f"xa-mass-server-runtime-{version}.zip"
        server_root = f"xa-mass-server-runtime-{version}"
        runtime_manifest = {
            "schemaVersion": runtime_schema,
            "version": version,
            "gitCommit": commit,
            "serverJar": f"lib/xa-mass-server-jvm-{version}.jar",
            "javaVersion": 21,
            "defaultSpringProfile": "default",
            "springProfiles": ["default", "consumer", "preview"],
            "frontendIncluded": True,
        }
        with zipfile.ZipFile(server, "w") as archive:
            archive.writestr(
                f"{server_root}/manifest.json", json.dumps(runtime_manifest)
            )
            archive.writestr(
                f"{server_root}/lib/xa-mass-server-jvm-{version}.jar", b"jar"
            )
            archive.writestr(
                f"{server_root}/frontend/dist/index.html", b"html"
            )
            if extra_server_entry is not None:
                archive.writestr(*extra_server_entry)

        sdk = root / f"xa-mass-worker-sdk-{version}.zip"
        sdk_root = f"xa-mass-worker-sdk-{version}"
        sdk_manifest = {
            "schemaVersion": 1,
            "version": version,
            "gitCommit": commit,
            "repository": "repository",
            "artifacts": [
                {
                    "groupId": "com.xa.mass",
                    "artifactId": artifact,
                    "packaging": packaging,
                }
                for artifact, packaging in resolver._SDK_ARTIFACTS
            ],
        }
        with zipfile.ZipFile(sdk, "w") as archive:
            archive.writestr(
                f"{sdk_root}/manifest.json", json.dumps(sdk_manifest)
            )
            archive.writestr(f"{sdk_root}/LICENSE", b"license")
            archive.writestr(
                f"{sdk_root}/THIRD_PARTY_NOTICES.md", b"notices"
            )
            for artifact, packaging in resolver._SDK_ARTIFACTS:
                base = (
                    f"{sdk_root}/repository/com/xa/mass/{artifact}/{version}/"
                    f"{artifact}-{version}"
                )
                archive.writestr(f"{base}.{packaging}", b"binary")
                archive.writestr(f"{base}.pom", b"pom")
                archive.writestr(f"{base}-sources.jar", b"sources")

        lock = {
            "schemaVersion": 1,
            "repository": "quiaingmuarua/xa_mass_platform",
            "version": version,
            "gitCommit": commit,
            "assets": {
                "serverRuntime": {
                    "assetName": server.name,
                    "sha256": hashlib.sha256(server.read_bytes()).hexdigest(),
                },
                "workerSdk": {
                    "assetName": sdk.name,
                    "sha256": hashlib.sha256(sdk.read_bytes()).hexdigest(),
                },
            },
        }
        lock_path = root / "XA_MASS_RELEASE.lock.json"
        lock_path.write_text(json.dumps(lock), encoding="utf-8")
        return server, sdk, lock_path, lock

    def test_retired_profile_is_rejected_even_when_archive_identity_matches(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive, _, lock_path, lock = self.fixture(root)
            target, _ = resolver.resolve(lock_path, "server", archive=archive)
            path = target / "manifest.json"
            manifest = json.loads(path.read_text(encoding="utf-8"))
            manifest["springProfiles"] = ["default", "agentforge", "preview"]
            path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(resolver.ReleaseResolutionError, "Profiles"):
                resolver.verify_component(target, lock, "server")

    def test_resolves_both_components_and_cache_hit_uses_zero_network(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            server, sdk, lock_path, _ = self.fixture(root)
            server_root, _ = resolver.resolve(
                lock_path, "server", archive=server
            )
            sdk_root, _ = resolver.resolve(
                lock_path, "worker-sdk", archive=sdk
            )
            marker = json.loads(
                (sdk_root / resolver._MARKER).read_text(encoding="utf-8")
            )
            self.assertEqual(2, marker["schemaVersion"])
            server.unlink()
            sdk.unlink()
            with patch.object(
                resolver, "_download", side_effect=AssertionError("network")
            ):
                self.assertEqual(
                    server_root,
                    resolver.resolve(lock_path, "server")[0],
                )
                self.assertEqual(
                    sdk_root,
                    resolver.resolve(lock_path, "worker-sdk")[0],
                )

    def test_schema_one_marker_is_revalidated_and_rebuilt(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            _, sdk, lock_path, _ = self.fixture(root)
            destination, _ = resolver.resolve(
                lock_path, "worker-sdk", archive=sdk
            )
            marker_path = destination / resolver._MARKER
            marker = json.loads(marker_path.read_text(encoding="utf-8"))
            marker["schemaVersion"] = 1
            marker_path.write_text(json.dumps(marker), encoding="utf-8")
            sdk.unlink()

            with patch.object(
                resolver, "_download", side_effect=AssertionError("network")
            ):
                resolver.resolve(lock_path, "worker-sdk")

            rebuilt = json.loads(marker_path.read_text(encoding="utf-8"))
            self.assertEqual(2, rebuilt["schemaVersion"])

    def test_tampered_sdk_cache_is_repaired_from_locked_archive(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            _, sdk, lock_path, _ = self.fixture(root)
            destination, _ = resolver.resolve(
                lock_path, "worker-sdk", archive=sdk
            )
            version = "0.3.0"
            artifact = "xa-mass-android-worker"
            base = (
                destination
                / "repository/com/xa/mass"
                / artifact
                / version
                / f"{artifact}-{version}"
            )
            targets = [
                Path(f"{base}.aar"),
                Path(f"{base}.pom"),
                base.parent / f"{base.name}-sources.jar",
            ]
            sdk.unlink()
            for target in targets:
                with self.subTest(target=target.name):
                    target.write_bytes(b"tampered")
                    with patch.object(
                        resolver,
                        "_download",
                        side_effect=AssertionError("network"),
                    ):
                        resolver.resolve(lock_path, "worker-sdk")
                    self.assertNotEqual(b"tampered", target.read_bytes())

            missing = targets[0]
            missing.unlink()
            with patch.object(
                resolver,
                "_download",
                side_effect=AssertionError("network"),
            ):
                resolver.resolve(lock_path, "worker-sdk")
            self.assertTrue(missing.is_file())

            extra = destination / "repository/unexpected.txt"
            extra.write_bytes(b"extra")
            with patch.object(
                resolver,
                "_download",
                side_effect=AssertionError("network"),
            ):
                resolver.resolve(lock_path, "worker-sdk")
            self.assertFalse(extra.exists())

    def test_tampered_server_jar_is_repaired_from_locked_archive(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            server, _, lock_path, _ = self.fixture(root)
            destination, manifest = resolver.resolve(
                lock_path, "server", archive=server
            )
            server_jar = destination / manifest["serverJar"]
            server_jar.write_bytes(b"tampered")
            server.unlink()
            with patch.object(
                resolver,
                "_download",
                side_effect=AssertionError("network"),
            ):
                resolver.resolve(lock_path, "server")
            self.assertEqual(b"jar", server_jar.read_bytes())

    def test_unreadable_cache_entry_is_repaired_from_locked_archive(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            server, _, lock_path, _ = self.fixture(root)
            destination, _ = resolver.resolve(
                lock_path, "server", archive=server
            )
            unreadable = destination / "unreadable"
            unreadable.write_bytes(b"unreadable")
            original_is_file = Path.is_file

            def is_file(candidate: Path) -> bool:
                if candidate == unreadable:
                    raise OSError("unreadable cache entry")
                return original_is_file(candidate)

            server.unlink()
            with patch.object(Path, "is_file", is_file), patch.object(
                resolver,
                "_download",
                side_effect=AssertionError("network"),
            ):
                resolver.resolve(lock_path, "server")
            self.assertFalse(unreadable.exists())

    def test_corrupt_archive_cache_fails_closed_without_network(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            _, sdk, lock_path, lock = self.fixture(root)
            resolver.resolve(lock_path, "worker-sdk", archive=sdk)
            self.archive_cache(root, lock, "worker-sdk").write_bytes(
                b"corrupt"
            )
            with patch.object(
                resolver,
                "_download",
                side_effect=resolver.ReleaseResolutionError("offline"),
            ):
                with self.assertRaisesRegex(
                    resolver.ReleaseResolutionError, "offline"
                ):
                    resolver.resolve(lock_path, "worker-sdk")

    def test_corrupt_archive_cache_is_replaced_by_download(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            _, sdk, lock_path, lock = self.fixture(root)
            payload = sdk.read_bytes()
            destination, _ = resolver.resolve(
                lock_path, "worker-sdk", archive=sdk
            )
            cache = self.archive_cache(root, lock, "worker-sdk")
            cache.write_bytes(b"corrupt")

            def download(
                _lock: dict[str, object],
                _component: str,
                output: Path,
            ) -> None:
                output.write_bytes(payload)

            with patch.object(resolver, "_download", side_effect=download):
                repaired, _ = resolver.resolve(
                    lock_path, "worker-sdk"
                )
            self.assertEqual(destination, repaired)
            self.assertEqual(
                lock["assets"]["workerSdk"]["sha256"],
                hashlib.sha256(cache.read_bytes()).hexdigest(),
            )

    def test_corrupt_cache_is_replaced_from_verified_archive(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            server, _, lock_path, _ = self.fixture(root)
            destination, _ = resolver.resolve(
                lock_path, "server", archive=server
            )
            (destination / "manifest.json").unlink()
            repaired, manifest = resolver.resolve(
                lock_path, "server", archive=server
            )
            self.assertEqual(destination, repaired)
            self.assertEqual("0.3.0", manifest["version"])

    def test_lock_upgrade_switches_component_cache(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            server, _, lock_path, old_lock = self.fixture(root)
            destination, _ = resolver.resolve(
                lock_path, "server", archive=server
            )
            old_archive = self.archive_cache(root, old_lock, "server")
            self.assertTrue(old_archive.is_file())
            upgraded, _, _, new_lock = self.fixture(
                root, version="0.3.1", commit="b" * 40
            )
            same_destination, manifest = resolver.resolve(
                lock_path, "server", archive=upgraded
            )
            self.assertEqual(destination, same_destination)
            self.assertEqual("0.3.1", manifest["version"])
            self.assertFalse(old_archive.exists())
            self.assertTrue(
                self.archive_cache(root, new_lock, "server").is_file()
            )

    def test_new_release_failure_does_not_return_old_cache(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            server, _, lock_path, _ = self.fixture(root)
            resolver.resolve(lock_path, "server", archive=server)
            upgraded, _, _, lock = self.fixture(
                root, version="0.3.1", commit="b" * 40
            )
            lock["assets"]["serverRuntime"]["sha256"] = "0" * 64
            lock_path.write_text(json.dumps(lock), encoding="utf-8")
            with self.assertRaisesRegex(
                resolver.ReleaseResolutionError, "SHA256 mismatch"
            ):
                resolver.resolve(lock_path, "server", archive=upgraded)

    def test_rejects_missing_dual_asset_and_wrong_runtime_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            server, _, lock_path, lock = self.fixture(
                root, runtime_schema=2
            )
            with self.assertRaisesRegex(
                resolver.ReleaseResolutionError, "schemaVersion"
            ):
                resolver.resolve(lock_path, "server", archive=server)
            del lock["assets"]["workerSdk"]
            lock_path.write_text(json.dumps(lock), encoding="utf-8")
            with self.assertRaisesRegex(
                resolver.ReleaseResolutionError, "assets"
            ):
                resolver.load_lock(lock_path)

    def test_rejects_zip_escape_and_non_owned_destination(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            server, _, lock_path, lock = self.fixture(
                root, extra_server_entry=("../escaped.txt", b"escape")
            )
            lock["assets"]["serverRuntime"]["sha256"] = hashlib.sha256(
                server.read_bytes()
            ).hexdigest()
            lock_path.write_text(json.dumps(lock), encoding="utf-8")
            with self.assertRaisesRegex(
                resolver.ReleaseResolutionError, "escapes its root"
            ):
                resolver.resolve(lock_path, "server", archive=server)
            with self.assertRaisesRegex(
                resolver.ReleaseResolutionError, "destination must be"
            ):
                resolver.resolve(
                    lock_path,
                    "worker-sdk",
                    destination=root / "sdk",
                    archive=root / "missing.zip",
                )

    def test_rejects_duplicate_symlink_and_expanded_limit(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            server, _, lock_path, lock = self.fixture(root)
            server_root = "xa-mass-server-runtime-0.3.0"
            duplicate = f"{server_root}/lib/xa-mass-server-jvm-0.3.0.jar"
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(server, "a") as archive:
                    archive.writestr(duplicate, b"duplicate")
            lock["assets"]["serverRuntime"]["sha256"] = hashlib.sha256(
                server.read_bytes()
            ).hexdigest()
            lock_path.write_text(json.dumps(lock), encoding="utf-8")
            with self.assertRaisesRegex(
                resolver.ReleaseResolutionError, "duplicate entry"
            ):
                resolver.resolve(lock_path, "server", archive=server)

            server, _, lock_path, lock = self.fixture(root)
            link = zipfile.ZipInfo(f"{server_root}/link")
            link.create_system = 3
            link.external_attr = (stat.S_IFLNK | 0o777) << 16
            with zipfile.ZipFile(server, "a") as archive:
                archive.writestr(link, b"lib/xa-mass-server-jvm-1.0.0.jar")
            lock["assets"]["serverRuntime"]["sha256"] = hashlib.sha256(
                server.read_bytes()
            ).hexdigest()
            lock_path.write_text(json.dumps(lock), encoding="utf-8")
            with self.assertRaisesRegex(
                resolver.ReleaseResolutionError, "symlink"
            ):
                resolver.resolve(lock_path, "server", archive=server)

            server, _, lock_path, _ = self.fixture(root)
            with patch.object(resolver, "_MAX_EXPANDED_BYTES", 1):
                with self.assertRaisesRegex(
                    resolver.ReleaseResolutionError,
                    "expands beyond the limit",
                ):
                    resolver.resolve(lock_path, "server", archive=server)


if __name__ == "__main__":
    unittest.main()
