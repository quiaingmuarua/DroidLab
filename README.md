# DroidLab

Android reverse engineering tools, labs, and reproducible experiments in one
source repository and one Gradle build.

| Component | Responsibility | Read next |
| --- | --- | --- |
| `dexlineage/` | Static APK analysis, evidence-backed class knowledge and version migration | [DexLineage](dexlineage/README.md) |
| `hookforge/` | Frida observation, hooks and repeatable public capability cases | [HookForge](hookforge/README.md) |
| `labs/` | Public Android samples and their shared native library | [Labs](labs/CLAUDE.md) |
| `droidlab_tools/` | Preparation and verification of immutable build inputs | [Build and CI](docs/BUILD_AND_CI.md) |

Start with [AGENTS.md](AGENTS.md), then the relevant component guide. Tools keep
their own runtime contracts; public integration belongs here and private
research belongs in its consuming workspace.

## Requirements

- JDK 21 and the committed Gradle Wrapper.
- Node **22.19.0** on `PATH` for HookForge, with npm lifecycle scripts enabled.
- Python 3.11 or newer for dependency preparation and host checks.
- Android work additionally needs the SDK packages, NDK and CMake listed in
  [Build and CI](docs/BUILD_AND_CI.md#android-environment).

There are no internal submodules or sibling-checkout dependencies. Run the
commands below from the **DroidLab root**. On Windows use `gradlew.bat` and
native Windows tools; the installed DexLineage launcher also has a `.bat` form.

## JVM and Node only

```sh
./gradlew -Pandroid=false :dexlineage:build :dexlineage:installDist
build/install/dexlineage/bin/dexlineage --help
./gradlew -Pandroid=false :hookforge:check :hookforge:verifyProductionArtifact
npm --prefix hookforge run cli -- list
```

`-Pandroid=false` excludes Android App/fixture projects. It does not download
Android inputs. An explicitly requested Android verification fails in this
mode rather than reporting a skipped check as a success.

## Prepare and build Android labs

```sh
./gradlew -Pandroid=false prepareDependencies
./gradlew :labs:check :labs:assemble
./gradlew :dexlineage:fixtureIntegrationTest
```

`prepareDependencies` installs locked npm dependencies and resolves the locked
XA Worker SDK and Duck AAR. It does not install or modify the machine's Android
SDK. Set `ANDROID_HOME` or `sdk.dir` in ignored root `local.properties` first.
Then open **DroidLab itself** in Android Studio; there is one settings file and
one Wrapper for all JVM and Android modules.

The Android labs are `:labs:detect-env`, `:labs:android-lab` and
`:labs:crackme-native-lib`. Their application IDs and existing behavior remain
unchanged. Device tests are explicit integration jobs, not part of ordinary
`check`.

## Outputs and maintenance

- DexLineage CLI: root `build/install/dexlineage/`.
- Android APKs: each App's `labs/<module>/build/outputs/`.
- Frida production files: `hookforge/dist/`; the Gradle manifest is
  `.runtime/hookforge/production-manifest.json`.
- Build inputs and CI evidence: root `.runtime/`; generated module output and
  local inputs are ignored by Git.

Root `check`, `assemble`, `build` and `clean` aggregate module tasks. Use
component-qualified tasks for focused work. See [Build and CI](docs/BUILD_AND_CI.md)
for the exact gates and [Source migration](docs/SOURCE_MIGRATION.md) for import
commits, exclusions and build changes.

Original component licenses and third-party notices remain with their code;
this merge does not relicense them. See [DexLineage notices](dexlineage/THIRD_PARTY_NOTICES.md)
and the notices accompanying Android SDK dependencies.
