# Detect Env Agent README

`labs:detect-env` is the target application for environment-detection,
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

## Optional Worker configuration

- `-PagentforgeWorkerRuntimeApiBaseUrl=<absolute HTTP(S) URL>` enables Worker
  assembly. Missing or blank preserves the local-only behavior.
- `-PagentforgeWorkerGroupId=<id>` selects the WorkerGroup and retains the
  existing default `agentforge-detect-env-workers`.

These existing build properties remain available for consumers. DroidLab does
not start a Runtime, provision its deployment or include private Worker proof
orchestration. A configured Worker requires its consumer's matching Runtime,
Group registration, installed handlers and device connectivity.

## Placement rules

- Keep detection behavior under the nearest feature package and event assembly
  under `scenario_control`.
- Capture `Context` explicitly in event-handler closures.
- Keep full `detect-env.*` capability names with
  `WorkerEventDefinition.extension(...)` and no short-name aliases.
- App checks and catalogs own their results; HTTP and Worker transports consume
  those definitions. Update exact-event tests when changing the public set.

## Verification

Run from the DroidLab root:

```sh
python -m droidlab_tools release resolve --component worker-sdk --lock XA_MASS_RELEASE.lock.json
python -m droidlab_tools duck-sdk resolve
./gradlew :labs:detect-env:testDebugUnitTest :labs:detect-env:assembleDebug
```

Use the root `gradlew.bat` on Windows. Build-time input verification is read-only.
The unit tests cover event and catalog contracts; actual HTTP collection and
Worker Delivery require this application on a connected device.
