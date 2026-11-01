# Android labs working guide

`labs/` owns the public Android target applications and their shared native
library. Use the single DroidLab root Gradle build and wrapper; do not add a
nested settings file or wrapper.

## Module choice

- `detect-env`: environment checks, fingerprints, anti-analysis signals and
  privacy collection demonstrations.
- `android-lab`: Android framework, networking, cryptography, IPC and library
  demonstrations, including deliberate hook and tracing targets.
- `crackme-native-lib`: reusable C/C++ capabilities, syscall helpers, ELF loading
  and native detection code. Both applications depend on this module.

Keep app screens and verdicts in the application. Put reusable native behavior
in `crackme-native-lib`, and read the nearest `CLAUDE.md` plus its CMake entry
before changing native code. Preserve package names, JNI names and event names.
Frida tooling and public reproducible cases live in `../hookforge`; private
research and consumer deployment remain outside DroidLab.

## Build inputs and verification

Run from the DroidLab root:

```sh
./gradlew -Pandroid=false prepareDependencies
./gradlew :labs:check :labs:assemble
```

`-Pandroid=false` disables Android projects for JVM/Node work and dependency
preparation. Default builds and Android Studio use the root settings with Android
projects enabled. `:labs:check` runs each module's Debug unit tests;
`:labs:assemble` builds their Debug APKs/AAR. `:labs:clean` cleans module outputs.
The two applications verify their required prepared XA SDK and Duck bytes before
building; verification does not download or repair inputs. Explicit preparation
is owned by `droidlab_tools` and writes only the root `.runtime` caches.

A new experiment should have a runnable entry, observable output and a focused
check. Unit tests and assembly do not prove device behavior: actual collection,
hook effects and Worker execution require separate device acceptance.

## Capability ownership

Apps own checks, verdicts and their full-name `extension.worker.<source>.*`
events and scenario catalogs. XA Mass supplies the independently released
Worker and capability HTTP SDKs. Keep catalog definitions consistent with
actual handlers and consume the locked Maven artifacts; do not include XA
production source in this build.

Read `detect-env/CLAUDE.md`, `android-lab/CLAUDE.md` or
`crackme-native-lib/CLAUDE.md` for the affected module. Hook-side work starts in
`../hookforge/AGENTS.md`.
