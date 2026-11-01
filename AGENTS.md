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

Run commands from the DroidLab root. Root `check` is a host-only check with
`-Pandroid=false`; Android labs and fixture verification are explicit when
working without an SDK. See `docs/BUILD_AND_CI.md` for task ownership and CI.

HookForge's check produces development files; its production build must follow
that check before recording a manifest. Commit identities in new tool evidence
refer to this repository. Historical fixture source identities stay unchanged.
Read `dexlineage/docs/fixture-testing.md` before changing the compiler or the
isolated fixture R8 profile; its constrained shapes are not general coverage of
all R8 transformations.

Use locked build inputs, verify them before building labs, and keep caches in
ignored `.runtime`. No sibling source checkout or private consumer configuration
may be needed to build this repository. Preserve original component notices.
