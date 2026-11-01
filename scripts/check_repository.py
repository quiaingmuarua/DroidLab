#!/usr/bin/env python3
"""Check local documentation links and the single Gradle build boundary."""
from __future__ import annotations

from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
LINK = re.compile(r"!?\[[^\]\n]*\]\((<[^>]+>|[^\s)]+)(?:\s+[^)]*)?\)")


def main() -> int:
    names = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=ROOT,
    ).decode().split("\0")
    files = sorted({name for name in names if name and (ROOT / name).is_file()})
    errors: list[str] = []
    settings = [p for p in files if Path(p).name in {"settings.gradle", "settings.gradle.kts"}]
    if settings != ["settings.gradle.kts"]:
        errors.append(f"expected one root settings file, found {settings}")
    wrappers = [p for p in files if Path(p).name in {"gradlew", "gradlew.bat"}]
    if wrappers != ["gradlew", "gradlew.bat"]:
        errors.append(f"expected only root Wrappers, found {wrappers}")
    if (ROOT / ".gitmodules").exists():
        errors.append("DroidLab source components must not be submodules")
    for name in files:
        if not name.endswith(".md"):
            continue
        source = ROOT / name
        contents = re.sub(r"```.*?```", "", source.read_text(encoding="utf-8"), flags=re.S)
        for match in LINK.finditer(contents):
            target = match.group(1).strip("<>")
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc or not parsed.path:
                continue
            path = unquote(parsed.path)
            # Runtime examples are intentionally generated or supplied by users.
            if any(part in {".runtime", ".local", "build", "node_modules", "third_party_readonly"}
                   for part in Path(path).parts):
                continue
            resolved = ROOT / path.lstrip("/") if path.startswith("/") else source.parent / path
            if not resolved.exists():
                errors.append(f"{name}: missing local link {target}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Repository boundaries and local Markdown links checked ({len(files)} files).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
