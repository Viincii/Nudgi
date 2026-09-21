# 1. Multi-module project with convention plugins

## Context

Nudgi will grow several surfaces that share the same mascot and data: the app, home screen widgets, a blocking overlay
and background workers. Splitting the code late is more painful than starting split.

## Decision

- Use several Gradle modules from the start: `app`, `core:*` (shared building blocks) and `feature:*` (one per screen or
  capability).
- Put the shared Gradle configuration in convention plugins under `build-logic/`, so a module's `build.gradle.kts`
  only states what is specific to it.
- Keep every dependency version in `gradle/libs.versions.toml`.

## Consequences

- The mascot can be reused by widgets and overlays without depending on a screen.
- Module boundaries force explicit dependencies; `core` modules never depend on `feature` modules.
- A little more build configuration than a single module, paid once in `build-logic/`.
