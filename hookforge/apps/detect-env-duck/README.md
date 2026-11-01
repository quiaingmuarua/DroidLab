# detect-env Duck App pack

From the DroidLab root, with the Duck-enabled detect-env APK installed and
the pinned Frida server running:

```bash
npm --prefix hookforge run cli -- plan --app hookforge/apps/detect-env-duck/app.json --trace file-io,system-properties
npm --prefix hookforge run cli -- run --serial emulator-5554 --app hookforge/apps/detect-env-duck/app.json --trace file-io,system-properties
```

The Host cold-starts the original `android.app.NativeActivity`, verifies the exact
main PID, waits for `DetectEnvMainActivity` and HTTP health, then activates only
the selected capabilities and this App's read-only subscriber. It does not start
a scan or enable replacement. Use the existing UI or HTTP scan event normally.
The owned HTTP forward is printed in `run.log`; it lasts for this run only.

To deliver normal property values, add `--replace system-properties --profile
hookforge/cases/duck-system-properties/normal.json`. Risk values use the sibling
`risk.json`.
Only these selected property inputs are claimed; other Duck findings retain
their original meaning.

`host.mjs` owns App readiness, not a Frida session. `agent.ts` emits App lifecycle
and read-only API-origin events. Public API installation, policies and callback
cleanup remain with the shared owner. The App subscriber cannot count as SDK
coverage in acceptance; the Host evaluates the original API records separately.
