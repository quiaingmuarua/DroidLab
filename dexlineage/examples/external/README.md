# External project example

This verification-only project compiles `consumer.example` against `build/install/dexlineage/lib/*.jar`. It has no production `project(...)` dependency, source inclusion or Kotlin friend access. Root Gradle owns the wrapper and versions; this is not a sixth production module.

`ConstructedClass` consumes a factory CLASS anchor and reads its declared construction returns through public `ApkFacts`. `FactoryAnalysis` owns discovery/task drafting. `environment()` explicitly composes both into `DexLineageRuntime`; `main` delegates ordinary commands to `runCli`. The core CLI has no knowledge of these IDs.

```text
./gradlew -Pandroid=false :dexlineage:external-example:test
./gradlew :dexlineage:external-example:fixtureIntegrationTest
```

The first command builds the installed core, compiles the external JAR and tests sequential registry isolation, duplicate IDs and code/resource identity. Changing only ZIP timestamps preserves identity; changing the extension resource changes it. The package deliberately lives outside `dev.dexlineage`.

The fixture command builds the existing self-authored R8 APKs. Child processes use the external entry to prepare direct/ambiguous/unknown/blocked results, inspect, submit authored test Reviews, publish and migrate. It also runs the external AnalysisMethod, verifies Mapping readback, replays publication and rejects a current proof under a changed extension. Expected Product identities are translated from R8 Mapping only in assertions.

Normal entry example after building (`;` replaces `:` in the Java classpath on Windows):

```text
java -cp 'dexlineage/examples/external/build/libs/external-example-0.4.0.jar:build/install/dexlineage/lib/*' consumer.example.ExampleKt --help
```

The two `example-*` commands are identity-test diagnostics for this example, not additions to the core CLI. A real project supplies its own implementations and configuration, while retaining the shared matching, proof, Review and publication rules.
