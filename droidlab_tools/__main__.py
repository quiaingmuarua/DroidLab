"""Prepare or verify the locked inputs used by DroidLab's Android labs."""
from __future__ import annotations

import argparse
from pathlib import Path
import sys
from typing import Sequence
import zipfile

from . import duck_sdk, release


ROOT = Path(__file__).resolve().parents[1]


def main(arguments: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("release").add_subparsers(dest="action", required=True).add_parser(
        "resolve", add_help=False
    )
    commands.add_parser("duck-sdk").add_subparsers(dest="action", required=True).add_parser(
        "resolve", add_help=False
    )
    verify = commands.add_parser("verify", help="Verify prepared inputs without downloads or writes")
    verify.add_argument("--component", choices=("all", "worker-sdk", "duck-sdk"), default="all")
    verify.add_argument("--lock", type=Path, default=ROOT / "XA_MASS_RELEASE.lock.json")
    options, remaining = parser.parse_known_args(arguments)
    try:
        if options.command == "release":
            return release.main(remaining)
        if options.command == "duck-sdk":
            return duck_sdk.main(remaining)
        if remaining:
            parser.error("unrecognized arguments: " + " ".join(remaining))
        lock_path = options.lock.resolve()
        if options.component in ("all", "worker-sdk"):
            release.verify_installed(lock_path, "worker-sdk")
        if options.component in ("all", "duck-sdk"):
            duck_sdk.verify_installed(lock_path.parent)
        print(f"Verified prepared DroidLab inputs: {options.component}")
        return 0
    except (release.ReleaseResolutionError, OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"DroidLab input verification/preparation failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
