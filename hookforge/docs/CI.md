# DroidLab CI and HookForge provenance

HookForge is source in DroidLab's `hookforge/` module. The root Gradle Wrapper
and root `.github/workflows/` own its build and CI. There is no nested Git
repository, submodule upgrade or independent HookForge development entrypoint.
Private target research and consumer deployments remain outside DroidLab.

## Local host gate

Run from the DroidLab root with Node from `hookforge/.node-version` on PATH:

```bash
./gradlew -Pandroid=false :hookforge:check :hookforge:verifyProductionArtifact
npm --prefix hookforge run cli -- list
npm --prefix hookforge run cli -- plan --replace root,system-properties --trace file-io,system-properties
```

The module keeps its npm lockfile and reviewed Java bridge patch.
`:hookforge:npmCi` installs both. `check` runs the existing source, unit, tool,
generated-host and development-build checks. `assemble` builds production.
When both are requested, production runs after the development check so that
`dist/` cannot silently revert to development bytes. The verification task
writes `.runtime/hookforge/production-manifest.json` at the DroidLab root.

The `af-frida` bin and case commands retain their names. The root commands use
`npm --prefix hookforge`; an optional npm link points at this source module.
Composable CLI paths and default run output belong to the caller. Legacy and
case runners retain their documented path rules; use absolute APK/artifact
paths and an explicit root `.runtime/` output when invoking them through npm.

## Source identity and fixtures

Production manifest schema 2 retains `commit`. Runtime case/suite/job schema 3
retains `checkoutCommit` where present. Both identify the owning **DroidLab
commit**, not a historical standalone HookForge commit. Tool paths, lock hashes,
bridge patch identity and artifact file hashes remain module-relative.
Git resolves the repository above `hookforge/`; `hookforge/.git` must not exist.
An optional external `consumerCommit` remains separate consumer evidence.

RootBeer coordinates and hash stay in `cases/rootbeer/catalog.mjs`.
The fixed detect-env APK coordinates, hash and historical build provenance stay
in `test/api33/fixture.json`. Both immutable assets continue to be downloaded
from the original public `quiaingmuarua/HookForge` Releases and verified before
installation. Their old source repository/commit describe the actual historical
APK build and must not be relabeled as a DroidLab build. This integration does
not publish, replace or upload a Release, and never selects a latest APK.

## Device evidence

The public Linux baseline is API33 x86_64. The fixed-fixture suite keeps four
Python/Node × attach/spawn mechanism cases, then RootBeer with detect-env reuse
and recovery using the same production bytes. Its report requires complete
mechanism and shared-API evidence; setup, behavior or cleanup failures fail.

An APK built from current DroidLab `labs/` is a separate input with its own
build/install hash and integration result. A targeted current-lab result cannot
be reported as the complete four-case fixed-fixture suite. See the root workflow
for the selected checks and [Testing Strategy](TESTING_STRATEGY.md) for acceptance.

This migration adds no macOS runner or ARM64 CI coverage. The retained ARM64
tooling can be invoked explicitly in a suitable environment; it does not prove
that such a machine or workflow is available. See [macOS scope](MACOS_RUNNER.md).
