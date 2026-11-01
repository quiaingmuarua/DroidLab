# Android Lab Agent README

`crackme:android-lab` is the target application for Android framework,
networking, crypto, IPC, and third-party integration demos. It owns its app
behavior, local scenario events, verdict shaping, and generated scenario
truth.

## Start Here

- `src/main/java/com/app/androidlab/sample/AndroidLabApplication.java`
- `src/main/java/com/app/androidlab/sample/HttpServerManager.java`
- `src/main/java/com/app/androidlab/sample/scenario_control/AndroidLabWorkerEvents.java`
- `src/main/java/com/app/androidlab/sample/scenario_control/AndroidLabCheckHandlers.java`
- `src/main/java/com/app/androidlab/sample/scenario_control/AndroidLabGeneratedContracts.java`
- `src/main/java/com/app/androidlab/sample/scenario_control/AndroidLabScenarioModels.java`

`HttpServerManager` constructs the app-owned extension definitions
with an explicit `Context`, passes them to the XA Mass capability HTTP SDK, and
starts the loopback server on port `5567`.

## Local Event Contract

The exact three local capabilities are:

- `extension.worker.android-lab.system-build-info`
- `extension.worker.android-lab.system-properties`
- `extension.worker.android-lab.scenario-catalog`

`AndroidLabWorkerEvents` owns capability definitions.
`AndroidLabCheckHandlers` owns app-side collection and verdict shaping.
`AndroidLabGeneratedContracts` owns event constants, port, prepare Activity,
and generated scenario definitions.

`extension.worker.android-lab.scenario-catalog` retains the `check`, `summary`,
`scenarios`, and `count` payload. Its `check` equals that full name. Every
generated `verify_event` must appear in the same capability server's
`GET /events` result and start with `extension.worker.android-lab.`.

## Placement Rules

- Keep target behavior in the nearest app feature package.
- Keep only scenario-facing event assembly under `scenario_control`.
- Capture `Context` explicitly in event-handler closures.
- Use explicit `android-lab.*` capability names with
  `WorkerEventDefinition.extension(...)`; never add short-name aliases.
- Keep the HTTP layer thin and keep scenario truth in this application.
- Update the exact-event test when intentionally changing the public set.

## Verification

```powershell
.\gradlew.bat :crackme:android-lab:testDebugUnitTest
.\gradlew.bat :crackme:android-lab:assembleDebug
python -m agentforge_tools release resolve --component worker-sdk
```

App unit tests cover the event and catalog contracts. Actual HTTP collection
requires this application on a connected device; unit success is not device
evidence. External APK experiments do not need a new catalog registration here.
