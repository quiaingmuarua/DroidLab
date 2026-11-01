# Build and CI

Run all entrypoints from the DroidLab root. The root settings, Wrapper and
version catalog are the only Gradle build definition; component build scripts
are ordinary subprojects, not independent builds.

## Toolchain

| Tool | Pin |
| --- | --- |
| Gradle | 9.7.0, distribution SHA256 in the Wrapper properties |
| Gradle JVM / Dex JVM | JDK 21 |
| Android Gradle plugin | 9.4.1 |
| Kotlin JVM | 2.4.21 |
| Protobuf Gradle plugin | 0.10.0 |
| Android Build Tools | 36.0.0 |
| NDK / CMake | 27.0.12077973 / 3.22.1 |
| HookForge Node | 22.19.0 (`hookforge/.node-version`) |
| CI Python | 3.11 |

Android modules retain their Java target and compile SDK. AGP supplies Android
Kotlin; the JVM modules use the Kotlin JVM plugin. The pure Java Dex fixture
disables Android Kotlin and retains its independent R8 oracle. Do not change
the expected oracle version without checking the actual AGP/R8 implementation.
Fixture APK inputs are exported through the Android variant artifact provider
into the fixture module's `build/fixture-inputs/` directory, independent of
IDE-injected ABI settings and AGP's internal APK output layout.

The explicit `fixtureIntegrationTest` and `buildFixtureApks` entrypoints select
an AGP R8 execution profile in a separate worker process. For pinned R8 9.4.24,
it disables bottom-up outlining so the self-authored fixtures retain the finite
instruction shapes they are intended to test, while real name obfuscation
remains enabled. Normal builds keep the normal profile. The verification profile
rejects other modules' R8 tasks in the same graph, and its identity participates
in fixture task inputs so switching profiles cannot reuse incompatible APKs.
There are no daemon-wide R8 flags, debuggable/minification shortcuts or relaxed
production matching rules. See [fixture verification](../dexlineage/docs/fixture-testing.md)
before changing this compiler profile.

## Android environment

Install the following packages with Android's SDK Manager, or select the same
packages in Android Studio:

```sh
sdkmanager 'platform-tools' \
  'platforms;android-33' 'platforms;android-34' \
  'platforms;android-35' 'platforms;android-37.0' \
  'build-tools;36.0.0' 'ndk;27.0.12077973' 'cmake;3.22.1'
```

`ANDROID_HOME` or root `local.properties` selects the SDK. Only device CI needs
the API33 x86_64 emulator image. The root `-Pandroid=false` mode does not
configure Android modules and is suitable for JVM/Node-only machines.

## Locked build inputs

```sh
./gradlew -Pandroid=false prepareDependencies
# The underlying public Python entrypoints are also available:
python3 -m droidlab_tools release resolve --component worker-sdk --lock XA_MASS_RELEASE.lock.json
python3 -m droidlab_tools duck-sdk resolve
python3 -m droidlab_tools verify
```

Use `-PpythonExecutable=/path/to/python` when the default `python3` (Unix) or
`python` (Windows) is not the intended interpreter. No PyPI package installation
is needed for these standard-library build-input tools.

XA and Duck downloads are explicit preparation actions. Ordinary lab builds
perform read-only verification before compiling. A missing, mismatched or
tampered input fails with a preparation command; there is no source-checkout,
Maven-local or unverified fallback. XA artifacts resolve exclusively from the
locked SDK's local Maven repository in root `.runtime/`.

## Task ownership

| Task | Verification or artifact |
| --- | --- |
| `:dexlineage:check` | Five JVM modules' normal tests/checks |
| `:dexlineage:installDist` | Root `build/install/dexlineage` CLI and notices |
| `:dexlineage:fixtureIntegrationTest` | Two public R8 APK versions, matching, knowledge, Mapping and independent DEX assertions |
| `:hookforge:check` | Locked npm install, types, lint, unit/tool tests, generated host consistency |
| `:hookforge:assemble` | Production bundle and hosts |
| `:hookforge:verifyProductionArtifact` | Production build, hash manifest write and verification |
| `:labs:check` | Existing App/native local unit tests |
| `:labs:assemble` | Debug Apps and native library |
| `:labs:detect-env:exportDebugApk` | Export the current Debug APK through AGP artifact metadata for CI |
| `toolsCheck` | Public dependency-preparation tests |
| `docsCheck` | Local documentation links and single-build boundaries |

Root `build` runs `check` and `assemble` for enabled components. In
`-Pandroid=false` mode this is explicitly a host-only build. Requesting
`:labs:check`, `:labs:assemble` or the Dex fixture in this mode is an error.
`clean` removes generated build/distribution output, not downloaded inputs.

HookForge's existing `test:ci` builds development output. Gradle orders that
check before the production build when both are requested; manifest generation
and verification follow the final production bytes. All npm processes use
`hookforge/` as their working directory. Do not run a separate development
build concurrently against the same `dist` directory.

## CI gates

The root workflow selects affected jobs from changed paths. Documentation-only
changes run documentation and preparation-tool checks, not emulators. Shared
build changes run every affected lane.

- Dex JVM: Ubuntu and Windows, CLI launch and notice packaging.
- Dex fixture: Ubuntu, production CLI against self-authored R8 fixtures; no
  device or private APK is needed.
- Hook host: Ubuntu, existing host checks and one production artifact.
- Labs: Ubuntu, locked SDK/AAR preparation, unit tests and Debug builds.
- Frida API33: Ubuntu x86_64, fixed fixture and current labs APK as separate
  evidence sets. Each uses Python/Node x attach/spawn plus RootBeer reuse and
  restoration. Both consume the same production artifact.

The current APK is checked against its build commit and SHA256. Production
APK export uses the Android variant artifact provider, so IDE-injected ABI
builds do not rely on an internal AGP output directory. Production
manifest `commit` and runtime `checkoutCommit` refer to DroidLab. Historical
fixed-fixture source commits remain historical; they are not rewritten to
pretend that those APKs were built from this checkout.

Reports and cleanup execute after failures as well. Missing prerequisites,
unavailable fixtures and initialization failures fail the lane. Passing host
checks is not device proof. This public workflow does not claim macOS ARM64
coverage and does not use a private self-hosted runner.
