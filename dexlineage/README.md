# DexLineage

DexLineage is an APK analysis tool used through its CLI or public JVM library. Version 0.4 provides two scenes:

- **Analysis:** import manual annotations or discover clues, combine built-in matching with compiled application strategies, resolve supporting symbols within one execution, then review and publish class names.
- **Migration:** freeze existing semantic targets, resolve their rules and dependencies in another APK, review continuity, and publish new bindings with unresolved history retained.

Every new published binding requires a unique, definite result from actual code matching. Multiple candidates are invalid. An Agent can revise the rule and start another run, but cannot choose a candidate through Review text or a HIGH confidence declaration.

Supporting CLASS or METHOD anchors are mechanical matching results, not Rename entries. A UNIQUE anchor is available to dependent rules in the same execution and may be published without a semantic Review. Final class names require review of the owner and the supporting evidence chain. All output Mapping rows remain class-only.

## Build from a clean checkout

Build from the DroidLab repository root with its single Gradle Wrapper and JDK 21. The root pins Kotlin 2.4.21 and Gradle 9.7; JADX 1.5.0, Gson 2.10.1 and mapping-io 0.6.1 remain unchanged. Module lockfiles record resolved dependencies. Initial dependency downloads require network access. All commands below run from the DroidLab root.

Linux/macOS:

```sh
git clone https://github.com/quiaingmuarua/DroidLab.git
cd DroidLab
./gradlew -Pandroid=false :dexlineage:build :dexlineage:installDist
build/install/dexlineage/bin/dexlineage --version
build/install/dexlineage/bin/dexlineage --help
```

Windows PowerShell, from the checkout:

```powershell
.\gradlew.bat -Pandroid=false :dexlineage:build :dexlineage:installDist
$cli = '.\build\install\dexlineage\bin\dexlineage.bat'
& $cli --version
& $cli --help
```

The launcher lives at root `build/install/dexlineage` and retains a 3 GiB maximum heap. The `:dexlineage:test`, `:dexlineage:check`, `:dexlineage:build`, `:dexlineage:installDist` tasks aggregate this component's modules. Focused JVM tests use `./gradlew -Pandroid=false :dexlineage:analysis:test` or `:dexlineage:jadx:test`.

Use `-Pandroid=false` with the DexLineage JVM tasks to avoid configuring Android modules; those tasks do not require an Android SDK or any APKs. Keep private profiles, rules, runs, reviews and reports in a private workspace outside DroidLab. The examples use `../private-research/dexlineage/` as an illustrative external location; replace it with your actual directory. APKs can stay at their existing paths. Temporary public probes may use ignored root `.runtime/`. No installed JADX GUI, exported Gradle project or model API is required.

### Android Studio

Open the DroidLab repository root, use its Gradle Wrapper and JDK 21, then sync. The root settings import the five production JVM modules, a verification-only external consumer, and the test-only `:dexlineage:fixture-apk` Android application module (`dexlineage/fixtures/android`). Its standard source sets are `src/main`, `src/v1` and `src/v2`; select `v1Release` or `v2Release` in Build Variants. There is no nested Android Gradle project to import separately.

Android modules are enabled by default. Use `-Pandroid=false` for JVM/Node-only work and `-Pandroid=true` to enable Android explicitly. Fixture tasks fail clearly if Android is disabled. Configure the Android SDK in DroidLab root `local.properties` (`sdk.dir=...`) or `ANDROID_HOME`. There is no nested settings file or Wrapper.

## Public APK integration regression

The public fixture suite builds readable, self-authored Java cases into two real APK versions using R8 name obfuscation. It then drives the installed production CLI through Analysis, review submission, publication, Migration, same-APK continuation and independent JADX Mapping readback. Published knowledge also drives a separate raw DEX lookup, whose returned class/member identities are checked against the independent R8 oracle. R8 shrinking and optimization are disabled; compiled version perturbations check that the existing rules survive changes to unrelated members, assignment order and supported construction shapes.

Install Android SDK command-line tools, configure `ANDROID_HOME`, then install the pinned components and accept their licenses:

```sh
export ANDROID_HOME=/path/to/android-sdk
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'platforms;android-35' 'build-tools;36.0.0'
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
./gradlew :dexlineage:fixtureIntegrationTest
```

On PowerShell, set `$env:ANDROID_HOME` to your SDK directory, use `sdkmanager.bat` with the same package names, and run `.\gradlew.bat :dexlineage:fixtureIntegrationTest`. Alternatively set `sdk.dir=/path/to/android-sdk` in ignored root `local.properties` (use forward slashes on Windows). Missing SDK components fail the integration task; the suite is never silently skipped.

The test-only `:dexlineage:fixture-apk` module lives under `dexlineage/fixtures/android/`: root-pinned AGP 9.4.1 and its embedded R8 9.4.24, compile/target SDK 35, build tools 36.0.0, min SDK 26 and Java source/target 17. The expected R8 identity remains an explicit fixture preflight input in `dexlineage/fixtures/cases.json`. The Android artifact provider exports exactly one APK per Release variant into `dexlineage/fixtures/android/build/fixture-inputs/`, including when IDE ABI properties change AGP's internal output location. It uses the root Gradle Wrapper and JDK 21. No device, emulator, private APK, signing key, model service or repository secret is needed.

The fixture task automatically uses an isolated R8 worker profile that disables bottom-up outlining for this static specimen. This preserves the direct diagnostic-guard shape covered by the existing matcher, with real class/member renaming still enabled. It does not add support for R8 synthetic throw helpers or claim production Release optimization coverage. See [fixture toolchain boundaries](docs/fixture-testing.md).

R8 Mapping is a test-only oracle for independent expected identities. The matcher receives APKs and rule data; it never receives that Mapping or expected candidate identities. Test Reviews contain predefined fixture semantics and real run/evidence references. They test protocol and publication behavior, **not Agent semantic judgment**.

The verification chain is **R8 APK → coding matching → published knowledge JSON → offline DEX verification → R8 oracle assertions**. The offline consumer takes only the APK and saved knowledge, resolves exact class/method addresses, and returns raw member facts. It does not execute DEX, infer missing bindings from historical entries, or use the oracle to find an entity. These checks establish static identity and rule-reuse regressions, not behavioral equivalence.

CI runs JVM checks on Ubuntu 24.04 and Windows, and the APK suite on Ubuntu 24.04. Public reports, command logs and generated fixture APK/Mapping/configuration are retained as CI artifacts for 14 days. See [fixture maintenance and failure diagnosis](docs/fixture-testing.md). Passing these cases does not establish coverage of all APKs or all obfuscators.

## Run an analysis method once

`analysis run` combines the method's prerequisite matching, discovery, target matching and review-packet output in one offline execution. Direct rules need no prerequisite setup. Declared CLASS/METHOD symbols are evaluated in dependency order and reused; no intermediate knowledge publication or stage workspace is created.

Copy [the profile configuration](examples/profile-workspace.json) and [logger profile](examples/logger-profile.json) into your project. The historical filename is an input configuration, not a recoverable workspace. Paths resolve from that configuration file.

```powershell
& $cli analysis run --method profile --config ../private-research/profile-workspace.json --work ../private-research/analysis-001
```

The invocation writes one final run, discovery results and a Review packet. Matching does not publish names. Read evidence and fill semantic fields, then explicitly submit and publish. Both scenes support `inspect --out <new-directory>`; omit `--task` with `--out` for all tasks. Interrupted work restarts in a new directory; retired status/resume commands return guidance.

Project-owned methods use the same commands through the project's composed entrypoint, as demonstrated by the [external example](examples/external/README.md).

## Analyze an APK

Copy [the logger profile](examples/logger-profile.json) into `../private-research/dexlineage/` and set its business tag. It discovers field-initializer clues and can generate per-target rule drafts. No owner list or expected target belongs in the profile.

```powershell
& $cli analysis discover --apk 'D:\apks\current.apk' --profile ..\private-research\dexlineage\logger-profile.json --out ..\private-research\dexlineage\discovery-001
& $cli analysis prepare --apk 'D:\apks\current.apk' --rules ..\private-research\dexlineage\discovery-001\rules.json --run ..\private-research\dexlineage\analysis-001
& $cli analysis tasks --run ..\private-research\dexlineage\analysis-001 --state UNREVIEWED --offset 0 --limit 20
& $cli analysis inspect --run ..\private-research\dexlineage\analysis-001 --task 'task-key-1,task-key-2'
# The Agent reads the evidence and writes the appropriate review batch.
& $cli analysis submit --run ..\private-research\dexlineage\analysis-001 --review ..\private-research\dexlineage\analysis-review.json
& $cli analysis publish --run ..\private-research\dexlineage\analysis-001 --out ..\private-research\dexlineage\knowledge-v1
& $cli knowledge verify --knowledge ..\private-research\dexlineage\knowledge-v1\knowledge.json --apk 'D:\apks\current.apk' --work .runtime\verify-v1
```

Discovery can return many owners: this is normal scope exploration. Its clues and rule drafts cannot publish names. `prepare` executes each target's rules over the entire APK, including owners outside the discovery scope, and freezes a Resolution. A discovery-linked unique result must also agree with that lead's original owner.

Discovery is optional. An Agent can write an [Analysis rules file](examples/analysis-rules.json) from an observed feature and pass it directly to `analysis prepare`. An item contains a stable task key, optional existing `semanticId`, optional name suggestion and locator list; it cannot supply an expected obfuscated target. A corrected name does not rewrite captured strings or locators.

Use `tasks --state INVALID` and `inspect` to investigate invalid results. Their candidates and diagnostics remain available; they do not require an artificial Review. Even a unique result needs actual semantic review before acceptance. Field-initializer naming requires owner and internal factory code; plain string-use rules require owner code and their actual matching evidence.

## Compose an external project

The installed CLI contains built-in locators and method-signature matching only. Project implementations are not registered inside the tool. A project main constructs `DexLineageRuntime(handlers, methods, codecs)` and calls the same `runCli(args, runtime)`. Analysis, Migration, Review and publication share that instance; no mutable global registration or dynamic JAR loading is required.

The [external consumer example](examples/external/README.md) compiles only against installed JARs. It exercises a dependency-based handler and an AnalysisMethod, including publication and migration, without Kotlin internal or friend access. Extend from those public contracts, not by editing the tool registry. Unknown extension IDs fail explicitly when only the core CLI is used.

App-specific handlers, catalogue code, parameter conventions and codecs belong to the consuming project. Existing Messages implementations now live in its private research project. Its project entry supplies the original strategy IDs; the core does not keep a second implementation as fallback.

## Import manual work and resolve symbol dependencies

Import a JADX `projectVersion=1` annotation project into a new corpus directory:

```powershell
& $cli analysis import-jadx --apk 'D:\apks\current.apk' --project 'D:\apks\current.apk.jadx' --out ..\private-research\dexlineage\manual-corpus
& $cli analysis targets --corpus ..\private-research\dexlineage\manual-corpus\corpus.json --limit 20
```

The corpus preserves class, field, method and local annotations. Class annotations become stable targets; members and local positions remain research context. Import validates original class/member references. A class target's source identity is checked after matching; it is never passed to the matcher as an expected-owner filter. Importing an annotation does not approve a name for publication.

An Analysis rule item can refer to a corpus target using `corpusTargetId`. Pass `--corpus` to `analysis prepare`; `analysis targets --corpus ... --run ...` shows every manual target, including `UNCONFIGURED` items and failures. New discoveries outside the corpus are reported separately.

Investigate selected manual targets before writing rules:

```powershell
& $cli analysis research --corpus ..\private-research\dexlineage\manual-corpus\corpus.json --target 'manual-target-id-1,manual-target-id-2' --out ..\private-research\dexlineage\research-001
```

Research reads the corpus's frozen source APK and preserves owner annotations alongside raw class/member identities, strings, direct calls, field accesses and Java/Smali evidence. It batches selected targets, does not recursively explore relationships, and writes `research.json` without creating a matching run. Research can help author a rule; it is not a Resolution, and its evidence cannot be submitted as another run's Review. A partial Java rendering remains explicit while raw DEX reading failures abort the operation.

Dependent rules declare `requires` roles referring to CLASS or METHOD anchors. One prepare evaluates available rules in dependency order, reuses shared symbols and records blocked dependants. A project handler consumes the public facts view and returns candidates, conditions and evidence. It cannot approve names or publish Mapping. The [external example](examples/external/README.md) includes this flow.

## Continue Analysis on existing knowledge

Pass a knowledge package from the same APK with `--base-knowledge`:

```powershell
& $cli analysis prepare --apk 'D:\apks\current.apk' --rules ..\private-research\dexlineage\additional-rules.json --base-knowledge ..\private-research\dexlineage\knowledge-v1\knowledge.json --run ..\private-research\dexlineage\analysis-002
# Inspect and review the new or changed tasks, then publish a complete revision.
& $cli analysis publish --run ..\private-research\dexlineage\analysis-002 --out ..\private-research\dexlineage\knowledge-v1-revision-2
```

The base knowledge ID is frozen. Existing current-version entities retain their `semanticId`; names are not identity keys. Successful updates and new confirmations are combined with unchanged base entries. A failed rule attempt, rejected update or conflicting proposal does not erase the prior valid binding, name or rule. This version does not move an existing semantic ID to another entity in the same APK.

Unchanged confirmations and supporting proofs can be inherited when their rules, implementation and dependency proofs are unchanged. An inherited class does not need another Review. Updating an anchor invalidates reuse of downstream proofs that refer to its previous revision; old published packages are preserved.

An explicitly selected pending item whose last binding belongs to the current APK can be retried. Historical pending entries from other APKs are carried forward; Analysis does not automatically investigate or claim them by matching a name.

## Migrate knowledge

Pass the published knowledge file and accompanying evidence directly to Migration:

```powershell
& $cli migration prepare --knowledge ..\private-research\dexlineage\knowledge-v1\knowledge.json --source-apk 'D:\apks\old.apk' --target-apk 'D:\apks\new.apk' --run ..\private-research\dexlineage\migration-001
& $cli migration tasks --run ..\private-research\dexlineage\migration-001 --state UNREVIEWED --offset 0 --limit 20
& $cli migration inspect --run ..\private-research\dexlineage\migration-001 --task 'semantic-id-1,semantic-id-2'
& $cli migration submit --run ..\private-research\dexlineage\migration-001 --review ..\private-research\dexlineage\migration-review.json
& $cli migration publish --run ..\private-research\dexlineage\migration-001 --out ..\private-research\dexlineage\knowledge-v2
& $cli knowledge verify --knowledge ..\private-research\dexlineage\knowledge-v2\knowledge.json --apk 'D:\apks\new.apk' --work .runtime\verify-v2
```

Migration only creates tasks for confirmed semantic targets in the source knowledge. It preserves their names and keeps unresolved knowledge with its last confirmed source. The default confidence threshold is HIGH; `prepare --min-confidence MEDIUM` freezes another threshold without relaxing uniqueness or identity checks.

Source anchors supply rule definitions, not target bindings. One Migration prepare resolves the prerequisites again in the target APK and evaluates the dependent targets. `--target-base` is optional continuation of already confirmed target results from the same source knowledge and target APK, not a dependency-stage input. New source revisions cannot borrow an earlier migration's target base.

Use `migration inspect --run ... --out ...` to obtain source/target evidence and a blank semantic-review template. Omitting `--task` includes all tasks; `--candidate` cannot be combined with packet output. Input, rule or implementation changes require fresh matching and current Review identities.

Supply an [override file](examples/migration-overrides.json) with `migration prepare --rules ...` and a new run directory to revise rules. Each override replaces that target's entire active locator set; unlisted targets keep their rules. Unknown semantic IDs are rejected. A target-version rule need not match the source APK. Accepted migration publishes the rule revision actually used for the new binding.

Multiple candidates cannot be resolved by Review. `migration inspect --candidate 'semantic-id=Lx/a;,Lx/b;'` remains available for reading selected candidates; it grants no publication eligibility.

## Reviews, revisions and packages

Use `tasks` to obtain task, Resolution and rule revision IDs. Both Review formats bind `resolutionId` and `ruleRevisionId`; they do not choose an entity. Analysis may propose a name. Migration judges whether the existing name and responsibility continue. See [the exact formats](docs/architecture.md#reviews).

Class review states are `UNREVIEWED`, `ACCEPTED`, `REJECTED`, `DEFERRED` and `INVALID`. Execution separately reports `EXECUTED`, `WAITING_DEPENDENCY` or `INHERITED`; task lists also contain supporting anchor tasks. Match results identify `UNIQUE`, `NO_MATCH`, `AMBIGUOUS` or `UNKNOWN`. Scan failure is an execution error. Listing creates no claims or leases; one coordinator submits sequentially.

Review batches save atomically. Identical resubmission is idempotent; replacing a Review requires its current revision in `supersedes`. Modified rules create a new execution, and old Review identities cannot pretend to describe its new Resolution.

Every publication uses a new directory containing:

- `knowledge.json`: schema 4, class bindings, supporting anchors, active rules, dependency proofs, parent knowledge reference, review provenance and unresolved history.
- `mapping.tiny`: class-only Tiny v2 `obfuscated` → `named` mappings.
- `report.json`: task outcomes, active rules, diagnostics and published review revisions.
- `evidence/` and `dependency-evidence/`: required review and symbol-proof Java/Smali with content hashes.

Keep the package together. Matching proofs include their frozen dependencies; all referenced code is copied into the package, including historical proof evidence. Reproduce a same-run publication after later reviews:

```powershell
& $cli analysis publish --run ..\private-research\dexlineage\analysis-001 --at ..\private-research\dexlineage\knowledge-v1\report.json --out ..\private-research\dexlineage\knowledge-v1-replay
& $cli migration publish --run ..\private-research\dexlineage\migration-001 --at ..\private-research\dexlineage\knowledge-v2\report.json --out ..\private-research\dexlineage\knowledge-v2-replay
```

This replays saved decisions. Re-executing matching from APKs and fixed rules is a separate check; publication replay does not establish that matching has been rerun.

## Upgrade historical knowledge explicitly

```powershell
& $cli knowledge import-v03 --knowledge ..\private-research\dexlineage\v03-package\knowledge.json --apk 'D:\apks\current.apk' --out ..\private-research\dexlineage\v04-imported
& $cli knowledge import-v02 --knowledge ..\private-research\dexlineage\v02-package\knowledge.json --apk 'D:\apks\current.apk' --out ..\private-research\dexlineage\v02-upgraded
& $cli knowledge import-s1 --output ..\private-research\dexlineage\s1-output --run ..\private-research\dexlineage\s1-run --out ..\private-research\dexlineage\s1-imported
```

The importers validate the old knowledge package, report, Mapping and code evidence, then rerun rules against the supplied APK. Only a unique result that remains the original binding can retain the validated historical semantic review. Ambiguous, unknown, missing or differently bound results become pending with their historical source preserved. Broken packages or missing required proof are input errors. S1 conversion retains its original consistency checks and applies the same uniqueness gate.

A v0.3 package may inherit an entry whose original Review lives in an earlier publication. Supply those packages explicitly with `--history <manifest.json>`; the importer does not guess directories or search private history. The manifest is `{"schemaVersion":4,"knowledgeFiles":["relative/path/to/knowledge.json"]}`, with paths relative to the manifest. The importer validates the cited package chain and preserves its provenance. Old files are never rewritten.

Old v0.2 Migration reports did not record their configurable confidence threshold. Their confirmed reviews therefore must be HIGH to prove they satisfied every supported old threshold; a weaker review lacks the required policy proof and is an input error. Old single-version reports retained the threshold in their profile and are checked against that value.

Evidence completeness means verifiable files, identities, hashes and review references. It does not require every Java method to decompile: acknowledged limitations remain visible. Historical packages are never changed in place. Old `bootstrap` and unprefixed S1 commands return guidance; [case.json](examples/case.json) is only a legacy reference.

## Verification and limits

Ordinary JVM checks do not need an APK or Android SDK. Public fixture checks build self-authored R8 APKs, then drive the installed core or external-project CLI in child processes. Matching candidates are checked against independent R8 Mapping before predefined fixture Reviews are submitted. The external consumer is compiled from distribution JARs with no production source-project dependency.

Project-specific scenario tests and real-case acceptance belong to the project providing the extensions. Current Messages regression is maintained there; it is not a core CLI preset. Historical rule IDs and schema-4 documents remain data, while executing their strategy or interpreting scenario-specific historical wire fields requires the corresponding project extension. Published class Mapping consumption remains available through the core.

Unique matching, semantic review and runtime behavior are separate claims. Unknown code shapes remain visible; no dynamic plugin/script loader, member Rename, GUI, MCP or model API is provided. See [architecture](docs/architecture.md) and [fixture testing](docs/fixture-testing.md).

## License

DexLineage and its public fixture source use [Apache-2.0](LICENSE). The CLI distribution includes [third-party notices](THIRD_PARTY_NOTICES.md) and the corresponding license texts. Version 0.4.0 is distributed as source; release tags and GitHub Releases are separate actions.
