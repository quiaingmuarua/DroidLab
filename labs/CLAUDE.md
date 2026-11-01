# Crackme Agent README

This document is the entry point for work under `crackme/`.

Read this first before editing any crackme-related module.

Primary goal for future agents:

- enable fast "vibe coding" for crackme attack / defense experiments
- keep module choice predictable
- let the user focus on acceptance instead of step-by-step implementation guidance

## 1. Scope

`crackme/` currently contains three modules:

1. `detect-env`
   Environment detection, risk inspection, fingerprint collection, and privacy-related collection demos.

2. `android-lab`
   Android framework, networking, crypto, IPC, and third-party library demos.

3. `crackme-native-lib`
   Shared native capabilities used by crackme modules.

## 2. Fast Decision Guide

Choose the module by intent first, not by implementation language.

If the task is about:

- root / xposed / frida / runtime risk checks
  start in `detect-env`

- framework APIs / binder / grpc / thrift / okhttp / cronet / crypto demos
  start in `android-lab`

- C / C++ detection logic / syscall helpers / ELF loading / reusable native code
  start in `crackme-native-lib`

## 3. Experiment Classification Rules

Use these labels mentally before writing code:

1. `detect-point`
   A signal, check, fingerprint, or suspicious-state collector.
   Default home: `detect-env`

2. `capability-demo`
   Learn or demonstrate how a framework API or third-party library works normally.
   Default home: `android-lab`

3. `attack-surface-demo`
   A lab intentionally designed to be hooked, traced, bypassed, or tampered with.
   Default home: `android-lab`

4. `native-capability`
   Reusable low-level capability that may be called from one or more crackme apps.
   Default home: `crackme-native-lib`

5. `hook-side-lab`
   Xposed-side or native-hook-side experiment targeting crackme demo apps.
   Default home: `../xposed/crackme-lab`

6. `frida-side-lab`
   Frida-side injection, spoofing, tracing, or bypass experiment targeting crackme demo apps.
   Default home: `../frida`

If you are unsure between `detect-env` and `android-lab`, ask:

- "Am I evaluating the environment or identity of the device / runtime?"
  If yes, use `detect-env`.

- "Am I learning or demonstrating a platform / library capability?"
  If yes, use `android-lab`.

## 4. Required Deliverables For New Experiments

By default, a crackme experiment is not complete unless it includes most of these:

1. runnable implementation
2. visible entry point
   activity, list item, or hook entry
3. observable output
   text, JSON, logs, or UI state that makes the result inspectable
4. at least one attack surface
   hook point, tamper point, bypass idea, or trace point
5. brief verification
   usually module assemble plus any task-specific sanity check

Do not stop at helper code unless the user explicitly asked for helper code only.

## 5. Runtime Relationship

- `detect-env` depends on `crackme-native-lib`
- `android-lab` is currently mostly app-layer demo code
- `crackme-native-lib` should provide reusable native capabilities, not UI flow
- `xposed/crackme-lab` owns crackme-targeted Xposed-side and native-hook-side work
- `frida/` owns crackme-targeted Frida-side instrumentation, target configs, and host launch tooling

## 6. Safe Change Rules

1. Prefer the smallest module-local change that matches the existing ownership.
2. Keep UI wiring in app modules and reusable low-level logic in `crackme-native-lib`.
3. If adding a new capability, decide first whether it is:
   - Java-only
   - native-only
   - hybrid Java + native
4. Do not move code across modules unless the reuse boundary is clear.

## 7. Placement Rules That Agents Should Follow

Put code in `detect-env` when:

- the result is a check, fingerprint, or suspicious-state snapshot
- the user wants to compare before / after hook behavior
- the "normal" output is itself the learning artifact

Put code in `android-lab` when:

- the user wants to understand framework or library behavior
- the task is a demo first and a bypass target second
- the task needs a UI or menu entry for exploratory learning

Put code in `crackme-native-lib` when:

- the implementation should be reusable by more than one crackme app
- the logic is inherently low-level
- the value is in the capability, not the screen flow

Put code in `xposed/crackme-lab` when:

- the work is on the hook side, not the app side
- the task is about Xposed behavior against crackme apps
- the task is about crackme-targeted native hook loading or native hook entry

Put code in `../frida` when:

- the work is on the Frida injection side, not the app side
- the task is about Frida scripts, runtime bypass, tracing, or spoofing against crackme apps
- the task needs target JSON config or host-side attach or spawn flow

## 8. Existing App Capabilities

Apps own their detection logic, full-name `extension.worker.<source>.*` events
and `scenario-catalog` contents. Existing consumers call the capability HTTP
SDK through `/events` and `/events/{eventCode}:call`, or use configured Worker
Delivery. Keep each catalog consistent with its actual App event definitions.

A new external APK experiment does not need a lab App, catalog entry or generic
scenario lifecycle. Put its analysis and hooks under `frida/apps/<sample>/`,
run the actual target and deliver the requested effect for user acceptance.

## 9. Recommended Reading Order

1. `detect-env/CLAUDE.md` for detection and fingerprint tasks
2. `android-lab/CLAUDE.md` for framework and library demo tasks
3. `crackme-native-lib/CLAUDE.md` for C/C++ and shared low-level capability work
4. `../xposed/crackme-lab/CLAUDE.md` for hook-side crackme work
5. `../frida/CLAUDE.md` for Frida-side crackme work

## 10. Common Failure Modes

- A detection feature looks like app logic but the real implementation is in `crackme-native-lib`
- A framework demo change accidentally expands into a cross-module refactor
- UI list entries are updated without updating the actual feature activity or implementation
- Native changes are made without checking the owning `CMakeLists.txt`
- A task that should live in `xposed/crackme-lab` is incorrectly pushed into the app module
- A task that should live in `../frida` is incorrectly pushed into `detect-env`, `android-lab`, or `xposed/*`
- A capability demo is mislabeled as a detection feature and ends up in `detect-env`
