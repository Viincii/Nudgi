# 15. Shared debug signing key

## Context

Nudgi is sideloaded (see `CLAUDE.md`), and debug APKs come from two places: `./gradlew installDebug` on a
development machine, and the `nudgi-debug-apk` artifact that CI uploads on every run. The Android Gradle plugin signs
debug builds with `~/.android/debug.keystore`, generated on first use on each machine. Every CI runner starts clean,
so every CI APK had its own signature, and Android refuses to update an app with a different one
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). The only way forward was to uninstall, which wipes the database
(`allowBackup` is `false`, see [0003](0003-on-device-only-and-no-cloud-backup.md)).

## Decision

`app/debug.keystore` is committed, with the conventional debug credentials (`android` / `androiddebugkey`), and the
`debug` signing config points to it. `.gitignore` keeps ignoring every other keystore.

## Consequences

- Any debug APK, from CI or from any machine, installs over any other and keeps the data.
- The key is public, so a debug build proves nothing about who built it. That is acceptable for a debuggable
  build that anyone can already rebuild from source. Release builds do not use it; a release key, when one exists,
  stays out of the repository.
- Moving an existing install to this key needs one last uninstall. A debug install's database can be copied out
  and back with `adb exec-out run-as com.vincentmignot.nudgi` around it, or exported first (see
  [0014](0014-local-data-export.md)).
