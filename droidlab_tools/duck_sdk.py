"""Resolve the original, fixed Duck SDK AAR; never rebuild detector code."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
import urllib.request


ROOT = Path(__file__).resolve().parents[1]
LOCK_PATH = Path("crackme/detect-env/duck-sdk.lock.json")


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def load_lock(root: Path = ROOT) -> dict:
    lock = json.loads((root / LOCK_PATH).read_text(encoding="utf-8"))
    if lock.get("schemaVersion") != 1:
        raise ValueError("Duck SDK lock schemaVersion must be 1")
    for key, length in (("commit", 40), ("sha256", 64)):
        if not re.fullmatch(r"[0-9a-f]{" + str(length) + "}", lock.get(key, "")):
            raise ValueError(f"Duck SDK {key} is invalid")
    asset = lock.get("asset", "")
    if not asset.endswith(".aar") or Path(asset).name != asset or "/" in asset or "\\" in asset:
        raise ValueError("Duck SDK asset must be an AAR filename")
    dependencies = lock.get("dependencies")
    if not isinstance(dependencies, list) or not dependencies or any(
        not isinstance(item, str) or not re.fullmatch(r"[\w.]+:[\w.-]+:[\w.-]+", item)
        for item in dependencies
    ):
        raise ValueError("Duck SDK dependencies must have exact Maven versions")
    return lock


def download(url: str, target: Path) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "AgentForge-Duck-SDK/1"})
    with urllib.request.urlopen(request, timeout=180) as response, target.open("wb") as stream:
        shutil.copyfileobj(response, stream)


def resolve(root: Path = ROOT, archive: Path | None = None) -> dict:
    lock = load_lock(root)
    destination = root / ".runtime/duck-detector/sdk" / lock["asset"]
    destination.parent.mkdir(parents=True, exist_ok=True)

    def verify(path: Path) -> None:
        if sha256(path) != lock["sha256"]:
            raise ValueError(f"Duck SDK SHA256 differs from lock: {path}")

    if destination.exists():
        verify(destination)
    else:
        handle, name = tempfile.mkstemp(prefix=".duck-", suffix=".aar", dir=destination.parent)
        os.close(handle)
        temporary = Path(name)
        try:
            if archive is not None:
                shutil.copyfile(archive, temporary)
            else:
                download(
                    f"https://github.com/{lock['repository']}/releases/download/"
                    f"{lock['tag']}/{lock['asset']}", temporary,
                )
            verify(temporary)
            os.replace(temporary, destination)
        finally:
            temporary.unlink(missing_ok=True)
    return {"path": str(destination.resolve()), "tag": lock["tag"],
            "commit": lock["commit"], "sha256": lock["sha256"]}


def main(arguments: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, help="Verify and consume an already downloaded AAR")
    args = parser.parse_args(arguments)
    try:
        print(json.dumps(resolve(archive=args.archive), indent=2))
        return 0
    except (OSError, ValueError) as error:
        print(f"Duck SDK resolution failed: {error}", file=sys.stderr)
        return 1
