# 4. ktlint through Spotless for code style

## Context

The CI needs a style check. detekt 1.x is compiled against an older Kotlin than the one used here, and its 2.x line is
still in alpha. ktlint has no dependency on the project's Kotlin compiler version.

## Decision

Use Spotless with ktlint (`./gradlew spotlessCheck` in CI, `./gradlew spotlessApply` locally). Rules live in
`.editorconfig`. Android lint and Kotlin warnings-as-errors (`-PwarningsAsErrors=true`) cover the rest.

## Consequences

- One command formats the whole codebase, which keeps commits free of style noise.
- No static-analysis rules beyond style and compiler warnings; revisit detekt once 2.x is stable.
