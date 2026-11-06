# Architecture and contracts

## Owners

| Module | Owns | Project dependencies |
|---|---|---|
| `analysis` | Manual corpus, discovery, current-APK targets, symbol dependencies, naming review and publication | `knowledge`, `jadx` |
| `migration` | Fixed-target continuity, rule overrides, unresolved carry-forward and publication | `knowledge`, `jadx` |
| `knowledge` | Version data, original CLASS/METHOD identities, rules, anchors, dependency proofs, evidence and review journal | None |
| `jadx` | Raw DEX facts, explicit strategy registry, public facts, code rendering, Tiny and Mapping readback | `knowledge` |
| `cli` | Scene commands, distribution and explicit integration checks | `analysis`, `migration`, `jadx` |

The scenes do not call each other. `VersionKnowledge` is their handoff. Each scene owns its tasks, run fingerprint, review meaning and publication decisions. Analysis methods run prerequisite discovery and target matching once. There is no Agent loop, stage recovery coordinator, graph scheduler or second publication service.

## Core, scenarios and projects

`MatchingHandler` and `AnalysisMethod` are public compiled extension contracts. A project supplies ordinary implementations to `DexLineageRuntime`; its main invokes `runCli(args, runtime)`. Core CLI construction supplies built-ins only. Registries are immutable instances shared by Analysis, Migration, inspection and publication. Duplicate IDs fail and separate runtime instances do not share registrations.

Core owns full-domain matching, symbol dependencies, unique/ambiguous/unknown decisions, evidence, Review and publication. A scenario owns the method-specific interpretation, drafts and typed artefacts. A project chooses those implementations and owns app adaptation, parameters, samples and acceptance. Naming a class a scenario does not make its implementation a core capability.

Handlers receive `ApkFacts`: original declarations and references, hierarchy, local values, bounded call/return slices, control shape and exception regions. JADX input objects and the raw loaders remain internal. The interface exposes existing analysis capabilities, not a wrapper for each backend getter. Domain interpretation and local conventions stay in the project.

Project codecs are explicitly supplied with the runtime. They interpret old scenario wire fields while preserving their original layout and fingerprints. No model annotation or default registry names a concrete project. The knowledge protocol retains legacy optional wire fields only for historical hash preservation; generic matching does not interpret their domain meanings.

## Single analysis execution

`analysis run --method ... --config ... --work ...` interprets the method configuration, resolves prerequisite symbols, discovers targets, matches them and emits one run plus a review packet. `prepare` directly executes supplied target rules and their dependencies. Both use the same engine semantics. Review and publication remain explicit operations.

The execution context owns a current-APK fact session and a memoized result per effective rule and dependency binding. Only UNIQUE symbols become inputs to dependants. Failed prerequisites block their dependants without preventing unrelated matching. Configuration cycles and missing definitions fail before execution.

Dependency evidence is identified from APK/rule/implementation and code content without the final runId. A complete SupportAnchor is constructed before downstream Resolution IDs; publication uses that same canonical anchor object. Review evidence remains bound to the final run. New runs use `dependencyEvaluation: "in-run"`; absent markers retain the legacy frozen-base validation semantics and unchanged fingerprints. Knowledge remains schema 4; optional additions are omitted from historical records.

Interrupted work is rerun in a new directory. `analysis status/resume` only provide migration guidance; workspace locks, receipts and stage adoption are retired. Existing Review journal and publish-at behavior remain unchanged.

## Matching and rules

Built-in matching follows exact string anchor → concrete field/call binding → configured factors → captures and evidence. Compiled strategies can additionally consume resolved dependencies and read type, call and bounded assignment relationships. Raw scanning reads method `const-string` and `const-string/jumbo` uses from root `classes*.dex`; it records original class/method identities, DEX entries and instruction offsets. APK resources and full Java export are not required. Displayed Java is review material; formal relationships come from structured facts.

The two locator kinds are:

- `string-use`: exact method-string use, with no field factors or explicit captures. It records the matched `literal` capture.
- `field-initializer`: original owner/field, flags, initializer method, call signature/arguments and result assignment relationship.

For a field locator, the anchor must occur in that initialization call. Strings in other methods or fields cannot supply missing conditions. Raw use sites saved alongside structural matches document anchor locations; the bound call establishes the field relation. Each factor returns PASS, FAIL or UNKNOWN. Any FAIL fails that relation; otherwise any UNKNOWN keeps it unknown.

| Factor `kind` | Parameters |
|---|---|
| `field-flags` | Required flags, such as `STATIC` and `FINAL`; optional `PUBLIC`, `PRIVATE`, `PROTECTED` or `PACKAGE` visibility |
| `invocation-kind` | `STATIC`, `VIRTUAL`, `INTERFACE`, `DIRECT` or `SUPER` |
| `method-signature` | Optional exact `parameterTypes`, `returnType`, and/or `returnSameAsField`; types use DEX descriptors |
| `argument-string` | Zero-based `index`, requiring a resolved string constant; optional exact `value` |
| `assignment-owner` | No parameters; the result initializes the bound field of this owner |

Unsupported kinds and parameter combinations are configuration errors. Unreadable initialization remains UNKNOWN; it does not fall back to accepting a class-level string coincidence. Known incompatible or non-string values fail. Partial methods elsewhere in a class do not automatically invalidate readable field facts.

A `RuleRevision` stores a strategy ID, parameters, CLASS/METHOD result kind, dependency roles and its content-derived `revisionId`. Existing locator lists are represented by `builtin.locators`; coded strategies use their own parameters and cannot simultaneously supply locators. `ResolutionEngine` validates and batches the ready rules. There is no expected-owner input. Its `engineBuildId` hashes actual project class contents from the knowledge/JADX artifacts, including compiled recipe code, while ignoring absolute locations and ZIP timestamps.

Alternative locators are OR alternatives for one semantic target: all execute, their evidence remains separate, and candidates merge by original class identity. There is no first-hit preference, confidence voting or review-dependent candidate removal.

| Candidate result | Resolution | Publication eligibility |
|---|---|---|
| One entity has PASS, no other possible entity exists and no relevant unresolved path remains | `UNIQUE` | Class target may enter semantic review; support anchor may be published |
| Two or more entities have PASS | `AMBIGUOUS` | Invalid |
| Fewer than two PASS entities, with a different UNKNOWN entity or a relevant unresolved path | `UNKNOWN` | Invalid |
| No non-failing entity and no relevant unresolved path | `NO_MATCH` | Invalid |
| Scan/execution incomplete | Execution error | No successful matching result |

Several fields/locators supporting one class still identify one entity. A PASS and UNKNOWN relation for the same entity can remain UNIQUE when no relevant unresolved path remains; its unknown diagnostics are retained. Renaming one task, rejecting another, reviewing only part of the queue or changing submission order cannot change these results.

METHOD results use the full owner plus original method name and descriptor. Two methods in the same class remain two entities. `Resolution.uniqueRef` only returns CLASS results; `uniqueEntity` covers both supported result kinds. An anchor has no Rename alias and cannot become a Mapping row merely because it is unique.

## Compiled strategies and dependencies

The core registers `builtin.locators` and `builtin.method-signature`. Project handlers declare an opaque ID, result kind, dependency roles, parameter/producer validation, scan needs and evidence requirements. `requires` resolves current-APK CLASS/METHOD symbols in one execution; failed prerequisites leave dependants blocked. A handler returns matching facts and typed scene artifacts; shared policy alone builds Resolution and controls publication.

Public `ApkFacts` sessions share the existing full APK inventory and demand-driven indices. Only their owner closes a session. Externally compiled code can consume the same bounded analysis without copying a parser or reaching into Kotlin internals.

The engine implementation identity covers core artifacts, registered handler/method/codec identities and actual contributed code/resources, including helpers in the same artifact. Absolute paths and ZIP timestamps do not define identity. Extension changes require fresh matching and current Review; version strings are not substitutes for code hashes.

## Manual annotation corpus

`analysis import-jadx` accepts the observed JADX `projectVersion=1` rename format. It preserves every original annotation and verifies class, field and method references against the supplied APK. Local variable positions are retained as background; they are not claimed to be cross-version identities.

`ManualCorpus` freezes the APK/project digests and creates class targets with stable IDs. An Analysis item can link `corpusTargetId`; its unique result must equal the imported source identity after unrestricted matching. Import does not establish confirmed knowledge. Source aliases remain distinct from captures and require explicit Analysis review when published or changed.

`analysis targets --corpus ... [--run ...]` reports the whole corpus, including UNCONFIGURED targets. The run keeps the complete corpus reference and reports its target ledger alongside configured tasks; newly discovered tasks outside that corpus are listed separately. Application-specific annotation counts belong in private acceptance data, not production logic.

`analysis research --corpus ... --target ... --out ...` provides a pre-rule investigation surface for selected original corpus entities. It freezes corpus/APK/implementation identity and publishes `research.json` with owner annotations, raw member and direct-reference facts, and presentation evidence. Target selection never enters a matcher. Research has no AnalysisRun, Resolution, Review or naming authority; its evidence namespace differs from later executions and cannot satisfy their review identity checks. Raw input errors abort, while partial Java/Smali limitations remain recorded. Direct references are observations, not a recursive call graph or inferred data flow.

## Analysis discovery and rules files

`analysis discover` is optional exploration. It produces `discovery.json` and evidence; when a profile template can instantiate passing leads, it also writes `rules.json`. Discovery may find many owners without being ambiguous as a whole. It cannot publish.

A discovery profile has `schemaVersion: 4`, `id` and either `discovery` (an existing locator) or `strategy`. Optional `nameCapture` selects a name suggestion. `locatorTemplate` drafts existing locators; alternatively `strategyTemplate` with `parameterCaptures` copies named captures into strategy parameters. They are fixed data substitutions, not a scripting language. `minConfidence` defaults to HIGH. Confirmed aliases are never template inputs. Strategy discovery can use `--base-knowledge` for previously resolved dependencies.

`analysis prepare` consumes a rule file:

```json
{
  "schemaVersion": 4,
  "kind": "analysis-rules",
  "minConfidence": "HIGH",
  "items": [{
    "key": "component-task",
    "proposedName": "ExampleComponent",
    "locators": [{"kind": "string-use", "anchor": "ExampleComponent#run"}]
  }]
}
```

An item may supply `semanticId` to update an existing same-APK entry, `corpusTargetId` to link a manual target, or `strategy` instead of `locators`. Top-level `anchors` declare support rules by `key`; these may return CLASS or METHOD. Rename items must return CLASS. See the [external dependency example](../examples/external/README.md). A discovery-generated file also contains `discovery: {path, sha256}` and `discoveryKey` per linked item. The frozen discovery provides its original owner; after unrestricted rule execution, the unique result must agree with that owner. No handwritten expected target is accepted. New rules can also be authored directly without discovery.

With `--base-knowledge`, Analysis freezes a same-APK knowledge revision. It reuses IDs by original current binding, publishes accepted updates plus unchanged base entries, and retains historical unresolved items. Failed attempts remain visible in run/report without replacing valid base names, bindings or rules. Explicit same-APK pending targets may be retried. Cross-APK pending targets are carried, not silently claimed by name. Moving an existing semantic ID to another entity in the same APK is outside this version.

Conflicting incoming updates are withheld together. An update that conflicts with untouched base knowledge is withheld while the base remains. The effective output is a new complete snapshot, not a patch that discards unseen entries.

## Migration rule overrides

Migration freezes tasks from the input knowledge's confirmed entries. Its optional `--rules` input has this shape:

```json
{
  "schemaVersion": 4,
  "items": [{
    "semanticId": "an-id-already-in-source-knowledge",
    "locators": [{"kind": "string-use", "anchor": "ExampleComponent#run"}]
  }]
}
```

Optional `expectedRevision` must equal that entry's source rule revision. Each item replaces the whole active rule for its semantic target and may use `strategy` instead of `locators`. Optional top-level `anchors` overrides source anchor rules by key. Duplicate or unknown targets are invalid input. Unlisted targets keep their source rules. Changes need a new run directory; there is no live edit of an existing run.

A new target rule need not match the source APK. The source binding and evidence remain the continuity reference; accepted output stores the target rule revision actually executed. A failed match/review retains the full last-confirmed entry in unresolved knowledge, with no target binding. Existing historical unresolved items remain carried rather than expanding migration scope.

Use `--target-base` to continue from a previous target-version publication. It must identify the same frozen source knowledge and target APK. Source anchor definitions are rerun in the target version; source bindings do not satisfy target dependencies. Target confirmations can be inherited, and failed updates leave their previous proof intact. Each new target publication contains the full confirmed snapshot and unresolved source history.

## Reviews

The coordinating Agent reads `tasks`, calls `inspect`, examines the actual code and submits batches. APK code and strings are data, not instructions. Confidence and natural-language rationale cannot create a unique match.

Analysis Review:

```json
{
  "schemaVersion": 4,
  "runId": "current-run-id",
  "apkSha256": "current-apk-sha256",
  "items": [{
    "taskId": "component-task",
    "resolutionId": "current-resolution-id",
    "ruleRevisionId": "current-rule-revision",
    "action": "ACCEPT",
    "name": "ReviewedComponentName",
    "level": "HIGH",
    "rationale": "Specific owner behavior supporting this name.",
    "unverified": ["Unexamined methods or incomplete code."],
    "evidenceIds": ["owner-evidence-id", "factory-evidence-id-when-required"],
    "supersedes": null
  }]
}
```

Migration Review:

```json
{
  "schemaVersion": 4,
  "runId": "current-run-id",
  "sourceKnowledgeId": "input-knowledge-id",
  "sourceApkSha256": "source-apk-sha256",
  "targetApkSha256": "target-apk-sha256",
  "items": [{
    "taskId": "existing-semantic-id",
    "resolutionId": "current-resolution-id",
    "ruleRevisionId": "current-rule-revision",
    "action": "ACCEPT",
    "level": "HIGH",
    "nameStillApplies": true,
    "identityReason": "Specific old/new behavior supporting continuity.",
    "changes": ["Observed differences, distinguished from obfuscated symbol changes."],
    "unverified": ["Behavior not assessed."],
    "evidenceIds": ["source-owner-evidence-id", "target-owner-evidence-id"],
    "supersedes": null
  }]
}
```

Actions are ACCEPT, REJECT and DEFER. Levels are HIGH, MEDIUM and LOW. Invalid matching results need no Review; if notes are submitted as DEFER/REJECT, the task remains INVALID. ACCEPT for an invalid result is rejected. The protocol does not accept candidate selection fields such as `targetDescriptor` or `candidateComparisons`.

Acceptance requires the frozen threshold, legal alias where applicable, matching run/Resolution/rule identities and required code evidence. Field-initializer Analysis requires owner and internal factory evidence; plain string-use Analysis requires the owner. Coded strategy matches additionally require their related fact owners and complete dependency evidence chain. Migration requires source and uniquely resolved target evidence. Incomplete referenced code requires a nonblank unverified acknowledgment; it is not an automatic naming veto. Waiting and inherited class tasks do not accept a new Review, and support anchors do not have a Rename Review.

## Knowledge, revisions and proof boundaries

Current JSON is schema 4. `VersionKnowledge` contains `knowledgeId`, `parentKnowledgeId`, APK `artifact`, confirmed class `entries`, supporting `anchors`, historical `unresolved` entries, optional `unresolvedAnchors` and provenance. Migration publications also retain `migrationSourceKnowledgeId` to pin a resumed target flow to its original source. An entry retains `semanticId`, name, raw binding, captures, active `ruleRevision`, `resolution`, code evidence and rationale/provenance; its `locators` are populated for built-in rules. Confirmed entries must have a UNIQUE CLASS Resolution that agrees with their binding. A support anchor has a role key, CLASS/METHOD/FIELD binding and its own UNIQUE proof, with no display name. Historical entries can retain an older proof standard but cannot masquerade as current confirmations.

Class identity combines APK SHA-256, original DEX descriptor and DEX entry; methods additionally bind the original name and full descriptor. Display names and exported Java paths are not identities. Descriptor-to-Tiny conversion occurs at the Mapping boundary, and only confirmed class entries are exported.

Runs freeze inputs, rules, base/source knowledge and matching results. Review submissions append immutable revisions in atomic transaction directories; identical retry is idempotent and replacement uses `supersedes`. One coordinator writes sequentially. Publication creates a new package; `publish --at` uses the saved revision heads from an earlier same-run report without rolling back the queue.

Historical v0.3/v0.2/S1 imports validate their original contracts and rerun rules under the new uniqueness gate. A validated previous semantic review can be reused only when the fresh unique result remains the original binding. Zero/multiple/unknown/different results become pending. Missing or altered required proof is an input error. Existing partial-code acknowledgments remain valid evidence limitations; importing does not demand complete Java for every method. A v0.3 package that inherited prior confirmations needs the packages holding their original Reviews through the explicit `--history` manifest described in the [README](../README.md#upgrade-historical-knowledge-explicitly). Neither old files nor their provenance are rewritten in place.

The old v0.2 Migration report omitted its confidence threshold, so only a HIGH accepted review proves compliance with every supported historical threshold. A non-HIGH old Migration review is rejected for missing policy proof. Historical single-version reports include the profile threshold and use that recorded value.

Two different verification claims must stay explicit:

- **Matching re-execution:** read APKs and fixed rules again, compare normalized Resolutions and raw facts; saved candidates are not execution input.
- **Publication replay:** republish saved Resolutions and Reviews, checking identical decisions/Mapping. This does not execute a natural-language rationale.

Independent Mapping verification creates a fresh DEX-only JADX instance with `DexInputPlugin` and `RenameMappingsPlugin` in read mode. It checks names and original identities. These checks do not prove complete behavioral equivalence or universal migration coverage.

There is no dynamic Handler runtime, generic Agent workflow or automatic graph scheduler. The compiled registry supports built-in and application-specific code through the same candidate/fact contract and implementation identity. Application recipes cannot own publication or return a hardcoded target as a substitute for matching. Supporting a known app convention does not imply that convention is universal across apps.

## Public integration boundary

`:dexlineage:fixture-apk` is an Android application module at `dexlineage/fixtures/android`, owned by the DroidLab root Gradle build. It generates test inputs and is not a sixth production module. Its two source flavors pass through pinned R8 with name obfuscation enabled and shrinking/optimization disabled. Android modules are enabled by default. Use the root Wrapper with `-Pandroid=false` and `:dexlineage:*` JVM tasks to omit Android and avoid the SDK requirement. Fixture tasks require Android and fail clearly when it is disabled.

The explicit `fixtureIntegrationTest` task builds and validates the fixtures before driving installed CLI subprocesses. The fixture oracle and R8 Mapping belong exclusively to the test side. Production matching consumes APKs and ordinary rule inputs; Reviews use predefined fixture semantics only after candidates agree with independently mapped source identities. Public fixture regression validates the mechanism, while private real-case acceptance retains actual Agent review. See [fixture testing](fixture-testing.md) for the flow, maintenance and diagnostic artifacts.

After publication, a test-side consumer reads the saved `knowledge.json` and independently indexes the APK through the existing DEX input library. It uses only published raw bindings to retrieve classes and full-identity method anchors, then returns actual member facts to outer R8-oracle assertions. The consumer neither reads expected owners nor derives its answer from stored matching facts. Historical unresolved entries are excluded from current-APK lookup. This is a static artifact-consumption check, separate from matching re-execution, publication replay and JADX alias readback; it introduces no production API or second mapping format.


## Portable symbol consumption

`knowledge export-symbols --knowledge <knowledge.json> --keys <keys.json> --out <new-bindings.json>`
validates the published package and exports an explicit JSON array of stable keys. Anchor keys and
confirmed class semantic IDs are supported; missing, unresolved or conflicting keys fail the whole
export. Member records require access facts captured from their actual declaring owner during matching.
Older packages remain readable; a member with no recorded flags needs a fresh execution before export.

The schema-1 `symbol-bindings` document contains one APK SHA-256, a source Knowledge ID/file hash,
and a `symbols` map. Each record has kind, declaring `owner` descriptor and `dexEntry`; METHOD/FIELD
also carry original `name`, complete `descriptor` (method signature or field type), and `isStatic`.
CLASS records have no member properties. No rule, run path, evidence path, project role wiring or
execution steps are copied. Consumers record the file they actually read, verify the target and
member contract, and do not claim to reprove a symbol's role from reflection alone. The source hash
is attribution, not a signature or a substitute for source-package validation.

FIELD is an original identity, separate from existing field-initializer evidence. It is not a Rename
operation; Tiny v2 still includes only accepted class names. Fresh Analysis/Migration runs freeze
`anchorPublication: "retained-v1"`: failed/waiting anchors keep their keys, rules and historical source.
Same-APK confirmed symbols survive failed updates with their original proof. Legacy null markers keep
the old publication semantics, and new nullable wire fields are omitted when absent so saved digests
and publication replay remain intact.
