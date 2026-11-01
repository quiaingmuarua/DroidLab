# Detect Env Agent README

`crackme:detect-env` is the target application for environment-detection,
fingerprint, privacy, and anti-analysis experiments. It owns its app behavior,
local scenario events, verdict shaping, and generated scenario truth.

## Start Here

- `src/main/java/com/detect/app/sample/DetectEnvApplication.java`
- `src/main/java/com/detect/app/sample/HttpServerManager.java`
- `src/main/java/com/detect/app/sample/scenario_control/DetectEnvWorkerEvents.java`
- `src/main/java/com/detect/app/sample/scenario_control/DetectEnvWorkerHostEvents.java`
- `src/main/java/com/detect/app/sample/worker/DetectEnvDeviceProperties.java`
- `src/main/java/com/detect/app/sample/scenario_control/DetectEnvCheckHandlers.java`
- `src/main/java/com/detect/app/sample/scenario_control/DetectEnvGeneratedContracts.java`
- `src/main/java/com/detect/app/sample/scenario_control/DetectEnvScenarioModels.java`

`DetectEnvApplication` creates the app-owned extension definitions once.
`HttpServerManager` is a thin startup seam that receives those definitions and
starts the loopback server on port `5566`. When a Runtime API URL is supplied,
the Application also passes the same business definitions to `AndroidWorker`.

## Local Event Contract

The five business capabilities, shared with Worker Delivery, are:

- `extension.worker.detect-env.networking`
- `extension.worker.detect-env.debug-state`
- `extension.worker.detect-env.device-properties`
- `extension.worker.detect-env.class-loader-state`
- `extension.worker.detect-env.scenario-catalog`

`DetectEnvWorkerEvents` owns capability definitions.
`DetectEnvCheckHandlers` owns app-side collection and verdict shaping.
`DetectEnvGeneratedContracts` owns event constants, port, prepare Activity,
and generated scenario definitions.

`extension.worker.detect-env.scenario-catalog` retains the `check`, `summary`,
`scenarios`, and `count` payload. Its `check` equals that full name. Every
generated `verify_event` must appear in the same capability server's
`GET /events` result and start with `extension.worker.detect-env.`.

The default standalone build also exposes the local-only
`extension.worker.detect-env.duck.scan`, so it has six HTTP events. See
[Duck SDK integration](DUCK_SDK.md) for its fixed asset, lifecycle, API and
baseline limits. Duck results come from the original SDK and are never included
in the remote business catalog. Integration scans alone do not prove hook
behavior; the separate Frida system-properties case correlates them with API
callers, configured inputs and restoration under its Host-owned verdict.

A configured
Worker build additionally exposes three local-only Host controls:

- `extension.worker.detect-env.host.snapshot`
- `extension.worker.detect-env.host.start`
- `extension.worker.detect-env.host.stop`

The configured HTTP assembly therefore has nine events. These controls and
Duck scans are passed only to capability HTTP. They must never be passed
to `AndroidWorker` or declared as WorkerGroup business capabilities.

## Worker Proof Configuration

- `-PagentforgeWorkerRuntimeApiBaseUrl=<absolute HTTP(S) URL>` enables Worker
  assembly. Missing or blank keeps the existing local-only behavior.
- `-PagentforgeWorkerGroupId=<id>` selects the WorkerGroup and defaults to
  `agentforge-detect-env-workers`.
- CI reads the Runtime API and Adapter ports from
  `config/xa-mass-runtime.json`, uses ADB reverse for both and exposes device
  port `5566` on host port `18184`.
- The proof reads the selected device API level, requires API 33 or newer, and
  uses `ANDROID_SERIAL` for explicit selection when multiple devices exist.
- The proof executes the actual environment report CLI (catalog plus four checks),
  retains results locally and verifies one subsequent take is empty, verifies repeat registration returns
  `already_registered`, explicitly prepares `agentforge-rpc-v1.<workerGroupId>`
  in Project `default` during initial setup, and requires the finite Task close
  operation on that RPC Task to be rejected with HTTP `400` and Server error
  code `12008`.
- XA Mass 3.1 Runtime requests explicitly declare packaged administrator `1000`
  with the development user/role headers. Server resolves its grants. Device
  capability HTTP receives no Runtime caller declaration.
- The orchestrator's first registration reads the detect-env declaration from
  `config/worker-groups.json`. The acceptance driver retains an independent App
  fixture and repeats registration, so catalog/App event drift fails closed.
- Local proof runs may override Server and Adapter ports with the shared
  `SERVER_PORT` and
  `XA_MASS_ADAPTER_PORT` variables before both APK construction and
  proof execution. `ANDROID_WORKER_DEVICE_FORWARD_PORT` remains proof-only;
  the three values must be distinct.
- The cross-repository proof, acceptance driver, and locked Release
  verification are owned by AgentForge and must not make XA Mass a Gradle
  included build.

## Placement Rules

- Keep real detection behavior under the nearest feature package.
- Keep only scenario-facing event assembly under `scenario_control`.
- Capture `Context` explicitly in event-handler closures.
- Use explicit `detect-env.*` capability names with
  `WorkerEventDefinition.extension(...)`; never add short-name aliases.
- Keep App checks and catalog truth in this application; HTTP transport and
  Host reports consume its definitions and results.
- Update the exact-event test when intentionally changing the public set.

## Verification

```powershell
python -m agentforge_tools duck-sdk resolve
.\gradlew.bat :crackme:detect-env:testDebugUnitTest
.\gradlew.bat :crackme:detect-env:assembleDebug
python -m agentforge_tools release resolve --component worker-sdk
```

For the configured Worker host and proof tooling, Gradle builds the APK and a
cross-platform Python orchestrator owns the Runtime, ADB and HTTP lifecycle.
Windows uses the installed Android/JDK/Python tools directly; WSL is not part
of the proof contract. Gradle provenance binds the clean AgentForge HEAD,
Worker configuration and exact APK SHA256 into all four lifecycle phases and
the independent raw finite window phase. The latter uses a new scope and Worker
identity; it does not extend lifecycle identity claims across scopes.

```powershell
$runtimeApiBaseUrl = python -m agentforge_tools config `
  --field runtime-api-base-url
.\gradlew.bat --no-daemon `
  "-PagentforgeWorkerRuntimeApiBaseUrl=$runtimeApiBaseUrl" `
  -PagentforgeWorkerGroupId=agentforge-detect-env-workers `
  :crackme:detect-env:workerProofArtifact
python -m unittest discover -s crackme/detect-env/host -p 'test_*.py' -v
python -m agentforge_tools proof android `
  --runtime-root .runtime/xa-mass-server `
  --apk crackme/detect-env/build/outputs/worker-proof/detect-env-debug.apk `
  --apk-provenance crackme/detect-env/build/outputs/worker-proof/detect-env-debug.provenance.json
```

App unit tests cover the event and catalog contracts. Actual collection through
HTTP or Worker Delivery additionally requires this application on a connected
device; unit success is not device evidence.

## Remote environment report acceptance

`agentforge_tools.environment_check` owns the report; this App owns the checks.
Root CLI `prepare` explicitly
creates the caller RPC Task only for initial setup; `run --worker-id <UUID>`
validates the existing Task and Group identity, collects the catalog and four
App-owned checks through public RPC, and preserves original check/summary/verdict/raw
in local JSON. App fail/unknown is a valid observation, not a transport failure.
The proof's initial phase executes this CLI and independently checks its report.
It no longer requires consumed RPC content to be reloadable. Other phases retain
the same Worker/Task identities without preparation or repair. Both deployment
and proof use DEFAULT Pacer; proof selects ports 18082/18083. Provenance schema 2
binds APK/AgentForge identities to the fixed XA Release and both ZIP hashes;
`artifact-origin.json` also records the actually started JAR hash. CI evidence
contains report digest/identities/assertions, not raw App result content.

## Named window Pool consumption

The same proof entry runs a final `window-pool` phase in a separate exact
`test_*_window` scope. The host loads all four deployment Groups and their named
Pool/query/paired-feedback bindings before startup validation. Only detect-env
has a live device. Three raw finite Items call the existing debug-state Handler
with `{}` through `worker.detect-env.available` and `detect-env-window` supply.
The Host verifies actual Item queries, terminal successful Results and positive
`windowAssignedAt`/`windowAssignmentCount` feedback, without changing App verdicts
or uploading result bodies. Every mutation is sent once and identities are kept
on uncertain response. Strict window qualification/rejection remains XA's proof.
