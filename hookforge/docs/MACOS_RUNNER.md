# macOS and ARM64 scope

DroidLab does not register or run a macOS self-hosted CI lane in this migration.
Its public device baseline is Linux/API33 x86_64. Native macOS host checks are
not evidence of Android ARM64 behavior.

The imported API33 tools retain `--abi arm64-v8a`, isolated emulator ownership
and cleanup support for an explicitly prepared manual environment. No runner
permission, machine ownership, label or private consumer workflow is changed.

The former AgentForge integration and its dedicated runner are historical,
external provenance. Its operations guide remains at the original
[upstream path](https://github.com/quiaingmuarua/AgentForge/blob/main/docs/FRIDA_MACOS_RUNNER.md).
That guide is not a DroidLab setup requirement or a claim of current availability.
See [DroidLab CI](CI.md) for the active public scope.
