from __future__ import annotations

import subprocess
import unittest
from unittest.mock import patch

from scripts import ci_changes


class ChangeSelectionTest(unittest.TestCase):
    def test_documentation_only_runs_no_build_or_device_lanes(self):
        self.assertEqual(dict.fromkeys(ci_changes.LANES, False), ci_changes.classify([
            "README.md", "labs/detect-env/CLAUDE.md", "hookforge/docs/CI.md", "docs/layout.svg"
        ]))

    def test_dex_change_runs_only_dex(self):
        self.assertEqual({"dex": True, "hook": False, "labs": False, "device": False},
                         ci_changes.classify(["dexlineage/analysis/src/main/A.kt"]))

    def test_either_side_of_android_hook_contract_runs_both_builds_and_device(self):
        for path in ("hookforge/packages/api.ts", "labs/detect-env/build.gradle"):
            with self.subTest(path=path):
                self.assertEqual({"dex": False, "hook": True, "labs": True, "device": True},
                                 ci_changes.classify([path]))

    def test_shared_inputs_and_mixed_changes_select_every_lane(self):
        for paths in (["settings.gradle.kts"], ["XA_MASS_RELEASE.lock.json"],
                      [".github/workflows/ci.yml"], ["droidlab_tools/release.py"],
                      ["tests/test_ci_inputs.py"], ["dexlineage/a.kt", "hookforge/b.ts"]):
            with self.subTest(paths=paths):
                self.assertEqual(dict.fromkeys(ci_changes.LANES, True), ci_changes.classify(paths))

    def test_diff_preserves_deletions_and_both_sides_of_rename(self):
        with patch.object(ci_changes, "git", return_value=b"hookforge/old.ts\0dexlineage/new.kt\0") as git:
            paths = ci_changes.changed_paths({"before": "a" * 40}, "push")
        self.assertEqual(["hookforge/old.ts", "dexlineage/new.kt"], paths)
        self.assertIn("--no-renames", git.call_args.args)
        self.assertIn("HEAD", git.call_args.args)

    def test_pr_uses_merge_base_and_manual_or_initial_push_runs_all(self):
        with patch.object(ci_changes, "git", return_value=b"") as git:
            self.assertEqual([], ci_changes.changed_paths({"pull_request": {"base": {"sha": "a" * 40}}}, "pull_request"))
            self.assertIn("a" * 40 + "...HEAD", git.call_args.args)
        self.assertIsNone(ci_changes.changed_paths({}, "workflow_dispatch"))
        self.assertIsNone(ci_changes.changed_paths({"before": "0" * 40}, "push"))

    def test_missing_or_unavailable_base_fails_instead_of_skipping_lanes(self):
        with self.assertRaises(ValueError):
            ci_changes.changed_paths({}, "pull_request")
        with patch.object(ci_changes, "git", side_effect=subprocess.CalledProcessError(128, "git")):
            with self.assertRaises(subprocess.CalledProcessError):
                ci_changes.changed_paths({"before": "a" * 40}, "push")


if __name__ == "__main__":
    unittest.main()
