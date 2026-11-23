# Frida 17 Migration Notes

This note focuses specifically on Android Java access, native API drift, and
Node-hosted bundle behavior in this workspace. It is not a complete summary of
every Frida 17 breaking change.

The upstream [Frida 17.0.0 release notes](https://frida.re/news/2025/05/17/frida-17-0-0-released/)
and [Bridges guide](https://frida.re/docs/bridges/) describe the runtime API
changes and bridge usage for the official CLI and custom hosts.

This document captures the main breakage we hit while moving the `frida/`
workspace from the older Frida 16 assumptions to Frida 17 runtime behavior.

Use this file first when:

- Node host injection suddenly stops activating Java-based modules
- CLI scripts work, but `hosts/node-cli/run.js` selects zero Java modules
- a future Frida upgrade changes how Android Java bridge access is exposed

## Short Version

The original migration established these host differences:

- Frida CLI on Android exposed a usable global `Java`
- Node-hosted `session.createScript()` bundles should not assume global `Java`
- Node-hosted bundles must access Java through a facade backed by
  `frida-java-bridge`

Current bundled code shares the implementation in `packages/runtime/java-bridge.ts`.
`packages/core/java-bridge.ts` re-exports it for existing callers. The loader,
shared API runtime and managed extensions use this same bridge instance.

The current setup also initializes the Application ClassLoader and App-private
DEX cache paths, and applies the reviewed ART patch to the exact pinned bridge.
The [current initialization and patch checks](#current-initialization-and-patch-checks)
below cover those later fixes.

## Before vs After

### Before

The older code assumed Android Java access looked like this everywhere:

```ts
if (typeof Java !== 'undefined' && Java.available) {
  Java.perform(() => {
    const ActivityThread = Java.use('android.app.ActivityThread');
  });
}
```

This worked reliably in CLI-injected scripts, but it was not a safe assumption
for Node-hosted bundles under Frida 17.

### After

The current code uses an explicit facade:

```ts
import { Java, isJavaAvailable, performOnJava } from '@runtime/java-bridge';

if (isJavaAvailable()) {
  await performOnJava(() => {
    const ActivityThread = Java.use('android.app.ActivityThread');
  });
}
```

This keeps module code independent from whether `Java` was injected globally by
the runtime.

## Symptoms We Saw

The migration issue surfaced with a confusing split:

- `frida -U/-Uf/-UF -l script.js` worked on-device
- `node hookforge/hosts/node-cli/run.js ...` connected, attached, and loaded the bundle
- but Java-dependent modules did not activate

Observed symptoms included:

- `Java available: false`
- `Selected 0 modules: (none)`
- probe output showing `hasJavaGlobal=false`
- Node host smoke tests succeeding at transport level but failing at Java-level

This made the issue look like a loader or timing bug at first, but the core
problem was earlier in the stack.

## Root Cause

Under our Frida 17 Node path:

- transport and script loading worked
- the Android Java bridge was not exposed as `globalThis.Java`
- waiting longer did not fix that by itself

We confirmed that:

- CLI path still had usable global `Java`
- explicit `import Java from 'frida-java-bridge'` worked in Node-created bundles

Explicit bridge access resolved this migration issue in Node-injected bundles.

## Fix Applied

### 1. Upgrade runtime dependencies

The current package declarations retain the Frida 17 client range and pin the
reviewed Java bridge exactly:

- `frida`: `^17.4.0`
- `frida-java-bridge`: `7.0.13`

Use the installed versions from `package-lock.json` and the matching server in
`test/api33/toolchain.lock.json`. Root `:hookforge:npmCi` installs the locked
dependencies and applies the bridge patch through `postinstall`.

### 2. Introduce a Java facade

`packages/runtime/java-bridge.ts` owns Java bridge access inside the bundled
agent. The core path remains a compatibility re-export of that implementation.

It provides:

- `Java`
- `isJavaAvailable()`
- `initializeJavaClassLoader()`
- `performOnJava()`

### 3. Migrate shared runtime helpers

These core files were migrated away from global `Java` assumptions:

- `packages/core/env.ts`
- `packages/core/java-utils.ts`
- `packages/loader/index.ts`

### 4. Migrate Android modules

Android capability modules were updated to use `performOnJava()` and the facade
instead of raw global `Java`.

That change affected:

- `packages/modules/android-runtime-bypass`
- `packages/modules/android-device-profile`
- `packages/modules/android-network-observe`
- `packages/modules/android-validation`

### 5. Adjust Frida 17 native API assumptions

One follow-up breakage showed up in the runtime-bypass module.

Older assumptions expected:

- `Module.findExportByName('libc.so', 'fopen')`

The Frida 17 runtime used during that investigation had these APIs:

- `Module.findExportByName` is not present
- global export lookup is exposed through `findGlobalExportByName` or
  `getGlobalExportByName`

`packages/runtime/native-bridge.ts` resolves exports through those global lookup
methods, with a legacy lookup fallback when that API is available.

### 6. Tighten SIM fallback merge rules

The device-profile module also needed a data-quality fix during the migration.

We saw a split where:

- TelephonyManager returned useful SIM values such as `simCountryIso=ca`
- system properties returned lower-quality placeholders such as `","` or
  unrelated fallback values

The collector now treats system-property SIM values as fallback-only:

- they only fill empty fields
- they do not overwrite TelephonyManager results
- placeholder strings such as `","` are ignored

## Current Initialization and Patch Checks

Bridge availability and Application readiness have separate checks.
`initializeJavaClassLoader()` uses `Java.performNow` and
`ActivityThread.currentApplication()` to obtain the App loader. It sets the
App's writable `cacheDir` and `codeCacheDir` before publishing that loader.
Setting only the loader can leave `/data/local/tmp` as the DEX output path,
which a newly installed non-root App cannot write. Initialization has a
deadline and cancellation support. `performOnJava()` also propagates synchronous
throws from deferred callbacks and rejected asynchronous work.

The reviewed bridge patch addresses an ART Nterp entry-point synchronization
window observed during API33 ARM64 spawn tests. After instrumentation setup it
flushes pending Interceptor changes and synchronizes registered replacement
methods. The patch installer checks the exact package, lock identity and file
hashes. This historical observation does not add ARM64 CI coverage. Details and
source/output hashes are in the [bridge patch guide](../patches/README.md).

After dependency installation, check the installed patch without modifying it:

```bash
npm --prefix hookforge run check:bridge-patch
```

For spawn-time Java failures, inspect loader/cache initialization and the patch
check alongside bridge exposure. Keep managed adapters on the shared bridge
export supplied by the public bundle. See the
[Script Integration Guide](SCRIPT_INTEGRATION_GUIDE.md#runtime-owners).

## Validation Commands

Run from the DroidLab root with Node from `hookforge/.node-version` on PATH.
Prepare and verify host checks and production artifacts first:

```bash
./gradlew -Pandroid=false :hookforge:check :hookforge:verifyProductionArtifact
```

PowerShell uses `./gradlew.bat`. The device commands below need the matching
Frida server and an installed target. Direct CLI smoke also needs the matching
Frida CLI on PATH. See [API33 preparation and replay](../test/api33/README.md).

### Step 1: verify CLI path

```bash
frida -q -Uf com.android.settings -l hookforge/test/simple_smoke.js
```

If this fails, the problem is not specific to the Node host.

### Step 2: verify Node host transport path

```bash
npm --prefix hookforge run test:node:smoke:settings:spawn
npm --prefix hookforge run test:node:smoke:settings:frontmost
```

If these fail before loader logs appear, check Frida client/server compatibility
and host-side attach or spawn flow first.

### Step 3: verify Java bridge exposure in Node

```bash
npm --prefix hookforge run test:node:probe:java
```

This reports global Java exposure in a bare Node-created Script. Compare it
with the facade-backed Node smoke in Step 2. For failures after VM availability,
inspect `initializeJavaClassLoader()` and writable App cache paths. For
replacement-method failures, verify the reviewed bridge patch.

### Step 4: verify shared API behavior

```bash
npm --prefix hookforge run case:rootbeer -- \
  --apk /absolute/path/rootbeer-sample-0.9.apk --serial emulator-5554 --reuse-detect-env \
  --agent /absolute/path/DroidLab/hookforge/dist/android-api.js \
  --production-manifest /absolute/path/DroidLab/.runtime/hookforge/production-manifest.json \
  --output /absolute/path/DroidLab/.runtime/frida/rootbeer-regression
```

Use absolute paths and a new output directory. The case requires its fixed
RootBeer APK and an installed detect-env App for reuse; see the
[RootBeer case guide](../cases/rootbeer/README.md).

This observes actual APK callers and verifies configured positive/negative
Java file and libc `fopen` behavior through the original RootBeer and detect-env
interfaces, including restoration. Native export lookup or a private self-probe
alone does not establish that the shared hook affects an APK.

## Operational Guidance For Agents

When editing Android-focused Frida modules in this workspace:

- do not assume `globalThis.Java` exists
- do not gate Android support on raw `typeof Java !== 'undefined'`
- use `packages/runtime/java-bridge.ts`; existing core imports re-export it
- prefer `performOnJava()` over ad-hoc Java scheduling
- treat CLI success and Node host success as separate validation layers

## Known Runtime Notes

- `frontmost attach` is a better first smoke target for Java-heavy debugging
  when early `spawn` timing is noisy
- `com.android.settings` is the preferred first Android Node smoke target
- `:labs:detect-env` is more timing-sensitive and should not be the first
  target used to debug host or bridge regressions
- native API shape may drift separately from Java bridge behavior, so treat
  native hook regressions as a separate verification path
- SIM fallback values from system properties are lower trust than
  TelephonyManager results

## If Frida 18+ Breaks Again

Repeat the validation sequence above using the selected dependency lock and
matching server. Locate failures in transport, bridge exposure, App loader/cache
initialization, ART replacement setup or native export lookup before editing
individual hooks. Keep version-specific changes in the corresponding runtime
owner and rerun the affected target behavior and restoration checks.
