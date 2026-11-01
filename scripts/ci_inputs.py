"""Bind CI APK inputs to immutable public fixtures or this DroidLab checkout."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from droidlab_tools import duck_sdk, release  # noqa: E402


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def commit(root: Path) -> str:
    value = subprocess.run(["git", "-C", str(root), "rev-parse", "HEAD"],
                           check=True, capture_output=True, text=True).stdout.strip()
    if re.fullmatch(r"[0-9a-f]{40}", value) is None:
        raise ValueError("DroidLab HEAD must be a full commit")
    return value


def fixture_lock(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or value.get("schemaVersion") != 1:
        raise ValueError("Fixture lock must use schemaVersion 1")
    patterns = {"repository": r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+",
                "tag": r"[A-Za-z0-9_.-]+", "asset": r"[A-Za-z0-9_.-]+\.apk",
                "sha256": r"[0-9a-f]{64}"}
    for key, pattern in patterns.items():
        if not isinstance(value.get(key), str) or re.fullmatch(pattern, value[key]) is None:
            raise ValueError(f"Invalid fixture {key}")
    if "source" in value:
        source = value["source"]
        if (not isinstance(source, dict) or not isinstance(source.get("commit"), str)
                or re.fullmatch(r"[0-9a-f]{40}", source["commit"]) is None):
            raise ValueError("Fixture source must retain its original full commit")
    return value


def download_fixture(lock_path: Path, output: Path) -> None:
    lock = fixture_lock(lock_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        if sha256(output) != lock["sha256"]:
            raise ValueError("Existing fixture SHA256 differs from the lock")
        return
    url = f"https://github.com/{lock['repository']}/releases/download/{lock['tag']}/{lock['asset']}"
    with tempfile.NamedTemporaryFile(dir=output.parent, suffix=".download", delete=False) as stream:
        temporary = Path(stream.name)
    try:
        request = urllib.request.Request(url, headers={"User-Agent": "DroidLab-CI/1"})
        with urllib.request.urlopen(request, timeout=180) as response, temporary.open("wb") as stream:
            shutil.copyfileobj(response, stream)
        if sha256(temporary) != lock["sha256"]:
            raise ValueError("Downloaded fixture SHA256 differs from the lock")
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)


def lock_identity(root: Path) -> dict:
    xa = release.load_lock(root / "XA_MASS_RELEASE.lock.json")
    duck = duck_sdk.load_lock(root)
    return {"xaMassRelease": {"version": xa["version"], "gitCommit": xa["gitCommit"],
                              "runtimeZipSha256": xa["assets"]["serverRuntime"]["sha256"],
                              "sdkZipSha256": xa["assets"]["workerSdk"]["sha256"]},
            "duckSdk": {key: duck[key] for key in ("repository", "tag", "commit", "sha256")}}


def check_apk(apk: Path) -> None:
    with zipfile.ZipFile(apk) as archive:
        libraries = [name for name in archive.namelist() if name.startswith("lib/") and name.endswith(".so")]
        if not libraries or any(not name.startswith("lib/x86_64/") for name in libraries):
            raise ValueError("Current CI APK must contain only the x86_64 native ABI")
        if "lib/x86_64/libdetect_sample.so" not in libraries:
            raise ValueError("Current CI APK is missing the detect-env native target")


def record_apk(root: Path, apk: Path, manifest: Path) -> dict:
    check_apk(apk)
    value = {"schemaVersion": 1, "kind": "droidlab-current", "droidLabCommit": commit(root),
             "artifactName": apk.name, "apkSha256": sha256(apk), "abi": "x86_64",
             "applicationId": "com.detect.app.sample", **lock_identity(root)}
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    return value


def verify_apk(root: Path, apk: Path, manifest: Path) -> dict:
    value = json.loads(manifest.read_text(encoding="utf-8"))
    expected = {"schemaVersion": 1, "kind": "droidlab-current", "droidLabCommit": commit(root),
                "artifactName": apk.name, "apkSha256": sha256(apk), "abi": "x86_64",
                "applicationId": "com.detect.app.sample", **lock_identity(root)}
    if not isinstance(value, dict) or value != expected:
        raise ValueError("Current APK manifest differs from this DroidLab commit, input locks or APK bytes")
    check_apk(apk)
    return value


def verify_results(apk: Path, output: Path, production_manifest: Path) -> None:
    """Bind all observed installations to the selected fixed/current APK bytes."""
    expected = sha256(apk)
    for host in ("python", "node"):
        for mode in ("attach", "spawn"):
            result = json.loads((output / f"{host}-{mode}/result.json").read_text(encoding="utf-8"))
            if (result.get("apk", {}).get("sha256") != expected
                    or result.get("installedApk", {}).get("sha256") != expected):
                raise ValueError(f"{host}-{mode} did not use the selected APK bytes")
    reused = json.loads((output / "rootbeer/result.json").read_text(encoding="utf-8"))
    if reused.get("reuse", {}).get("apkSha256") != expected:
        raise ValueError("RootBeer reuse did not use the selected APK bytes")
    production = json.loads(production_manifest.read_text(encoding="utf-8"))
    copied = json.loads((output / "rootbeer/production-manifest.json").read_text(encoding="utf-8"))
    if (copied != production
            or reused.get("apiAgentSha256") != production.get("files", {}).get("dist/android-api.js")
            or not isinstance(reused.get("apiAgentSha256"), str)):
        raise ValueError("RootBeer did not use the same production artifact")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    fixture = commands.add_parser("download-fixture")
    fixture.add_argument("--lock", type=Path, required=True)
    fixture.add_argument("--output", type=Path, required=True)
    for name in ("record-apk", "verify-apk"):
        command = commands.add_parser(name)
        command.add_argument("--apk", type=Path, required=True)
        command.add_argument("--manifest", type=Path, required=True)
    results = commands.add_parser("verify-results")
    results.add_argument("--apk", type=Path, required=True)
    results.add_argument("--output", type=Path, required=True)
    results.add_argument("--production-manifest", type=Path, required=True)
    options = parser.parse_args()
    try:
        if options.command == "download-fixture":
            download_fixture(options.lock, options.output)
        elif options.command == "verify-results":
            verify_results(options.apk, options.output, options.production_manifest)
        else:
            operation = record_apk if options.command == "record-apk" else verify_apk
            operation(ROOT, options.apk, options.manifest)
    except (OSError, ValueError, release.ReleaseResolutionError, zipfile.BadZipFile) as error:
        print(f"CI input rejected: {error}", file=sys.stderr)
        return 1
    print(f"CI input {options.command}: verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
