# Privacy

Nudgi is built around one rule: **your data never leaves your phone.**

## What stays on the device

Everything Nudgi observes or computes is stored in a local database on your phone:

- which apps you use and for how long (usage statistics),
- the notifications Nudgi sent and how you reacted to them,
- daily aggregates derived from the above.

None of it is transmitted anywhere.

## Exporting your data

In Settings, "Export my data" writes everything above to a zip file, in a location you pick with the system file
picker. Nudgi never sends that file anywhere, but once it is saved it is outside the app: if you pick a synced
folder, it is synced like any other file there.

## What Nudgi does not do

- **No network access.** The app does not declare the `INTERNET` permission, so it cannot send data even by accident.
- **No cloud AI.** Any model that ever runs in Nudgi will run on the device.
- **No analytics, telemetry or crash reporting.** No third-party SDK is included.
- **No cloud backup.** Android's automatic backup is disabled, so the local database is not uploaded to your Google account.
- **No account, no sign-in.**

## Permissions

Nudgi will ask for the following permissions as features arrive. Each one is granted by you in the system settings.

| Permission | Why | Status |
|---|---|---|
| Usage access | Read which apps are in use and for how long, to spot doom-scrolling | Planned |
| Notifications | Show Nudgi's reminders | Planned |
| Accessibility service | Detect the foreground app in order to gradually block chosen apps | Planned |

Nudgi is distributed as an APK from GitHub rather than the Play Store, so you can inspect the source and build it yourself.

## Deleting your data

Uninstalling the app deletes its local database. There is no server-side copy to erase. Exports you saved are
yours to delete.

## Changes

If this policy ever changes, the change will appear in this file's git history.
