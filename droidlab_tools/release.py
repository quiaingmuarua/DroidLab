#!/usr/bin/env python3
"""Resolve one component from the immutable XA Mass release lock."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import stat
import tempfile
import urllib.request
import uuid
import zipfile
from pathlib import Path, PurePosixPath
from typing import Any


_REPOSITORY = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
_VERSION = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+")
_COMMIT = re.compile(r"[0-9a-f]{40}")
_SHA256 = re.compile(r"[0-9a-f]{64}")
_MAX_ARCHIVE_BYTES = 512 * 1024 * 1024
_MAX_EXPANDED_BYTES = 2 * 1024 * 1024 * 1024
_MARKER = ".droidlab-xa-mass-release.json"
_MARKER_SCHEMA_VERSION = 2
_ARCHIVE_CACHE_DIRECTORY = "xa-mass-release-cache"
_SDK_ARTIFACTS = (
    ("xa-mass-worker-delivery-contract", "jar"),
    ("xa-mass-worker-core", "jar"),
    ("xa-mass-android-worker", "aar"),
    ("xa-mass-android-capability-http", "aar"),
)
_COMPONENTS = {
    "server": {
        "assetKey": "serverRuntime",
        "baseName": "xa-mass-server-runtime",
        "destination": "xa-mass-server",
    },
    "worker-sdk": {
        "assetKey": "workerSdk",
        "baseName": "xa-mass-worker-sdk",
        "destination": "xa-mass-worker-sdk",
    },
}


class ReleaseResolutionError(RuntimeError):
    pass


def load_lock(path: Path) -> dict[str, Any]:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ReleaseResolutionError(f"XA Mass release lock is unreadable: {path}") from error
    if not isinstance(document, dict) or set(document) != {
        "schemaVersion",
        "repository",
        "version",
        "gitCommit",
        "assets",
    }:
        raise ReleaseResolutionError("XA Mass release lock fields do not match schema 1")
    if document.get("schemaVersion") != 1:
        raise ReleaseResolutionError("XA Mass release lock schemaVersion must be 1")
    repository = document.get("repository")
    version = document.get("version")
    commit = document.get("gitCommit")
    assets = document.get("assets")
    if not isinstance(repository, str) or _REPOSITORY.fullmatch(repository) is None:
        raise ReleaseResolutionError("XA Mass release repository is invalid")
    if not isinstance(version, str) or _VERSION.fullmatch(version) is None:
        raise ReleaseResolutionError("XA Mass release version is invalid")
    if not isinstance(commit, str) or _COMMIT.fullmatch(commit) is None:
        raise ReleaseResolutionError("XA Mass release commit is invalid")
    if not isinstance(assets, dict) or set(assets) != {
        "serverRuntime",
        "workerSdk",
    }:
        raise ReleaseResolutionError("XA Mass release assets do not match schema 1")
    for component, spec in _COMPONENTS.items():
        asset = assets.get(spec["assetKey"])
        if not isinstance(asset, dict) or set(asset) != {"assetName", "sha256"}:
            raise ReleaseResolutionError(f"XA Mass {component} asset is invalid")
        expected_name = f"{spec['baseName']}-{version}.zip"
        if asset.get("assetName") != expected_name:
            raise ReleaseResolutionError(
                f"XA Mass {component} assetName must be {expected_name}"
            )
        digest = asset.get("sha256")
        if not isinstance(digest, str) or _SHA256.fullmatch(digest) is None:
            raise ReleaseResolutionError(f"XA Mass {component} SHA256 is invalid")
    return document


def _component_spec(component: str) -> dict[str, str]:
    try:
        return _COMPONENTS[component]
    except KeyError as error:
        raise ReleaseResolutionError(f"Unknown XA Mass component: {component}") from error


def _asset(lock: dict[str, Any], component: str) -> dict[str, str]:
    spec = _component_spec(component)
    return lock["assets"][spec["assetKey"]]


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _verify_archive_hash(
    path: Path, lock: dict[str, Any], component: str
) -> None:
    if not path.is_file():
        raise ReleaseResolutionError(f"XA Mass {component} archive is missing: {path}")
    if path.stat().st_size > _MAX_ARCHIVE_BYTES:
        raise ReleaseResolutionError(f"XA Mass {component} archive exceeds the limit")
    expected = _asset(lock, component)["sha256"]
    actual = _sha256(path)
    if actual != expected:
        raise ReleaseResolutionError(
            f"XA Mass {component} SHA256 mismatch: expected {expected}, found {actual}"
        )


def _inspect_zip(
    archive: Path, lock: dict[str, Any], component: str
) -> str:
    spec = _component_spec(component)
    expected_root = f"{spec['baseName']}-{lock['version']}"
    expanded_bytes = 0
    names: set[str] = set()
    with zipfile.ZipFile(archive) as payload:
        for entry in payload.infolist():
            path = PurePosixPath(entry.filename)
            if path.is_absolute() or not path.parts or ".." in path.parts:
                raise ReleaseResolutionError(
                    f"XA Mass {component} ZIP entry escapes its root: {entry.filename}"
                )
            if path.parts[0] != expected_root:
                raise ReleaseResolutionError(
                    f"XA Mass {component} ZIP has an unexpected root"
                )
            if entry.filename in names:
                raise ReleaseResolutionError(
                    f"XA Mass {component} ZIP contains a duplicate entry"
                )
            names.add(entry.filename)
            file_type = (entry.external_attr >> 16) & 0o170000
            if file_type == stat.S_IFLNK:
                raise ReleaseResolutionError(
                    f"XA Mass {component} ZIP contains a symlink"
                )
            expanded_bytes += entry.file_size
            if expanded_bytes > _MAX_EXPANDED_BYTES:
                raise ReleaseResolutionError(
                    f"XA Mass {component} ZIP expands beyond the limit"
                )
    return expected_root


def _archive_file_entries(
    payload: zipfile.ZipFile,
    root_name: str,
    component: str,
) -> dict[str, zipfile.ZipInfo]:
    result: dict[str, zipfile.ZipInfo] = {}
    for entry in payload.infolist():
        path = PurePosixPath(entry.filename)
        if entry.is_dir() or len(path.parts) == 1:
            continue
        relative = PurePosixPath(*path.parts[1:]).as_posix()
        if path.parts[0] != root_name:
            raise ReleaseResolutionError(
                f"XA Mass {component} ZIP has an unexpected root"
            )
        if relative == _MARKER:
            raise ReleaseResolutionError(
                f"XA Mass {component} ZIP contains a reserved marker"
            )
        result[relative] = entry
    return result


def _sha256_zip_entry(
    payload: zipfile.ZipFile, entry: zipfile.ZipInfo
) -> str:
    digest = hashlib.sha256()
    with payload.open(entry) as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _verify_extracted_tree(
    root: Path,
    archive: Path,
    root_name: str,
    component: str,
) -> None:
    if root.is_symlink() or not root.is_dir():
        raise ReleaseResolutionError(
            f"XA Mass {component} extracted cache is invalid"
        )
    actual: dict[str, Path] = {}
    try:
        for candidate in root.rglob("*"):
            if candidate.is_symlink():
                raise ReleaseResolutionError(
                    f"XA Mass {component} extracted cache contains a symlink"
                )
            if candidate.is_file():
                relative = candidate.relative_to(root).as_posix()
                if relative != _MARKER:
                    actual[relative] = candidate
    except OSError as error:
        raise ReleaseResolutionError(
            f"XA Mass {component} extracted cache contains an unreadable entry"
        ) from error

    with zipfile.ZipFile(archive) as payload:
        expected = _archive_file_entries(payload, root_name, component)
        if set(actual) != set(expected):
            raise ReleaseResolutionError(
                f"XA Mass {component} extracted cache file set differs"
            )
        for relative, entry in expected.items():
            candidate = actual[relative]
            if candidate.stat().st_size != entry.file_size:
                raise ReleaseResolutionError(
                    f"XA Mass {component} extracted cache content differs: "
                    f"{relative}"
                )
            if _sha256(candidate) != _sha256_zip_entry(payload, entry):
                raise ReleaseResolutionError(
                    f"XA Mass {component} extracted cache content differs: "
                    f"{relative}"
                )


def _safe_member(root: Path, relative: object, name: str) -> Path:
    if not isinstance(relative, str) or not relative:
        raise ReleaseResolutionError(f"XA Mass manifest {name} is invalid")
    path = Path(relative)
    if path.is_absolute() or ".." in path.parts:
        raise ReleaseResolutionError(f"XA Mass manifest {name} escapes its root")
    candidate = (root / path).resolve()
    try:
        candidate.relative_to(root.resolve())
    except ValueError as error:
        raise ReleaseResolutionError(
            f"XA Mass manifest {name} escapes its root"
        ) from error
    if not candidate.is_file():
        raise ReleaseResolutionError(f"XA Mass manifest {name} is missing")
    return candidate


def _load_manifest(root: Path) -> dict[str, Any]:
    try:
        manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ReleaseResolutionError("XA Mass manifest is unreadable") from error
    if not isinstance(manifest, dict):
        raise ReleaseResolutionError("XA Mass manifest must be an object")
    return manifest


def _verify_runtime(
    root: Path, lock: dict[str, Any]
) -> dict[str, Any]:
    manifest = _load_manifest(root)
    if manifest.get("schemaVersion") != 6:
        raise ReleaseResolutionError("Server Runtime manifest schemaVersion must be 6")
    if manifest.get("version") != lock["version"]:
        raise ReleaseResolutionError("Server Runtime version differs from lock")
    if manifest.get("gitCommit") != lock["gitCommit"]:
        raise ReleaseResolutionError("Server Runtime commit differs from lock")
    if manifest.get("defaultSpringProfile") != "default" or manifest.get(
        "springProfiles"
    ) != ["default", "consumer", "preview"]:
        raise ReleaseResolutionError("Server Runtime Profiles differ from lock contract")
    if manifest.get("javaVersion") != 21:
        raise ReleaseResolutionError("Server Runtime toolchain contract differs")
    if manifest.get("frontendIncluded") is not True:
        raise ReleaseResolutionError("Server Runtime frontend is missing")
    _safe_member(root, manifest.get("serverJar"), "serverJar")
    if manifest.get("serverJar") != f"lib/xa-mass-server-jvm-{lock['version']}.jar":
        raise ReleaseResolutionError("Server Runtime JAR differs from lock coordinate")
    _safe_member(root, "frontend/dist/index.html", "frontend")
    return manifest


def _verify_worker_sdk(
    root: Path, lock: dict[str, Any]
) -> dict[str, Any]:
    manifest = _load_manifest(root)
    if manifest.get("schemaVersion") != 1:
        raise ReleaseResolutionError("Worker SDK manifest schemaVersion must be 1")
    if manifest.get("version") != lock["version"]:
        raise ReleaseResolutionError("Worker SDK version differs from lock")
    if manifest.get("gitCommit") != lock["gitCommit"]:
        raise ReleaseResolutionError("Worker SDK commit differs from lock")
    if manifest.get("repository") != "repository":
        raise ReleaseResolutionError("Worker SDK repository path is invalid")
    expected = [
        {
            "groupId": "com.xa.mass",
            "artifactId": artifact,
            "packaging": packaging,
        }
        for artifact, packaging in _SDK_ARTIFACTS
    ]
    if manifest.get("artifacts") != expected:
        raise ReleaseResolutionError("Worker SDK artifact catalog differs")
    version = lock["version"]
    for artifact, packaging in _SDK_ARTIFACTS:
        base = f"repository/com/xa/mass/{artifact}/{version}/{artifact}-{version}"
        _safe_member(root, f"{base}.{packaging}", f"{artifact} binary")
        _safe_member(root, f"{base}.pom", f"{artifact} POM")
        _safe_member(root, f"{base}-sources.jar", f"{artifact} sources")
    _safe_member(root, "LICENSE", "LICENSE")
    _safe_member(root, "THIRD_PARTY_NOTICES.md", "third-party notices")
    return manifest


def verify_component(
    root: Path, lock: dict[str, Any], component: str
) -> dict[str, Any]:
    if component == "server":
        return _verify_runtime(root, lock)
    if component == "worker-sdk":
        return _verify_worker_sdk(root, lock)
    raise ReleaseResolutionError(f"Unknown XA Mass component: {component}")


def verify_archive(
    archive: Path, lock: dict[str, Any], component: str
) -> dict[str, Any]:
    _verify_archive_hash(archive, lock, component)
    root_name = _inspect_zip(archive, lock, component)
    with tempfile.TemporaryDirectory(prefix="droidlab-xa-mass-verify-") as temporary:
        staging = Path(temporary)
        with zipfile.ZipFile(archive) as payload:
            payload.extractall(staging)
        return verify_component(staging / root_name, lock, component)


def _runtime_directory(lock_path: Path) -> Path:
    return lock_path.resolve().parent / ".runtime"


def _archive_cache_path(
    runtime_directory: Path,
    lock: dict[str, Any],
    component: str,
) -> Path:
    return (
        runtime_directory
        / _ARCHIVE_CACHE_DIRECTORY
        / _asset(lock, component)["assetName"]
    )


def _require_archive_cache_directory(runtime_directory: Path) -> Path:
    cache_directory = runtime_directory / _ARCHIVE_CACHE_DIRECTORY
    if cache_directory.is_symlink():
        raise ReleaseResolutionError(
            "XA Mass release archive cache cannot be a symlink"
        )
    cache_directory.mkdir(parents=True, exist_ok=True)
    return cache_directory


def _store_verified_archive(
    source: Path,
    cache: Path,
    lock: dict[str, Any],
    component: str,
) -> Path:
    _verify_archive_hash(source, lock, component)
    if source.resolve() == cache.resolve(strict=False):
        return cache
    staging = cache.parent / f".{cache.name}.staging-{uuid.uuid4().hex}"
    try:
        shutil.copyfile(source, staging)
        _verify_archive_hash(staging, lock, component)
        os.replace(staging, cache)
    except OSError as error:
        raise ReleaseResolutionError(
            f"XA Mass {component} archive cache replacement failed"
        ) from error
    finally:
        staging.unlink(missing_ok=True)
    return cache


def _resolve_locked_archive(
    runtime_directory: Path,
    lock: dict[str, Any],
    component: str,
    archive: Path | None,
) -> Path:
    cache_directory = _require_archive_cache_directory(runtime_directory)
    cache = _archive_cache_path(runtime_directory, lock, component)
    if archive is not None:
        return _store_verified_archive(
            archive.resolve(), cache, lock, component
        )
    if cache.is_file() and not cache.is_symlink():
        try:
            _verify_archive_hash(cache, lock, component)
            return cache
        except ReleaseResolutionError:
            pass

    download = cache_directory / (
        f".{cache.name}.download-{uuid.uuid4().hex}.zip"
    )
    try:
        _download(lock, component, download)
        return _store_verified_archive(
            download, cache, lock, component
        )
    finally:
        download.unlink(missing_ok=True)


def _cleanup_old_archives(
    cache: Path,
    component: str,
) -> None:
    base_name = _component_spec(component)["baseName"]
    for candidate in cache.parent.glob(f"{base_name}-*.zip"):
        if (
            candidate != cache
            and candidate.is_file()
            and not candidate.is_symlink()
        ):
            candidate.unlink()


def default_destination(lock_path: Path, component: str) -> Path:
    spec = _component_spec(component)
    return _runtime_directory(lock_path) / spec["destination"]


def _require_owned_destination(
    destination: Path, lock_path: Path, component: str
) -> Path:
    runtime_directory = _runtime_directory(lock_path).resolve()
    if runtime_directory.is_symlink():
        raise ReleaseResolutionError("DroidLab .runtime cannot be a symlink")
    expected = default_destination(lock_path, component).resolve(strict=False)
    if destination.resolve(strict=False) != expected:
        raise ReleaseResolutionError(
            f"{component} destination must be {default_destination(lock_path, component)}"
        )
    if destination.is_symlink():
        raise ReleaseResolutionError(f"{component} destination cannot be a symlink")
    return runtime_directory


def _marker(lock: dict[str, Any], component: str) -> dict[str, Any]:
    return {
        "schemaVersion": _MARKER_SCHEMA_VERSION,
        "component": component,
        "release": lock,
    }


def _marker_matches(
    destination: Path, lock: dict[str, Any], component: str
) -> bool:
    marker = destination / _MARKER
    if marker.is_symlink():
        return False
    try:
        value = json.loads(marker.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return False
    return value == _marker(lock, component)


def _download(lock: dict[str, Any], component: str, output: Path) -> None:
    asset_name = _asset(lock, component)["assetName"]
    url = (
        f"https://github.com/{lock['repository']}/releases/download/"
        f"v{lock['version']}/{asset_name}"
    )
    request = urllib.request.Request(
        url, headers={"User-Agent": "DroidLab-XA-Mass-Release-Resolver/1"}
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            with output.open("wb") as target:
                shutil.copyfileobj(response, target)
    except OSError as error:
        raise ReleaseResolutionError(f"XA Mass {component} download failed: {url}") from error


def _replace_destination(
    destination: Path, extracted: Path, runtime_directory: Path
) -> None:
    backup = runtime_directory / f".{destination.name}.backup-{uuid.uuid4().hex}"
    if destination.exists():
        os.replace(destination, backup)
    try:
        os.replace(extracted, destination)
    except OSError:
        if backup.exists() and not destination.exists():
            os.replace(backup, destination)
        raise
    if backup.exists():
        shutil.rmtree(backup)


def resolve(
    lock_path: Path,
    component: str,
    destination: Path | None = None,
    *,
    archive: Path | None = None,
) -> tuple[Path, dict[str, Any]]:
    resolved_lock = lock_path.resolve()
    lock = load_lock(resolved_lock)
    target = (destination or default_destination(resolved_lock, component)).resolve()
    runtime_directory = _require_owned_destination(target, resolved_lock, component)
    runtime_directory.mkdir(parents=True, exist_ok=True)
    source = _resolve_locked_archive(
        runtime_directory,
        lock,
        component,
        archive,
    )
    root_name = _inspect_zip(source, lock, component)
    if target.is_dir() and _marker_matches(target, lock, component):
        try:
            _verify_extracted_tree(
                target, source, root_name, component
            )
            manifest = verify_component(target, lock, component)
            _cleanup_old_archives(source, component)
            return target, manifest
        except ReleaseResolutionError:
            pass

    staging = runtime_directory / f".{target.name}.staging-{uuid.uuid4().hex}"
    try:
        staging.mkdir()
        with zipfile.ZipFile(source) as payload:
            payload.extractall(staging)
        extracted = staging / root_name
        _verify_extracted_tree(
            extracted, source, root_name, component
        )
        manifest = verify_component(extracted, lock, component)
        (extracted / _MARKER).write_text(
            json.dumps(_marker(lock, component), indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        _replace_destination(target, extracted, runtime_directory)
        _cleanup_old_archives(source, component)
        return target, manifest
    except (OSError, zipfile.BadZipFile) as error:
        raise ReleaseResolutionError(
            f"XA Mass {component} cache replacement failed"
        ) from error
    finally:
        if staging.exists():
            shutil.rmtree(staging)


def verify_installed(lock_path: Path, component: str) -> dict[str, Any]:
    """Verify prepared bytes without downloading, repairing or writing cache state."""
    resolved_lock = lock_path.resolve()
    lock = load_lock(resolved_lock)
    runtime_directory = _runtime_directory(resolved_lock)
    target = default_destination(resolved_lock, component)
    archive = _archive_cache_path(runtime_directory, lock, component)
    if runtime_directory.is_symlink() or archive.parent.is_symlink() or archive.is_symlink():
        raise ReleaseResolutionError("XA Mass prepared cache cannot be a symlink")
    if not target.is_dir() or not archive.is_file():
        raise ReleaseResolutionError(
            f"XA Mass {component} is not prepared. From the DroidLab root run: "
            f"python -m droidlab_tools release resolve --component {component} "
            "--lock XA_MASS_RELEASE.lock.json"
        )
    if not _marker_matches(target, lock, component):
        raise ReleaseResolutionError("XA Mass prepared cache identity differs from the lock")
    _verify_archive_hash(archive, lock, component)
    root_name = _inspect_zip(archive, lock, component)
    _verify_extracted_tree(target, archive, root_name, component)
    return verify_component(target, lock, component)


def _write_github_output(
    path: Path, component: str, root: Path, lock: dict[str, Any]
) -> None:
    with path.open("a", encoding="utf-8") as output:
        output.write(f"component-root={root}\n")
        if component == "server":
            output.write(f"runtime-root={root}\n")
        output.write(f"version={lock['version']}\n")
        output.write(f"git-commit={lock['gitCommit']}\n")


def main(arguments: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--component", choices=tuple(_COMPONENTS), required=True)
    parser.add_argument(
        "--lock",
        type=Path,
        required=True,
    )
    parser.add_argument("--destination", type=Path)
    parser.add_argument("--archive", type=Path)
    parser.add_argument("--github-output", type=Path)
    parsed = parser.parse_args(arguments)
    lock_path = parsed.lock.resolve()
    lock = load_lock(lock_path)
    root, _ = resolve(
        lock_path,
        parsed.component,
        parsed.destination.resolve() if parsed.destination else None,
        archive=parsed.archive.resolve() if parsed.archive else None,
    )
    if parsed.github_output:
        _write_github_output(
            parsed.github_output.resolve(), parsed.component, root, lock
        )
    print(
        "Resolved XA Mass release component: "
        f"component={parsed.component} version={lock['version']} "
        f"commit={lock['gitCommit']} root={root}"
    )
    return 0
