# 3. On-device only, no network permission, no cloud backup

## Context

Nudgi observes app usage, which is sensitive. Trust is the product, and the project is open source.

## Decision

- The manifest declares no `INTERNET` permission, so the app cannot transmit data even by mistake.
- `android:allowBackup` is `false`: the local database is never uploaded to a Google account.
- No analytics, crash reporting or third-party SDK that phones home.

## Consequences

- Data does not follow the user to a new phone automatically. An explicit local export can be added later if needed.
- Adding any network capability later requires a new decision here and an update to `PRIVACY.md`.
