# MedTracker

![MedTracker icon](pills-in-time.png)

An Android app for tracking medication schedules — dosing (in tablets/units or active-substance strength), tapering/titration across multiple time-bound periods per drug, stock-on-hand tracking, and reminders. Tracks one or more people from a single install.

## Features

### Patients
Every drug belongs to a patient — useful if you're tracking your own medications alongside, say, a parent's or a child's. A single default patient exists from the start, renameable to taste; add more from the switcher in any tab's top bar (a color-dot-and-name button, top right), which also lets you rename, recolor, or delete a patient (the last remaining one can't be deleted). Switching patients re-scopes Home, Drugs, and History to that patient's own drugs. The app's whole color theme follows the current patient's chosen accent color, and reminder/low-stock notifications carry that same color — plus the patient's name in the title once there's more than one — so it's clear at a glance whose data or reminder you're looking at.

### Drugs & supplies
Add a drug with a form (tablet, capsule, drops, ml, ampoule, sachet, or a custom text form) — this drives how quantities are labeled everywhere ("3 sachets", "12 drops", etc.), fully localized (English/Ukrainian/Russian/Czech, including proper plural forms).

Track one or more stock batches ("supplies") per drug — each batch has its own **quantity**, **strength** (value + unit: mg/mcg/IU), and purchase date (`addedAt`, used to tell which batch is the "current"/most-recently-bought one). Strength is **optional**: leave it blank if you don't need dose-strength tracking for this drug. A drug with no strength is capped at a single supply, though — strength is what would justify (and let the app tell apart) more than one batch on hand at once. Supply rows support a quick **"+"** action to add newly purchased quantity onto an existing batch without creating a duplicate batch row (so partial refills of the same purchase don't fragment your stock history).

### Periods (schedules)
Each drug can have multiple, possibly overlapping, time-bound periods:

- **Start**: a custom date, or "continue previous" (starts the day after the drug's most recently ended period).
- **End** — four ways to bound (or not bound) a period:
  - **Fixed date** — pick an explicit end date.
  - **N days** — a literal calendar-day span (start + N − 1 days). For a daily cycle this is the same as "N doses"; for anything less frequent it isn't (see below).
  - **N occurrences** — instead of a calendar span, ends after N actual active-cycle days have occurred. Only offered for cycles that aren't active every day (everything except Daily/Custom), since that's where it actually differs from "N days": e.g. a once-a-week cycle with "8 occurrences" spans about 8 weeks, not 8 calendar days.
  - **Ongoing** — open-ended, no end date.
- **Cycle** — how often within the period a dose is due:
  - **Daily** — every day.
  - **Every other day** — alternating, anchored to the period's own start date.
  - **Specific days of the week** — pick one or more weekdays.
  - **Days-on/days-off** — e.g. "3 days on, 4 days off", repeating from the period's start.
  - **Custom** — a free-text description for your own reference; scheduling-wise it behaves like Daily.
- **Times of day**: any number of times, each with its own dose — specified either as a plain **unit count** (e.g. "1 tablet") or as **active-substance strength** (e.g. "20 mg"). For a strength dose that could be made up of more than one combination of on-hand strengths (e.g. two 10 mg tablets vs. one 20 mg), you pick which combination it's fixed to right here, once — if there's only one possible combination, it's picked automatically and there's nothing to choose. That choice is what later drives real stock deduction (see below) — logging a dose never re-asks which tablets it came from. If the drug has more than one supply on hand and at least one time uses a plain unit count, you can similarly pin the whole period's consumption to one specific supply instead of drawing first-in-first-out across all of them.

### Home
Shows the day's scheduled intakes (previous/today/next-day navigation, also reachable by swiping or tapping the date to open a calendar) with live status: upcoming, overdue (past scheduled time + grace period), taken, skipped, or missed (a past occurrence with no recorded action). "Took it" / "Skipped" quick actions log the occurrence immediately; re-opening an already-logged occurrence offers an "Undo" instead. The date-picker calendar marks each day at a glance — a filled green circle for a day where every scheduled dose was taken, a gray outline for a future day with a dose scheduled, and nothing for empty or incomplete days.

### History
Day-grouped log of every taken/skipped occurrence, filterable by drug, with manual add/edit/delete for retroactive entries (distinguished from reminder-driven entries by source).

### Stock consumption & projection
Marking a dose as taken really decrements the matching on-hand stock batch — by the exact combination fixed on the period for strength doses, or first-in-first-out (oldest supply first) for plain unit doses, unless that period pins one specific supply (see above) — not just a passive count. Editing or deleting a logged dose (or changing it from Taken back to Skipped) reverses that deduction exactly. If stock can't cover a dose, taking it is blocked everywhere (Home, notification, manual history entry) until you add more — nothing is ever force-deducted into the negative.

For each period, the app also projects how much stock is left at its start and end, by simulating day-by-day consumption forward from today the same way real logging would (so the projection and reality never disagree). Once a drug has more than one supply, both the overall and per-period figures also break down each supply's own projected run-out date individually (or "sufficient" if it isn't expected to run out soon). A period pinned to one supply reports its own start/end/run-out against that supply alone rather than the drug-wide total. A period card is only highlighted as depleted when an actual dose along the way can't be covered — running out exactly after covering the period's last dose is not flagged.

A shopping-cart icon appears — next to a period with a defined end (fixed date / N days / N occurrences), and once overall covering every such period at once — whenever there isn't enough on hand to cover every remaining dose through that end date; tapping it opens a popup listing exactly what's short (by strength, or a plain count for unit-dosed drugs). An open-ended period has no course to "complete", so it never shows one.

### Notifications
- **Medication reminders**: exact alarms for the next few days, rescheduled automatically whenever a period/time changes, after a device reboot, and via a periodic background refresh. Each notification has Took it / Skipped / Remind later actions; snooze duration is configurable. Left unanswered, a reminder repeats every 5 minutes until you respond — but logging the dose directly in the app dismisses it right away. When a patient has more than one drug due at the exact same time, their reminders are merged into a single notification instead of one per drug — Took it / Skipped / Remind later on it apply to every drug in the group at once, and tapping the notification body opens a combined panel where you can check off individually which of them you're taking right now. Any time of day can optionally be marked to ring like a system alarm — full-screen over the lock screen, with alarm-volume sound and vibration — for doses that need to actually wake you up (these never get merged, even if another reminder lands at the same time); on Android 14+ this needs a one-time permission grant, which the app prompts for the moment you turn the option on. The alarm-ringing screen has a "Mute alarm" button to silence the sound/vibration without dismissing the reminder, so there's time to actually get to the medication before tapping Took it.
- **Low-stock reminders**: a per-batch, optional alert configured either as "remind me N days before it runs out" (based on the same stock projection) or "remind me when only N units remain" (checked directly against current quantity, no forecast needed). The notification names the specific supply and how much of it remains (plus the projected run-out date, for the days-before mode). Tapping one opens that drug's detail screen; a "Remind tomorrow" action postpones it a day.

### Backup & restore (Google Drive & local file)
Connect a Google account (Drive `appdata` scope only) to back up all data — every patient and their drugs, plus the snooze-duration setting — as a single JSON file in the app's private Drive folder, and restore it later. A local file (`pills-in-time-backup.json`) can be saved/restored the same way via the system file picker, no Google account needed. Restore is always a full wipe-and-replace of local data, followed by re-arming all notifications.

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

## Release

Release builds are signed via a gitignored `keystore.properties` (repo root) with `storeFile`/`storePassword`/`keyAlias`/`keyPassword` keys — without it, release builds are simply unsigned. Bump `versionCode` in `app/build.gradle.kts` before every new upload.

```
./gradlew bundleRelease          # produces app/build/outputs/bundle/release/app-release.aab
./gradlew publishReleaseBundle   # builds + uploads straight to the Play Console internal testing track
```

`publishReleaseBundle` needs a gitignored `release-manager-key.json` (repo root) — a Google Cloud service account key with Play Console's "Manage testing releases" permission only.

## License

MIT — see [LICENSE](LICENSE).
