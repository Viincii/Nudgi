# 14. Local data export

## Context

Everything Nudgi records lives in one Room database on the phone, and `allowBackup` is `false` (see
[0003](0003-on-device-only-and-no-cloud-backup.md)). Uninstalling the app, or an update Android refuses because
the signing key changed, wipes weeks of usage and nudge history. That history is the dataset the on-device bandit
will be trained on, and later the data its policies are evaluated against offline: the holdout of
[0010](0010-rule-based-nudges.md) logs a propensity for every decision. 0003 already allowed for an explicit local
export.

## Decision

- **A "Settings" screen, reached from the home screen, with an "Export my data" button**, in release builds too:
  keeping a copy of one's own data is a feature, not a debug tool. It lives in `feature:settings`, which later
  settings can join.
- **The file goes through the Storage Access Framework** (`ACTION_CREATE_DOCUMENT`). No storage permission, no
  network, and the user picks the destination. The suggested name is `nudgi-export-yyyyMMdd-HHmm.zip`.
- **One zip, one file per table, in formats a notebook reads directly**, written by `core:export`:
  - `events.jsonl`: one event per line, with `metadata` inlined as a JSON object rather than an escaped string, so
    it flattens with `pandas.json_normalize`. Metadata that is not valid JSON is kept as a string instead of
    failing the export.
  - `daily_stats.csv`.
  - `manifest.json`: an export format version, the Room schema version (matching `core/database/schemas/`), the
    app version, the export time and the device time zone. Timestamps are epoch milliseconds; the zone turns them
    back into the local hours the rules saw.
- **A consistent snapshot without loading everything**: events are read in pages of 1,000 by id, up to the max id
  seen when the export starts. Rows the pipeline appends meanwhile are left for the next export.
- **A failed or cancelled export deletes its partial file**, since a truncated zip passes for a valid export until
  a script reads it.
- **No import yet.** Restoring means merging with rows recorded since, deduplicating and migrating across schema
  versions. Exports keep the data safe in the meantime, and the manifest carries what an import will need.

## Consequences

- An export is a complete copy of sensitive data outside the app's sandbox. Where it goes is the user's choice, and
  `PRIVACY.md` says so. Real exports must never be committed to this repository; `*.zip` is already ignored, and
  tests and examples use synthetic data.
- The export runs in the settings screen's `ViewModel` scope: leaving the screen mid-export cancels it and removes
  the partial file. Fine at today's volumes; a long export would move to WorkManager.
- The export files are a second contract next to the metadata keys of 0010: changing their layout bumps
  `EXPORT_FORMAT_VERSION`.
- For training and validation, split exports by time (train on the earlier weeks, validate on the later ones),
  not at random, or the evaluation sees the future.
