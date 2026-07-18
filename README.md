# MedTracker

An Android app for tracking medication schedules — dosing (in tablets/units or active-substance strength), tapering/titration across multiple time-bound periods per drug, stock-on-hand tracking, and reminders.

## Features

### Drugs & supplies
Add a drug with a form (tablet, capsule, drops, ml, or a custom text form). Track one or more stock batches per drug — each batch has its own quantity, strength (value + unit: mg/mcg/IU), and purchase date. Supply rows support a quick "+" action to add newly purchased quantity onto an existing batch without creating a duplicate batch row.

### Periods (schedules)
Each drug can have multiple, possibly overlapping, time-bound periods:
- **Start**: a custom date, or "continue previous" (starts the day after the drug's most recently ended period).
- **End**: a fixed date, a duration in days, or open-ended ("ongoing").
- **Cycle**: daily, every other day, specific days of the week, days-on/days-off (e.g. 3 days on, 4 days off), or a custom free-text description (treated as daily for scheduling purposes).
- **Times of day**: any number of times, each with its own dose — specified either in units (tablets) or in active-substance strength, converted using the drug's most-recently-added stock batch.

### Home
Shows the day's scheduled intakes (previous/today/next-day navigation) with live status: upcoming, overdue (past scheduled time + grace period), taken, skipped, or missed (a past occurrence with no recorded action). "Took it" / "Skipped" quick actions log the occurrence immediately.

### History
Day-grouped log of every taken/skipped occurrence, filterable by drug, with manual add/edit/delete for retroactive entries (distinguished from reminder-driven entries by source).

### Stock projection & dose combos
For each period, projects how much stock is left at the period's start and end by simulating day-by-day consumption from today. Also suggests which physical tablets/capsules on hand combine (in 0.5-unit steps, across up to 4 distinct strengths) to hit a target strength dose exactly, ranked by fewest total pieces.

### Notifications
- **Medication reminders**: exact alarms for the next few days, rescheduled automatically whenever a period/time changes, after a device reboot, and via a periodic background refresh. Each notification has Took it / Skipped / Remind later actions; snooze duration is configurable.
- **Low-stock reminders**: a per-batch, optional "remind me N days before it runs out" alert based on the same stock projection.

### Backup & restore (Google Drive)
Connect a Google account (Drive `appdata` scope only) to back up all data as a single JSON file in the app's private Drive folder, and restore it later. Restore is a full wipe-and-replace of local data, followed by re-arming all notifications.

### Language
English, Українська, Русский, and Čeština, switchable in-app (per-app language override) independent of the system language.

## Requirements

- Android Studio with AGP 9.3.0 / Gradle
- JDK 11+
- Android SDK: `minSdk 26`, `targetSdk 37`, `compileSdk 37`
- A physical or emulated device running API 26+

## Configuration

Google Drive backup requires a one-time Google Cloud Console setup (see the doc comment in `DriveAuthManager.kt` for exact steps): enable the Drive API, configure the OAuth consent screen (add your own account as a test user while the `drive.appdata` scope is unverified), and create an Android OAuth client ID matching this app's `applicationId` (`app.zelgray.pills_in_time`) and your signing key's SHA-1 (`./gradlew signingReport`). Until that client exists for the exact package + SHA-1 you're building with, the "Connect Google account" flow will fail with an authorization error — everything else in the app works without it.

## Running

```
./gradlew installDebug
```

or open the project in Android Studio and run the `app` configuration on a device/emulator with API 26+.

## Testing

```
./gradlew test            # unit tests (domain use cases, util)
./gradlew connectedCheck  # instrumented tests (Hilt-backed DB, happy-path UI flow)
```

## License

MIT — see [LICENSE](LICENSE).
