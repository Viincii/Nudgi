# 5. Toolchain and SDK levels

## Context

The development device is a Nothing Phone (1) on Android 15 with no more major updates. The current AndroidX libraries
require compiling against a newer API level than the one the app targets.

## Decision

- `minSdk` 31 (Android 12): no legacy handling, and the splash screen API and adaptive icons work out of the box.
- `targetSdk` 35 (Android 15), matching the device the behavior is tested on.
- `compileSdk` 37, required by the current AndroidX releases. It only unlocks newer APIs; it does not change runtime
  behavior.
- Latest stable AGP, Gradle, Kotlin and AndroidX at project start, with versions pinned in the version catalog.

## Consequences

- Android Studio must be recent enough to open the project (the build itself only needs JDK 21 and the SDK).
- Raising `targetSdk` is a deliberate, separate step that requires checking the new runtime behavior changes.
