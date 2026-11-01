# Duck SDK integration baseline

detect-env hosts the original Duck Detector AAR behind its existing loopback
NanoHTTP capability surface and a small third-party checks page. This proves
SDK integration and collects original evidence; it does not prove hook evasion
or reproduce the installed Duck APK's complete application environment.

## Fixed input and build

`duck-sdk.lock.json` pins the upstream Release, commit, original AAR SHA256 and
exact external dependencies. The dependency list follows the pinned
`headlessSdkModules` Maven publication and `compileForSdkHosts` stdlib version;
upstream UI dependencies are not included. The resolver and Gradle both check
the AAR bytes. A corrupt cache fails without replacing it. The license and
source attribution are included in the APK's `assets/licenses`.

```powershell
python -m agentforge_tools release resolve --component worker-sdk
python -m agentforge_tools duck-sdk resolve
.\gradlew.bat :crackme:detect-env:testDebugUnitTest :crackme:detect-env:assembleDebug
```

Use JDK 21, AGP 9.4.1, Gradle 9.8.1, build tools 37.0.0 and SDK Manager package
`platforms;android-37.0`. The API 33 runtime remains supported. The standalone
APK is under `crackme/detect-env/build/outputs/apk/debug/`.

## Start the actual SDK host

```powershell
adb -s emulator-5554 install -r crackme/detect-env/build/outputs/apk/debug/detect-env-debug.apk
adb -s emulator-5554 shell am force-stop com.detect.app.sample
adb -s emulator-5554 shell am start -n com.detect.app.sample/android.app.NativeActivity
adb -s emulator-5554 forward tcp:18184 tcp:5566
```

The NativeActivity delegates to the existing main screen after early mount and
virtualization collection. The main Activity captures its extras and registers
the SDK host. The first Duck scan attaches the SDK's invisible mount sampler
on the main thread before invoking any detector or binding helper processes;
ordinary app startup does not initialize WebView. The view is destroyed with
the host Activity. The SDK's merged app-zygote preload and
services remain intact. HTTP and Worker start only in the exact main process.
Starting an exported Java Activity directly skips early launch collection;
its absence stays visible in the original SDK reports and host metadata.

The host requests full package visibility and biometric permissions. Online
revocation refresh and the active timing experiment keep SDK defaults; no
consent-changing API or automatic grant is added.

## Original scan API

```powershell
$duckUri = 'http://127.0.0.1:18184/events/extension.worker.detect-env.duck.scan:call'
$response = Invoke-WebRequest -Method Post -Uri $duckUri `
  -ContentType 'application/json' -Body '{}' -TimeoutSec 300
$scan = $response.Content | ConvertFrom-Json
if ($scan.status -ne 'succeeded') { throw 'Duck scan collection failed' }
$output = Join-Path (Get-Location).Path ".runtime/duck-detector/runs/$($scan.result.scanId)"
New-Item -ItemType Directory -Path $output -ErrorAction Stop | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $output 'http-response.json'), $response.Content, $utf8)
[IO.File]::WriteAllText((Join-Path $output 'report.json'), ($scan.result | ConvertTo-Json -Depth 100), $utf8)
[IO.File]::WriteAllText((Join-Path $output 'report.txt'), $scan.result.reportText, $utf8)

Invoke-RestMethod -Method Post -Uri $duckUri -ContentType 'application/json' `
  -Body '{"detectorIds":["system_properties"]}' -TimeoutSec 300
```

Omitting `detectorIds` runs the entire original registry. An explicit array
must be nonempty, unique and contain only known IDs. Invalid input uses the
existing HTTP input-error response. UI and HTTP share a single scan gate;
overlapping scans fail instead of entering a queue. A host request timeout
does not establish that a scan stopped; do not automatically replay it.

The existing `status/eventCode/diagnosticCode/result` response envelope is
unchanged. `result` contains SDK identity, a new scan ID, PID, timestamps,
API/ABIs, selected/executed IDs, host conditions and each complete original
`id/status/report`. Report rows, bullets, verbatim blocks, null details and
original whitespace are preserved. Scans execute freshly; launch snapshots
retain their original process-start lifetime. No detector verdict is recomputed.

Each completed result also contains `reportText`, rendered once from that same
snapshot, and `host.app` with the actual package name, version name/code and
target SDK. Rendering uses no Android APIs or detectors. It preserves the
original status and report content; a missing result, malformed block or
rendering failure fails the event instead of returning an empty report.

The Host command above writes three views of a single request:

| File | Contents |
| --- | --- |
| `http-response.json` | Complete HTTP envelope and result |
| `report.json` | The result object, including original fields and `reportText` |
| `report.txt` | Exactly that result's `reportText` |

The new scan ID names a fresh output directory; an existing directory is not
overwritten. Text uses UTF-8 without a BOM or an added newline. The selected
example makes a separate fresh scan; it cannot be substituted for the full
scan's result. After using the manually created forwarding port, remove it
with `adb -s emulator-5554 forward --remove tcp:18184`.

HTTP success means collection completed. `DANGER` and `WARNING` remain findings;
`ALL_CLEAR` remains the SDK's limited negative result. `INFO/SUPPORT` means
unavailable or unsupported and `INFO/ERROR` means failure. Neither is a negative
result. Missing results, thrown exceptions or malformed data never become an
empty successful report. Diagnose integration errors such as missing libraries
or startup wiring rather than treating them as environmental limitations.

## Visible entry and evidence

Open **third-party-checks → Duck Detector SDK**, then **Scan Duck SDK**. The page
defaults to the Full Report. **View JSON / View report** changes only the view,
and **Copy current format** copies the currently selected representation.
Details scroll below a compact status and action area.

**Export TXT / Export JSON** opens the system document picker with the filename
`duck-report-<scanId>.txt` or `duck-report-<scanId>.json`. Choose a location
such as Downloads. Both formats use the current completed scan, UTF-8 without
a BOM, and no added newline. JSON exports the same result-object shape as
`report.json` above; TXT is exactly its `reportText`. No storage permission
is requested.

An export captures immutable content before opening the picker. UI scanning
and another export remain disabled until cancellation or the write and stream
close finish. File writes run on IO threads and hold no SDK scan gate, so HTTP
scans retain their existing admission rules. Cancellation and write failures
are shown explicitly and never trigger a new scan. If Activity recreation
loses a pending snapshot, that export fails visibly; it never substitutes a
later scan. Scan again to obtain a new UI result. Export recovery and history
are outside this slice.

The report includes SDK/host identity, scope, status counts, original dangerous
and warning findings, all detector blocks, and actual host conditions.
Completed counts are separate from `ALL_CLEAR`; `INFO/SUPPORT` and
`INFO/ERROR` are never counted as safety conclusions. Selected scans are
labelled explicitly. The renderer adds no overall security score or pass/fail
verdict, and retains original detail, coverage limits and verbatim whitespace.
Times are fixed scan timestamps displayed in UTC, not export times.

The separately installed upstream APK and this host can have different
target SDKs and conditions. Reports identify those conditions; matching the
upstream layout does not establish identical findings or complete APK parity.
The last successful UI result is still written to app-private
`files/duck-sdk-ui-result.json` for inspection. HTTP never reads that receipt,
and display/copy/export operations never scan again.

For the first baseline, retain the HTTP full response, two selected scans with
distinct scan IDs, UI receipt, Logcat, screenshot, APK/AAR hashes and another
scan after a cold NativeActivity launch under
`.runtime/duck-detector/runs/<run-id>`. API33/x86_64 gaps remain explicit.

The 2026-10-20 API33/x86_64 baseline returned all 18 registry results through
HTTP, the UI and a fresh process after a cold launch. Two selected
`system_properties` calls had distinct scan IDs in the same process; invalid
selections and an overlapping request failed. No `INFO/ERROR` result occurred.
The SDK returned `INFO/SUPPORT` for `custom_rom`, `dangerous_apps`,
`heap_residue`, `kernel_check` and `su`; their complete reports retain the
original coverage limits. Early launch extras, attached sampler, full package
visibility and completed app-zygote preload were observed. Findings and
limited results remain original SDK evidence rather than a pass/fail security
verdict for the emulator.

The new event is HTTP-only: six standalone or nine configured HTTP events.
Worker Delivery and the Group/scenario catalog still expose five business
definitions. Existing Worker and Frida proofs retain their own scopes.
The separate [Frida system-properties experiment](../../frida/cases/duck-system-properties/README.md)
now exercises four property inputs through the original scan event, retaining
this JSON/text format. Its configuration, expectations and intervention verdict
belong to the Host; this SDK baseline integration owns no hook or bypass result.
That experiment has local API33/x86_64 evidence and is not a Duck CI gate.
