# Native Lib Agent README

This document explains how to work in `labs:crackme-native-lib`.

## 1. Module Goal

`crackme-native-lib` provides shared native capabilities for crackme-related modules.

It is not a UI module.

## 2. Current Native Capability Groups

- `asm`
  inline syscall and arch-specific helpers

- `detect`
  anti-root, anti-frida, and related detection logic

- `crypto`
  native crypto demos

- `elf-loader`
  ELF parsing, relocation, loading, and related support code

- `fingerprint`
  native fingerprint-related helpers

- `playground`
  small self-contained native practice code and experiments

## 3. Change Decision Rules

Use this module when:

- the logic must run in C / C++
- the capability should be reusable by more than one crackme app
- the behavior is fundamentally low-level

Do not use this module for:

- activity navigation
- menu wiring
- app-specific UI flow
- one-off demo glue unless it is expected to be reused

Default heuristic:

- if only one crackme screen needs it right now, start in the app module
- if two experiments would reasonably want the same low-level behavior, move or build it here

## 4. How To Modify Safely

Before editing native code:

1. identify the owning submodule
2. open the related `CMakeLists.txt`
3. confirm whether headers, ABI-specific files, or Java bindings also need updates

If the task touches Java + native:

1. change the native implementation first
2. then update the calling Java side
3. verify the owning app module still builds

When a native capability is loaded from Java:

1. check the CMake target name
2. check the Java `System.loadLibrary(...)` name
3. check any JNI exported symbol names that encode the Java package/class path

Do not change only one of these three.

## 5. High-Risk Areas

- `asm`
  ABI-specific behavior can break only one architecture and still look fine elsewhere

- `elf-loader`
  broad refactors here are risky because loader, relocation, and memory assumptions are tightly coupled

- `detect`
  detection behavior may vary across Android versions, SELinux state, and vendor ROMs

## 6. Practical Rules For Agents

1. prefer minimal changes within one native capability group
2. avoid renaming headers or moving files unless `CMakeLists.txt` is updated in the same change
3. do not assume app-layer bugs are native-layer bugs
4. if the issue can be fixed in the app module without duplicating native logic, prefer the app-layer fix

## 7. First Files To Open

- `src/main/cpp/CMakeLists.txt`
- `src/main/cpp/detect/CMakeLists.txt`
- `src/main/cpp/asm/CMakeLists.txt`
- `src/main/cpp/elf-loader/CMakeLists.txt`

If the task is about native fingerprint helpers or practice code, also open:

- `src/main/cpp/fingerprint/CMakeLists.txt`
- `src/main/cpp/playground/CMakeLists.txt`
