# 7. Guided permission onboarding

## Context

Usage access and accessibility are both special permissions with no runtime dialog: the user has to be sent to
system settings and come back. The app had no navigation yet — `MainActivity` rendered `HomeRoute` directly.
Both permissions are needed for the data-collection phase (usage access for the `UsageStatsManager` polling
next up) and for app blocking later (roadmap step 4, accessibility), so both are requested up front rather than
staggering the ask across two later features.

## Decision

- New `feature:onboarding` module: a two-permission screen (usage access, accessibility), each with an
  "Open settings" button and a live granted/not-granted state that re-checks on `ON_RESUME` (the only way to
  know the user came back from settings). Once both are granted it calls back to navigate away; there's no
  separate "Continue" button to tap.
- New `core:accessibility` module: a stub `NudgiAccessibilityService` (declared, does nothing yet) plus
  `isAccessibilityServiceEnabled()`. It lives in `core:*` rather than `app` or `feature:onboarding` because
  it's a device capability other things will depend on later (the app-blocking feature), and a `core` module
  keeps that dependency direction valid without `feature:onboarding` needing to know about `app`'s classes.
  Its `AndroidManifest.xml` (library manifests merge into the app's) declares the service; the config XML sets
  `canRetrieveWindowContent="false"` since nothing reads screen content yet.
- Usage access's check (`AppOpsManager` op check) stays in `feature:onboarding` for now — nothing else needs it
  until the polling work, which can hoist it into a shared module then if it turns out to.
- First real navigation in the app: `androidx.navigation:navigation-compose`, added to the `nudgi.android.feature`
  convention plugin so every feature module can expose its own `NavGraphBuilder` extension
  (`onboardingScreen()`, `homeScreen()`) and route constant, instead of `app` reaching into feature internals.
  `MainActivity` picks the start destination once, synchronously, from `hasCompletedOnboarding()` — no splash
  or loading state, since both checks are cheap local reads (`AppOpsManager`, `Settings.Secure`).
- DAO-style permission-check tests run under Robolectric on the JVM, same approach as `core:database`.

## Consequences

- `PACKAGE_USAGE_STATS` (granted from settings, not a runtime permission) is declared in
  `feature:onboarding`'s manifest; `android.permission.BIND_ACCESSIBILITY_SERVICE` and the service itself in
  `core:accessibility`'s.
- Verified on-device (Android 15 emulator): granting a permission in settings and returning to the app updates
  the screen without restarting it; once both are granted, it navigates to Home on its own; a cold start with
  both already granted skips onboarding entirely. Note: Android disables an app's accessibility service when
  the app is *force-stopped* (not on ordinary process death) — expected platform behavior, not a bug here.
- `NudgiAccessibilityService` still does nothing; its real event handling is roadmap step 4's job.
