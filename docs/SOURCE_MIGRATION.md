# Source migration

The exact input repositories and commits are recorded in
[source-imports.json](source-imports.json). During that import they were copied
as tracked-source snapshots; source checkouts were not renamed, edited, deleted
or archived by the import.

| Source | DroidLab destination |
| --- | --- |
| DexLineage | `dexlineage/` |
| HookForge | `hookforge/` |
| AgentForge public crackme modules | `labs/` |
| XA SDK and Duck preparation helpers | `droidlab_tools/` |

Nested Git metadata, Wrappers/settings, source workflows, local state and
private deployment proof were excluded. The public App snapshot omits
`detect-env/host` and its private `workerProofArtifact` task. The normal App
behavior, optional Worker configuration, package names and native interfaces
remain. No private research data or historical run conversion is part of this
change.

## Android labs maintenance ownership

`labs/` is the sole maintenance location for the three public Android labs.
Their HTTP capabilities, Duck integration and optional Worker configuration
remain. The paired DroidWorks convergence retires its copied App modules and
sample-specific report/deployment declarations. Private gateway access,
Runtime deployment and private proof orchestration do not move here. The
sample-specific Xposed modules remain in DroidWorks for optional manual use.
DroidWorks' normal build and CI do not fetch or build DroidLab source or APKs.
Existing immutable historical fixtures keep their recorded origins.

The 2026-11-22 audit compared the historical import
`9fc7c0017a0ca9447af99465d168d1c613d554ca`, DroidWorks
`4f4aa8f97fcce41606d7fedffe3afb2bbce528ae`, and DroidLab
`3c3ca80c4379cb5609cf6dc5b50331539fed3f2a`. All 280 public import files are
present: detect-env 106, android-lab 123 and crackme-native-lib 51. Of those,
274 retain the exact Git blob; six differences are documentation and build
adaptations. App code, manifests, resources and native code match the import.
The five private Host proof files were deliberately excluded. The later
DroidWorks gateway implementation and its test were not in the import baseline
and are not copied into the public samples.

`source-imports.json`, existing dependency locks and historical fixture
identities remain unchanged. This is source-completeness evidence; unit/build,
device, hook and Worker outcomes are separate verification claims.

All development now uses the single DroidLab build. DexLineage's five modules
are `:dexlineage:*`, its fixture is `:dexlineage:fixture-apk`, and public Apps
are `:labs:*`. HookForge is a normal `:hookforge` subproject that invokes npm.
There are no internal submodules or composite builds.

The build baseline is consolidated in [Build and CI](BUILD_AND_CI.md). In
particular, the Dex fixture's AGP, protobuf plugin, Build Tools and expected R8
version must move together. Its package/Mapping assertions remain independent
of the matcher. Compiler changes can legitimately change engine bytecode
identity; no old identity is forged or accepted through a compatibility shim.

R8 9.4.24 introduces bottom-up outlining even with the fixture's existing
optimization restrictions. Fixture verification therefore uses an isolated R8
execution profile to retain its documented local construction/throw shapes.
The matcher does not gain support for synthetic throw helpers. Preflight still
requires actual obfuscation and checks anonymous SourceFile identities against
the independently generated Mapping. Ordinary Android compilation is unchanged.

CLI names, tool APIs, published knowledge format and App identities retain
their previous contracts. Current HookForge source provenance now uses the
DroidLab commit. Existing fixed-fixture repository/tag/asset hashes and
historical build provenance remain immutable references.

Licenses and third-party notices stay with their originating components. This
import does not create a new blanket license for third-party or previously
unlicensed files. Publishing releases, retiring source repositories and
switching private consumers are separate work.
