# Source migration

The exact input repositories and commits are recorded in
[source-imports.json](source-imports.json). They were imported as tracked-source
snapshots; source checkouts were not renamed, edited, deleted or archived.

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
