# Nudgi

Android companion app: an animated mascot ("Nudgi") that observes phone usage, sends nudges against doom-scrolling and
inactivity, and later blocks apps with progressive friction. Personal project, open source, portfolio-oriented: the
git history, this file and `docs/decisions/` are part of what the project shows.

## Non-negotiable constraints

- **Everything stays on the device.** No backend, no cloud AI, no network dependency, no telemetry. The manifest must
  not declare `INTERNET`, and `allowBackup` stays `false` (see `docs/decisions/0003`).
- **Sideloaded APK**, not the Play Store, so `AccessibilityService` use is not constrained by store policy.
- **The rule-based V1 is a real product and the data-collection phase for a later on-device model** (contextual bandit:
  Thompson sampling or LinUCB). Design the `events` schema so context, proposed action and outcome can be
  reconstructed later. Reward design and cold start are the hard problems; keep them in mind.
- No cloud LLM as an intermediate step.
- Dev device: Nothing Phone (1), Android 15.

## Stack

Kotlin, Jetpack Compose (Material 3), Hilt, Room (SQLite), WorkManager, Glance (widgets), Health Connect (watch, much
later). `UsageStatsManager` is read by **periodic polling**, not by logging every foreground/background event.

Versions live in `gradle/libs.versions.toml`. SDK levels: minSdk 31, targetSdk 35, compileSdk 37 (see
`docs/decisions/0005`).

## Modules

| Module | Role | May depend on |
|---|---|---|
| `app` | Entry point, `@HiltAndroidApp`, wiring, the periodic usage pipeline | everything |
| `core:designsystem` | Theme (`NudgiTheme`), shared UI | nothing |
| `core:mascot` | `NudgiMascot`, `MascotMood`, `MascotFace` | nothing |
| `core:database` | Room database (`events`, `daily_stats`) | nothing |
| `core:accessibility` | `NudgiAccessibilityService` (foreground app changes), permission check | nothing |
| `core:usagestats` | `UsageStatsManager` polling into `events`, aggregated into `daily_stats` | `core:database` |
| `core:nudge` | Rule-based nudges: rules, notification, nudge events | `core:database`, `core:usagestats` |
| `feature:*` | One screen or capability each | `core:*`, never another feature |
| `build-logic` | Convention plugins (`nudgi.android.*`) | n/a |

`core` modules never depend on `feature` modules. New modules use the convention plugins from `build-logic` and are
registered in `settings.gradle.kts`. Prefer `alias(libs.plugins.nudgi.android.feature)` for a feature module.

Package root: `com.vincentmignot.nudgi`, with `.core.<name>` and `.feature.<name>` sub-packages.

## Data model (planned)

Two layers in Room:

1. `events`: raw, append-only. `id, timestamp, event_type, package_name, duration_ms, metadata (JSON)`.
2. `daily_stats`: aggregates, refreshed by WorkManager.

Exported Room schemas go in `core/database/schemas/` and are committed.

## Commands

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew spotlessCheck        # spotlessApply to fix
./gradlew assembleRelease -PwarningsAsErrors=true
```

CI (`.github/workflows/ci.yml`) runs spotlessCheck, lintDebug, testDebugUnitTest and assembleDebug with
`-PwarningsAsErrors=true`. Run the same locally before pushing.

## Conventions

- Code, comments, commit messages and docs are written in **English**.
- **Small atomic commits** with explicit messages (Conventional Commits style: `feat:`, `fix:`, `chore:`, `docs:`,
  `test:`, `refactor:`, `ci:`). The initial setup went directly on `main`; from then on, work on branches.
- Unit tests for rule logic and aggregation. Pure logic stays free of Android types so it runs on the JVM.
- Log notable architectural decisions in `docs/decisions/NNNN-title.md` (context, decision, consequences).
- Comments explain constraints or non-obvious reasoning, never tickets or task references.
- UI state flows through a `ViewModel` exposing `StateFlow<UiState>`; composables are stateless where possible and take
  state plus callbacks. Debug-only UI is gated on `BuildConfig.DEBUG`.
- Ktlint rules are in `.editorconfig`; do not fight the formatter, run `spotlessApply`.

## Mascot

Drawn in code with Compose primitives (`docs/decisions/0002`): flat blob, pill-shaped eyes, tiny antenna, no mouth. A
`MascotMood` maps to continuous `MascotFace` parameters that are spring-animated. Do not copy code or assets from AGPL
projects that inspired the style. Glance widgets cannot host a Compose `Canvas`, so widgets need per-mood bitmaps
rendered from the same drawing.

## Roadmap

1. Mascot + skeleton (done)
2. Usage tracking + rule-based logic (data-collection phase)
3. Home screen widget
4. App blocking with progressive friction: soft friction, warning overlay, forced close as a last resort
5. Lock screen widget (deferred: the Nothing Phone (1) lacks the Android 16 QPR1 lock screen widget API)
6. On-device contextual bandit
7. Watch connectivity via Health Connect
8. Deeper RL, only if justified

## Next up

- Sideload a debug build onto the Nothing Phone (1) to start collecting real usage and nudge data.
- Show the mascot's mood and today's nudges on the home screen, from `daily_stats` and the nudge events.
