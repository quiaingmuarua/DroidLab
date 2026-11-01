"""Select CI lanes from the actual Git diff, including deleted and renamed paths."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess


LANES = ("dex", "hook", "labs", "device")


def classify(paths: list[str]) -> dict[str, bool]:
    result = dict.fromkeys(LANES, False)
    for path in paths:
        name = PurePosixPath(path)
        if name.suffix.lower() in {".md", ".rst", ".adoc"} or (
            path.startswith("docs/") and name.suffix.lower() in {".png", ".jpg", ".jpeg", ".svg"}
        ) or name.name in {"LICENSE", "NOTICE", "THIRD_PARTY_NOTICES"}:
            continue
        if path.startswith("dexlineage/"):
            result["dex"] = True
        elif path.startswith(("hookforge/", "labs/")):
            result.update(hook=True, labs=True, device=True)
        else:
            return dict.fromkeys(LANES, True)
    return result


def git(*arguments: str) -> bytes:
    return subprocess.run(["git", *arguments], check=True, stdout=subprocess.PIPE).stdout


def changed_paths(event: dict, event_name: str) -> list[str] | None:
    if event_name == "workflow_dispatch":
        return None
    if event_name == "pull_request":
        base = event.get("pull_request", {}).get("base", {}).get("sha")
    elif event_name == "push":
        base = event.get("before")
    else:
        raise ValueError(f"Unsupported CI event: {event_name}")
    if base == "0" * 40:
        return None
    if not isinstance(base, str) or re.fullmatch(r"[0-9a-f]{40}", base) is None:
        raise ValueError("CI diff base must be a full Git commit")
    # A full-history checkout contains the PR base and the push-before commit.
    # PR checkout is the merge commit; three-dot avoids unrelated base changes.
    revision = f"{base}...HEAD" if event_name == "pull_request" else base
    arguments = ["diff", "--name-only", "--no-renames", "-z", revision]
    if event_name == "push":
        arguments.append("HEAD")
    # A missing base fails the job, never silently becoming documentation-only.
    changed = git(*arguments, "--")
    return changed.decode("utf-8").rstrip("\0").split("\0") if changed else []


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event", type=Path, default=os.environ.get("GITHUB_EVENT_PATH"))
    parser.add_argument("--event-name", default=os.environ.get("GITHUB_EVENT_NAME"))
    parser.add_argument("--github-output", type=Path, default=os.environ.get("GITHUB_OUTPUT"))
    options = parser.parse_args()
    if options.event is None:
        parser.error("--event is required")
    paths = changed_paths(json.loads(options.event.read_text(encoding="utf-8")), options.event_name)
    selected = dict.fromkeys(LANES, True) if paths is None else classify(paths)
    print(json.dumps({"paths": paths, "lanes": selected}, indent=2))
    if options.github_output:
        with options.github_output.open("a", encoding="utf-8") as stream:
            for key, value in selected.items():
                stream.write(f"{key}={str(value).lower()}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
