# 24. Local data import

## Context

[0014](0014-local-data-export.md) gave Nudgi an export and left the import for later: an export keeps the data
safe, but nothing puts it back. Since `rules_v5` and the shadow `lin_ts_v2` of [0023](0023-rules-explore-friction-levels.md)
started collecting exploration data, every week of decisions is worth more, and a reinstall (a signing key change,
a phone change, a cleared app) still starts from an empty database. `allowBackup` stays `false` ([0003](0003-on-device-only-and-no-cloud-backup.md)),
so the export is the only way back.

What the import has to work with:

- **Links between events do not use row ids.** A decision, its response and its outcome share a `nudge_id` UUID in
  their metadata. Nothing else refers to `events.id`; queries order by `timestamp, id`, and the export pages by id.
- **`daily_stats` is derived.** `DailyStatsAggregator` recomputes every day touched since an instant from the raw
  events, and running it twice is harmless.
- **The shadow bandit has no stored state.** It replays `events.since(0)` at each decision, so its posterior comes
  back with the events.
- **The database is rarely empty when an import is possible.** Onboarding has to grant usage access before the app
  is usable, and the first poll then records the last 15 minutes. On the phone that made the export, those rows can
  be identical to the export's last rows.
- **There is one schema version so far** (`NUDGI_DATABASE_VERSION = 1`) and one export format (`EXPORT_FORMAT_VERSION
  = 1`).

## Options

### Replace or merge

- **A. Replace.** Delete every event and daily stat, then insert the export with its own ids. The result is exactly
  the export. Whatever was recorded since install is lost, and importing an old export on the live phone by mistake
  loses everything since that export: the button needs a confirmation that says so.
- **B. Merge.** Add the export's events to the existing ones, skipping those already present. Nothing is ever
  lost, and importing the same file twice changes nothing. It needs a definition of "already present".
- **C. Empty database only.** Refuse unless `events` is empty. Simplest and safest, but in practice never possible
  after onboarding (see above), so it would need a reset first, which is option A with an extra step.

### Duplicates and ids (for B)

- **Identity is the whole row except its id**: `timestamp, event_type, package_name, duration_ms, metadata`. Nudge
  events carry a UUID, so two distinct ones never collide; two `app_foreground` rows with the same package and the
  same millisecond are the same system event. Existing rows in the export's time range are loaded once into a set
  of keys (tens of thousands of rows at most, a few MB), and the import skips the matches.
- **Ids are reassigned**: imported rows are inserted in the export's id order and get new autoincrement ids. Into
  an empty database (after clearing the app's data) they get the same ids as in the export. Into a database with
  newer rows they come after them, so ids stop following time, which nothing relies on. Keeping the export's ids
  would collide with rows recorded since install.
- A unique index on those columns would let SQLite do the deduplication, but it needs a schema migration and an
  index over every `metadata` string, for a check that runs only on import.

### `daily_stats`

- **D. Recompute** with `DailyStatsAggregator` from the earliest imported timestamp. Consistent with the events by
  construction, including on a merge where a day has rows from both sides. The CSV is only checked against the
  manifest's count. Days are cut in the importing phone's time zone, as they are for new data.
- **E. Upsert the CSV.** Restores the exact rows, but on a merge a day shared by both sides keeps one side's total
  until the next aggregation touches it.

## Decision

- **B, merge, with the identity above and reassigned ids; D, recompute `daily_stats`.**
- **An "Import data" button in Settings**, next to the export, in release builds too, through the Storage Access
  Framework (`ACTION_OPEN_DOCUMENT`, `application/zip`). No permission, no network.
- **Validate everything before writing anything.** A first pass reads the manifest and streams both files, checking
  that `format_version` is one this build reads, that `database_schema_version` is not newer than the database, that
  every line parses and that the counts match the manifest (a truncated zip fails here). The second pass inserts the
  events in one Room transaction, in pages, so a failure halfway leaves the database as it was.
- **An older `database_schema_version` is accepted** once a reader for it exists. With only version 1 today, that
  means equality; a later schema change adds a conversion from the previous export layout to the new entities,
  next to its Room migration.
- **Metadata goes back as it came in**: a JSON object is re-encoded as a string, and a metadata the export kept as a
  JSON string (because it was not valid JSON) is restored verbatim.
- The result screen says how many events were added and how many were already there.

## Consequences

- The import reads a file the user picked, which could come from anywhere. It only ever inserts rows through Room,
  validates types before writing, and caps nothing else: a crafted file can fill the database with fake events, which
  only the user can do to themselves.
- A decision exported before its outcome window closed (the last 11 minutes before the export) gets its outcome
  computed on the importing phone, from events that may miss the gap between export and import. Such outcomes are
  wrong in the direction of "did not leave". Export a few minutes after the last nudge to avoid it; the import does
  not try to detect it.
- The import runs in the settings screen's `ViewModel` scope, like the export. Leaving the screen mid-import cancels
  the transaction, so nothing is half-written.
- Changing the export layout now means keeping a reader for the old layout, or exports written before the change
  can no longer be restored.
