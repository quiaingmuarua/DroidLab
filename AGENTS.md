# DroidLab working guide

DroidLab owns the public source for DexLineage, HookForge and Android labs.
Use the root Gradle Wrapper and settings. Do not introduce nested repositories,
submodules, wrappers or independent Gradle builds.

Read the nearest module guidance before changing it. `dexlineage` owns static
APK analysis and knowledge; `hookforge` owns Frida tooling; `labs` owns public
Android samples. `droidlab_tools` owns only locked build-input preparation.
Keep private consumer deployment and research outside this repository.

Use `-Pandroid=false` for JVM/Node-only work. Preserve tool APIs, application
identities, fixture provenance and the distinction between unit/static checks
and actual device outcomes. Report the checks actually run and their limits.

The current integration task is authorized to adapt imported module guidance,
build entrypoints and CI to this single repository. Source repositories remain
read-only. Do not publish releases or change their repository settings.
