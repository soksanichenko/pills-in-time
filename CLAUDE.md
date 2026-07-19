# CLAUDE.md — pills-in-time (MedTracker)

Android app (Kotlin + Jetpack Compose) tracking medication schedules, stock-on-hand, and reminders. Package: `app.zelgray.pills_in_time`.

## Project Structure

```
app/src/main/java/app/zelgray/pills_in_time/
  MedTrackerApp.kt, MainActivity.kt
  data/local/entity/        Drug, DrugStockBatch, ScheduledIntake, IntakeTime, IntakeLog, IntakeLogConsumption, ScheduledAlarm, Enums
  data/local/dao/            one DAO per entity
  data/local/relation/       @Relation projections (ScheduledIntakeWithTimes, IntakeLogWithDrug)
  data/local/converter/      Converters.kt (java.time <-> Room primitives, enum <-> name, Set<DayOfWeek> <-> CSV,
                              List<DoseComboPiece> <-> CSV for IntakeTime.doseAllocation)
  data/local/MedTrackerDatabase.kt, Migrations.kt
  data/repository/           DrugRepository, StockRepository, StockConsumptionRepository, ScheduleRepository,
                              IntakeRepository, BackupRepository, SettingsRepository, ScheduledAlarmRepository
  data/remote/drive/         DriveApi.kt (Retrofit), DriveAuthManager.kt (Play Services Authorization API)
  domain/model/              Occurrence, DoseCombo, DoseConsumptionResult, RecordLogResult, EffectiveStrength,
                              StockProjection, BackupPayload (DTOs), AlarmSpec, LowStockAlert
  domain/usecase/            GenerateOccurrencesForDateUseCase, ProjectDrugStockUseCase, FindDoseCombosUseCase,
                              ResolveDoseConsumptionUseCase, ResolveEffectiveStrengthUseCase, ScheduleAlarmsForWindowUseCase,
                              CheckLowStockRemindersUseCase, Export/ImportBackupUseCase,
                              CycleActiveDays.kt (isPeriodActiveOn, computeEndDateForOccurrences — shared, not a class)
  notification/              NotificationChannels, AlarmScheduler, AlarmPermissions, DailyRescheduleWorker,
                              LowStockCheckWorker, LowStockActionReceiver, SnoozeLowStockReminderWorker, LowStockNotifications,
                              BootRescheduleReceiver, NotificationPostReceiver,
                              PostNotificationWorker, IntakeActionReceiver, LogIntakeActionWorker, SnoozeWorker
  ui/navigation/, ui/home/, ui/drugs/, ui/history/, ui/settings/, ui/common/, ui/theme/
  di/                        DatabaseModule, NetworkModule, UtilModule
  util/                      NowProvider, ValidationUtils, NumberFormat
```

Layer-based at the top (`data`/`domain`/`ui`/`notification`/`di`/`util`) since `data` is shared across every feature; `ui` is grouped by feature (drugs/home/history/settings) for screen+ViewModel cohesion.

## Navigation & Screens

Single-activity, bottom-nav `NavHost` (`MedTrackerNavHost.kt`) with 4 tabs: **Home**, **Drugs**, **History**, **Settings** (routes in `NavRoutes.kt`). There is no separate onboarding/backup tab — language switching and Google Drive backup both live inside the Settings screen. Detail/form screens (drug detail, add/edit drug/stock/period, add/edit history entry) are pushed on top of the tab stack; the bottom bar hides on non-tab routes.

Tapping a notification action deep-links into Home via `OccurrenceRequest` (parsed from the launching `Intent` in `MainActivity`), consumed once by `MedTrackerNavHost`. A low-stock notification tap deep-links the same way but via `StockRequest`/`ACTION_VIEW_STOCK`, navigating straight to that batch's `EDIT_STOCK` route instead of Home.

On Home, the day can also be changed by a horizontal swipe (only recognized starting in the bottom half of the content area, with a thin hint strip there, so it doesn't fight the list's vertical scroll) or by tapping the day label to open a date picker — both call the same `HomeViewModel.onPrevDay`/`onNextDay`/`goToDate` as the arrow buttons. Re-opening an already-logged occurrence's action sheet shows a single "Undo" action (deletes the log, reversing any stock consumption) instead of Took it/Skipped/Remind later.

## Command / Command-equivalent notes

This is an Android app, not a bot — there are no slash commands. The nearest equivalents to "commands" are the notification actions (`Took it` / `Skipped` / `Remind later`) wired through `IntakeActionReceiver` and `SnoozeWorker`.

## Database (Room, schema version 5)

Seven entities in `MedTrackerDatabase` (`data/local/entity/`):

- **Drug** — id, name, form (`DrugForm`: TABLET/CAPSULE/DROPS/ML/AMPOULE/SACHET/OTHER), customFormText, createdAt.
- **DrugStockBatch** — id, drugId (FK cascade), quantity (Double), strengthValue/strengthUnit (nullable `Double?`/`StrengthUnit?`: MG/MCG/IU — null means this drug doesn't track strength at all, in which case it's capped at a single batch; see `AddEditStockViewModel`), addedAt (Instant — defines "most recently added" batch, not row order), lowStockReminderDaysBefore (nullable Int), lowStockReminderFiredForRunOutDate (nullable LocalDate, dedupes repeat low-stock notifications for an unchanged forecast).
- **ScheduledIntake** — id, drugId (FK cascade), startDate, endMode (`EndMode`: DATE/DAYS/OCCURRENCES/NONE), endDate, durationDays, durationOccurrences (nullable Int, only for `OCCURRENCES` — see below), cycleType (`CycleType`: DAILY/EVERY_OTHER_DAY/SPECIFIC_DAYS/DAYS_ON_OFF/CUSTOM), specificDays (`Set<DayOfWeek>?`), customCycleText, intakeDays/breakDays (only meaningful for DAYS_ON_OFF).
- **IntakeTime** — id, scheduledIntakeId (FK cascade), timeOfDay, doseMode (`DoseMode`: UNITS/STRENGTH), doseValue, doseAllocation (nullable `List<DoseComboPiece>`, CSV-encoded — the specific on-hand strength combo this STRENGTH-mode dose is fixed to, chosen once at period-setup time when more than one combo exists; null for UNITS-mode times and legacy rows).
- **IntakeLog** — id, drugId, scheduledIntakeId, intakeTimeId, occurrenceDate, status (`IntakeStatus`: TAKEN/SKIPPED), actualDateTime, actualDoseValue, actualDoseMode, source (`IntakeSource`: REMINDER/MANUAL), createdAt, updatedAt. Unique index on `(scheduledIntakeId, intakeTimeId, occurrenceDate)` — this triple is the composite occurrence key, upserted lazily only when an occurrence's status actually changes.
- **IntakeLogConsumption** — id, intakeLogId (FK cascade), batchId (FK cascade), quantity. A registry of exactly what a TAKEN log decremented and from which batch, so editing/deleting the log or changing its status away from TAKEN can reverse stock precisely (see "Real stock consumption" below).
- **ScheduledAlarm** — requestCode (PK), scheduledIntakeId, intakeTimeId, occurrenceDate, triggerAtMillis. A registry (not a spec concept) so `DailyRescheduleWorker` can reconcile/cancel exact alarms correctly instead of guessing.

`EndMode.OCCURRENCES` ("N occurrences" instead of "N days"): for a non-daily cycle, a literal calendar-day span doesn't mean "N doses" — 8 calendar days of a once-a-week cycle contains only ~1 active day, not 8. `computeEndDateForOccurrences` (`domain/usecase/CycleActiveDays.kt`) instead walks forward from `startDate` counting only cycle-active days until N are reached (capped at a 10-year safety horizon), so "8 occurrences" of a weekly cycle correctly spans ~8 weeks. Only offered in the UI for non-DAILY/CUSTOM cycles (`AddEditPeriodUiState.occurrenceDurationAvailable`), since for those "N days" already means the same thing.

Occurrences for a given date are never pre-materialized: `GenerateOccurrencesForDateUseCase` builds them live from active periods+times, left-joined against any existing `IntakeLog` row. A past date with no log renders as `MISSED` (never fabricated as taken); today's un-logged occurrences are `OVERDUE` once `now > scheduledTime + grace`, else `UPCOMING`. Cycle-active-day logic (`isPeriodActiveOn`) is shared with `ProjectDrugStockUseCase` and `computeEndDateForOccurrences` from `domain/usecase/CycleActiveDays.kt` — previously duplicated 3 ways, now one implementation.

Migrations: `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`, `MIGRATION_4_5` registered in `DatabaseModule`; keep all schema JSON exports under `app/schemas/` in sync with `Migrations.kt` when bumping the version. `MIGRATION_4_5` rebuilds `drug_stock_batches` (SQLite can't relax a column's `NOT NULL` via `ALTER TABLE`) to make strength nullable, and adds `durationOccurrences` to `scheduled_intakes`.

## Real stock consumption

Marking an occurrence TAKEN (Home, notification action, or manual history entry — all funnel through `IntakeRepository`'s single `writeLog` path) really decrements on-hand stock, not just a passive projection:

- **STRENGTH-mode doses**: the exact combo (which on-hand strengths + counts) is fixed once in `AddEditPeriodScreen` at period-setup time (`IntakeTime.doseAllocation`) — reusing `FindDoseCombosUseCase`'s ranked candidates, with a picker shown only when more than one combo exists. Which *specific batch* among same-strength batches gets decremented is never a user decision, though — that's resolved fresh at every logging event via FIFO (oldest `addedAt` first), since same-strength batches are fungible.
- **UNITS-mode doses**: no combo concept at all — just FIFO across whichever batches the drug has, oldest first.
- `ResolveDoseConsumptionUseCase` does this resolution (pinned allocation or FIFO) against current batches, returning either `DoseConsumptionResult.Resolved(decrements)` or `.Insufficient`.
- `StockConsumptionRepository` applies/reverses the resolved decrements transactionally, recording each in `IntakeLogConsumption` for exact reversal later.
- `IntakeRepository.writeLog` wraps reversal-of-old-status + the log write + apply-of-new-status in one transaction; if resolution comes back `Insufficient`, it throws internally so the *entire* transaction rolls back — nothing is written at all, and callers get `RecordLogResult.InsufficientStock` (Home shows a snackbar; the notification path silently no-ops, so the already-built 5-minute repeat reminder keeps nagging; manual history entry shows a form error). The user must top up stock first — insufficient stock is never force-drained or fabricated.
- `ProjectDrugStockUseCase`'s forward-looking projection reuses this exact same resolution day-by-day (on a scratch copy of the batches), so the projection and real logging can never disagree the way they used to when both were driven by a single "effective strength" conversion instead of real per-strength/per-batch accounting.

## Domain Use Cases (plain Kotlin, unit-tested, no Android imports)

- `GenerateOccurrencesForDateUseCase` — per-date occurrence list + live status (see above); `EVERY_OTHER_DAY` parity is anchored to the period's own `startDate`; `CUSTOM` behaves like `DAILY` (purely descriptive); `DAYS_ON_OFF` cycles `intakeDays` on then `breakDays` off from `startDate`.
- `ProjectDrugStockUseCase` — day-by-day simulation (730-day horizon cap for open-ended periods) that resolves each active time's dose via `ResolveDoseConsumptionUseCase` against a scratch copy of the batches (real per-strength/per-batch accounting, not a single "effective strength" conversion), yielding per-period stock-at-start/at-end, an overall run-out date or "sufficient long-term" verdict, and a `batchExhaustionDates: Map<batchId, LocalDate>` for per-supply reminders. A dose that can't be resolved consumes nothing that day (mirrors the real blocking behavior) rather than draining the remainder.
- `ResolveDoseConsumptionUseCase` — resolves a dose (pinned strength allocation, or a plain unit count) to concrete `(batchId, quantity)` decrements via FIFO; see "Real stock consumption" above.
- `CheckLowStockRemindersUseCase` — runs `ProjectDrugStockUseCase` once per drug (across all its batches together, so multi-strength combos are modeled correctly) and reads each batch's own projected exhaustion date back out of that single simulation, deduping via `lowStockReminderFiredForRunOutDate`.
- `FindDoseCombosUseCase` — backtracking search over up to 4 distinct on-hand strengths (batches with `quantity <= 0` or no strength excluded), 0–6 units in 0.5 steps, ranked by `(total pieces, distinct strengths, dominant-piece share)`, top 2 returned. Drives both the period-setup combo picker (pinning `IntakeTime.doseAllocation`) and the advisory dose-preview text elsewhere.
- `ResolveEffectiveStrengthUseCase` — picks the batch with the max `addedAt` as "the" strength for unit<->strength dose conversion (display only; unrelated to real consumption resolution). Null if there are no batches, or the most recent one has no strength — which also gates `strengthModeAvailable` in period setup, since a drug that doesn't track strength can only ever log in UNITS mode.
- `ScheduleAlarmsForWindowUseCase` — expands occurrences over the next few days into `AlarmSpec`s for the notification layer.
- `Export`/`ImportBackupUseCase` — map entities <-> `BackupPayload` DTOs (epoch-primitive fields only, since `java.time` types aren't natively kotlinx.serialization-able); also carries the snooze-minutes setting (see Backup below).

## Notifications

Rolling-window exact-alarm design: `DailyRescheduleWorker` (periodic + enqueued on every period/time CRUD and on boot via `BootRescheduleReceiver`) reconciles the `scheduled_alarms` registry against a fresh few-day window from `ScheduleAlarmsForWindowUseCase`, registering `AlarmManager` exact alarms per occurrence (request code = stable hash of the occurrence triple). `NotificationPostReceiver` builds the reminder notification (channel: `medication_reminders`); `IntakeActionReceiver` handles the 3 actions, delegating the actual Room write to `LogIntakeActionWorker` (kept out of `onReceive`'s execution budget) and snooze to `SnoozeWorker` (re-arms a single alarm at `now + snoozeMinutes` from `SettingsRepository`, default 15 min, no DB write). `LowStockCheckWorker` runs `CheckLowStockRemindersUseCase` periodically on a separate channel (`low_stock_reminders`).

`PostNotificationWorker` self-reschedules: after posting, it enqueues another copy of itself 5 minutes later as WorkManager unique work named `post_notification_<notificationId>` (`ExistingWorkPolicy.REPLACE`), so an ignored reminder keeps re-alerting every 5 minutes. `IntakeActionReceiver` cancels that unique work on Take/Skip/Snooze; `SnoozeWorker` re-enqueues under the same unique name (so the snooze delay replaces the pending 5-minute repeat instead of racing it — once the snoozed notification re-fires, the 5-minute repeat resumes automatically if still unacknowledged). Each run also checks `IntakeRepository.getLogForOccurrenceOnce` first and no-ops (without rescheduling) if the occurrence was already logged some other way (e.g. a Home screen quick action), so the chain self-terminates even without an explicit cancel. Logging directly in-app (Home or manual history entry) additionally cancels the currently-shown notification and its pending repeat work outright (`ScheduleAlarmsForWindowUseCase.computeRequestCode` + `NotificationContracts.repeatWorkName`), so it doesn't linger visibly until the next repeat tick would have silently no-op'd it.

Exact-alarm permission (API 31+) is requested via `AlarmPermissions`; falls back to inexact alarms if declined.

Low-stock reminders (`LowStockCheckWorker`, channel `low_stock_reminders`) are interactive too: tapping one deep-links (via `ACTION_VIEW_STOCK` / `StockRequest`, mirroring `OccurrenceRequest`) straight into that batch's edit-stock screen, and a "Remind tomorrow" action (`LowStockActionReceiver` → `SnoozeLowStockReminderWorker`) re-posts the same notification after a fixed 24-hour delay. `LowStockNotifications` holds the shared notification-building logic so the initial post and the postponed re-post stay identical.

## Backup (Google Drive & local file)

`DriveAuthManager` wraps the Play Services **Authorization API** (`Identity.getAuthorizationClient`), requesting only the `drive.appdata` scope — never full Drive access. Requires a one-time Google Cloud Console OAuth client matching this app's package + signing SHA-1 (see doc comment in `DriveAuthManager.kt`); without it, authorization returns `DriveAuthResult.Error`. `BackupRepository` talks to the Drive v3 REST API directly via Retrofit/OkHttp (`DriveApi`) rather than the generated Drive client SDK. Backup payload schema is versioned (`BackupPayload.SCHEMA_VERSION`, currently 6, absent/nullable fields keep older exports decodable); restore is always a full wipe-and-replace inside one Room transaction, followed by cancelling+re-registering every alarm via `DailyRescheduleWorker.enqueueNow`. The payload also carries the snooze-minutes setting (`SettingsRepository`), restored (or defaulted) alongside everything else.

`BackupRepository.backupToFile`/`restoreFromFile` do the same export/import through Android's Storage Access Framework (`ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT`) instead of the Drive API — everything except the Drive-specific auth/upload/search calls (`buildBackupJson`/`restoreFromJson`) is shared between the two paths. The local file name (`BackupRepository.LOCAL_BACKUP_FILE_NAME` = `pills-in-time-backup.json`) is deliberately a separate constant from the Drive backup's file name, so renaming one never orphans or breaks lookup of the other.

## Architecture Principles

- **DI**: Hilt throughout (`@HiltAndroidApp`, `@AndroidEntryPoint` on activity/receivers, `@HiltWorker` on WorkManager workers). Modules in `di/`.
- **Reactive state**: repositories expose `Flow`s over DAOs; ViewModels `combine()` them into a single `StateFlow<UiState>` per screen, collected via `collectAsStateWithLifecycle()`. Persisting a change (e.g. `updateBatch`) is sufficient to recompute all dependent projections — no manual recalculation call needed.
- **Stateless composables + hoisted state**: purely cosmetic local state stays `remember`; anything needing domain computation is hoisted to the ViewModel.
- **Validation**: centralized in `util/ValidationUtils.kt`, not scattered parse-or-fallback logic.
- **Testability**: `domain/usecase/` has zero Android imports and is unit-tested with JUnit + `kotlinx-coroutines-test` + Turbine/MockK; time is injected via `NowProvider` (bound to `SystemNowProvider` in `UtilModule`), never read directly from `Instant.now()`/`LocalDate.now()` inside use cases.
- **Enums stored as `.name`** in Room (via `Converters.kt`), not ordinal, so future enum additions don't corrupt existing rows.

## Localization

`values/` (English, source of truth) + `values-uk/`, `values-ru/`, `values-cs/` — flat `snake_case` string keys, all four locale files must stay in parity (same key set). `plurals.xml` exists per-locale alongside `strings.xml`. Locale switching is per-app (`AppLanguage`/`LanguageManager` in `ui/settings/Language.kt`, wrapping `AppCompatDelegate.setApplicationLocales`), independent of system language, declared in `locales_config.xml` + manifest `android:localeConfig`.

## Release Signing & Publishing

`app/build.gradle.kts` loads `keystore.properties` (repo root, gitignored — never commit) via `rootProject.file(...)`; if the file is absent, `signingConfigs`/`buildTypes.release.signingConfig` are skipped entirely, so debug/CI builds without a keystore still work (release builds just come out unsigned). Expected keys in `keystore.properties`: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. `*.keystore`/`*.jks` are also gitignored. Bump `versionCode` in `defaultConfig` before every new Play Console upload (it rejects a reused `versionCode` outright); `./gradlew bundleRelease` produces `app/build/outputs/bundle/release/app-release.aab`.

The **Gradle Play Publisher** plugin (`com.github.triplet.play`, `libs.plugins.play.publisher`) is applied in `app/build.gradle.kts` with a `play { }` block pointing at `release-manager-key.json` (repo root, gitignored — a Google Cloud service account key scoped to Play Console's "Manage testing releases" permission only, never production) and `track.set("internal")`. `./gradlew publishReleaseBundle` builds and uploads the release bundle straight to the internal testing track in one step — no manual Play Console upload needed. Without `release-manager-key.json` present, normal builds are unaffected; only the publish task would fail.

## Key Dependencies

| Package | Version | Purpose |
|---|---|---|
| AGP | 9.3.0 | Android Gradle Plugin |
| Kotlin / KSP | 2.2.10 / 2.2.10-2.0.2 | Language + annotation processing |
| Hilt | 2.60.1 | DI |
| Room | 2.8.4 | Local persistence |
| Compose BOM | 2026.06.01 | UI toolkit (Material3) |
| Navigation Compose | 2.9.8 | In-app navigation |
| Lifecycle | 2.11.0 | ViewModel/runtime-compose |
| WorkManager | 2.11.2 | Background reschedule/backup/log workers |
| DataStore Preferences | 1.2.1 | Settings (snooze minutes, Drive connection state) |
| kotlinx-serialization-json | 1.11.0 | Backup JSON payload |
| kotlinx-coroutines | 1.11.0 | Coroutines (+ play-services interop) |
| Credentials / googleid / play-services-auth | 1.6.0 / 1.2.0 / 21.6.0 | Google account auth for Drive backup |
| Retrofit / OkHttp | 2.12.0 / 4.12.0 | Drive v3 REST API client |
| Turbine / MockK | 1.2.1 / 1.14.11 | Test-only: Flow testing, mocking |

`minSdk 26`, `targetSdk`/`compileSdk 37`. Exact versions live in `gradle/libs.versions.toml` — check there directly rather than trusting this table if it looks stale.

## Notable Deviations from a Naive Port

If working from the original prototype/spec this app was built against, these are intentional, already-implemented correctness choices — not gaps:
1. Overdue status is computed live from current time, never hardcoded.
2. Past occurrences with no log render as `MISSED`, never auto-marked taken.
3. "Most recently added" stock batch is resolved via `addedAt`, not list/insertion order.
4. Dose-combo search filters out batches with `quantity <= 0`.
5. Form validation is centralized and real (required name, quantity/dose > 0, >=1 time-of-day per period), not silent fallbacks.
6. Real Android `<plurals>` per locale.

Also implemented beyond the original scope: a `DAYS_ON_OFF` cycle type, per-batch low-stock reminders (`CheckLowStockRemindersUseCase` + `LowStockCheckWorker`) with a per-supply run-out date breakdown once a drug has more than one batch, a restock ("+") quick action on stock rows, real stock consumption tied to logged doses (see "Real stock consumption" above) rather than a passive projection only, local file backup alongside Google Drive, ampoule/sachet as additional drug forms, optional per-drug strength (a drug with no strength is capped at one supply), a duration-by-occurrences end mode for non-daily cycles, and Czech (`cs`) as a fourth locale.
