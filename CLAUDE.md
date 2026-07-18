# CLAUDE.md — pills-in-time (MedTracker)

Android app (Kotlin + Jetpack Compose) tracking medication schedules, stock-on-hand, and reminders. Package: `app.zelgray.pills_in_time`.

## Project Structure

```
app/src/main/java/app/zelgray/pills_in_time/
  MedTrackerApp.kt, MainActivity.kt
  data/local/entity/        Drug, DrugStockBatch, ScheduledIntake, IntakeTime, IntakeLog, ScheduledAlarm, Enums
  data/local/dao/            one DAO per entity
  data/local/relation/       @Relation projections (ScheduledIntakeWithTimes, IntakeLogWithDrug)
  data/local/converter/      Converters.kt (java.time <-> Room primitives, enum <-> name, Set<DayOfWeek> <-> CSV)
  data/local/MedTrackerDatabase.kt, Migrations.kt
  data/repository/           DrugRepository, StockRepository, ScheduleRepository, IntakeRepository,
                              BackupRepository, SettingsRepository, ScheduledAlarmRepository
  data/remote/drive/         DriveApi.kt (Retrofit), DriveAuthManager.kt (Play Services Authorization API)
  domain/model/              Occurrence, DoseCombo, EffectiveStrength, StockProjection, BackupPayload (DTOs), AlarmSpec, LowStockAlert
  domain/usecase/            GenerateOccurrencesForDateUseCase, ProjectDrugStockUseCase, FindDoseCombosUseCase,
                              ResolveEffectiveStrengthUseCase, ScheduleAlarmsForWindowUseCase,
                              CheckLowStockRemindersUseCase, Export/ImportBackupUseCase
  notification/              NotificationChannels, AlarmScheduler, AlarmPermissions, DailyRescheduleWorker,
                              LowStockCheckWorker, BootRescheduleReceiver, NotificationPostReceiver,
                              PostNotificationWorker, IntakeActionReceiver, LogIntakeActionWorker, SnoozeWorker
  ui/navigation/, ui/home/, ui/drugs/, ui/history/, ui/settings/, ui/common/, ui/theme/
  di/                        DatabaseModule, NetworkModule, UtilModule
  util/                      NowProvider, ValidationUtils, NumberFormat
```

Layer-based at the top (`data`/`domain`/`ui`/`notification`/`di`/`util`) since `data` is shared across every feature; `ui` is grouped by feature (drugs/home/history/settings) for screen+ViewModel cohesion.

## Navigation & Screens

Single-activity, bottom-nav `NavHost` (`MedTrackerNavHost.kt`) with 4 tabs: **Home**, **Drugs**, **History**, **Settings** (routes in `NavRoutes.kt`). There is no separate onboarding/backup tab — language switching and Google Drive backup both live inside the Settings screen. Detail/form screens (drug detail, add/edit drug/stock/period, add/edit history entry) are pushed on top of the tab stack; the bottom bar hides on non-tab routes.

Tapping a notification action deep-links into Home via `OccurrenceRequest` (parsed from the launching `Intent` in `MainActivity`), consumed once by `MedTrackerNavHost`.

## Command / Command-equivalent notes

This is an Android app, not a bot — there are no slash commands. The nearest equivalents to "commands" are the notification actions (`Took it` / `Skipped` / `Remind later`) wired through `IntakeActionReceiver` and `SnoozeWorker`.

## Database (Room, schema version 3)

Six entities in `MedTrackerDatabase` (`data/local/entity/`):

- **Drug** — id, name, form (`DrugForm`: TABLET/CAPSULE/DROPS/ML/OTHER), customFormText, createdAt.
- **DrugStockBatch** — id, drugId (FK cascade), quantity (Double), strengthValue, strengthUnit (`StrengthUnit`: MG/MCG/IU), addedAt (Instant — defines "most recently added" batch, not row order), lowStockReminderDaysBefore (nullable Int), lowStockReminderFiredForRunOutDate (nullable LocalDate, dedupes repeat low-stock notifications for an unchanged forecast).
- **ScheduledIntake** — id, drugId (FK cascade), startDate, endMode (`EndMode`: DATE/DAYS/NONE), endDate, durationDays, cycleType (`CycleType`: DAILY/EVERY_OTHER_DAY/SPECIFIC_DAYS/DAYS_ON_OFF/CUSTOM), specificDays (`Set<DayOfWeek>?`), customCycleText, intakeDays/breakDays (only meaningful for DAYS_ON_OFF).
- **IntakeTime** — id, scheduledIntakeId (FK cascade), timeOfDay, doseMode (`DoseMode`: UNITS/STRENGTH), doseValue.
- **IntakeLog** — id, drugId, scheduledIntakeId, intakeTimeId, occurrenceDate, status (`IntakeStatus`: TAKEN/SKIPPED), actualDateTime, actualDoseValue, actualDoseMode, source (`IntakeSource`: REMINDER/MANUAL), createdAt, updatedAt. Unique index on `(scheduledIntakeId, intakeTimeId, occurrenceDate)` — this triple is the composite occurrence key, upserted lazily only when an occurrence's status actually changes.
- **ScheduledAlarm** — requestCode (PK), scheduledIntakeId, intakeTimeId, occurrenceDate, triggerAtMillis. A registry (not a spec concept) so `DailyRescheduleWorker` can reconcile/cancel exact alarms correctly instead of guessing.

Occurrences for a given date are never pre-materialized: `GenerateOccurrencesForDateUseCase` builds them live from active periods+times, left-joined against any existing `IntakeLog` row. A past date with no log renders as `MISSED` (never fabricated as taken); today's un-logged occurrences are `OVERDUE` once `now > scheduledTime + grace`, else `UPCOMING`.

Migrations: `MIGRATION_1_2`, `MIGRATION_2_3` registered in `DatabaseModule`; keep both schema JSON exports under `app/schemas/` in sync with `Migrations.kt` when bumping the version.

## Domain Use Cases (plain Kotlin, unit-tested, no Android imports)

- `GenerateOccurrencesForDateUseCase` — per-date occurrence list + live status (see above); `EVERY_OTHER_DAY` parity is anchored to the period's own `startDate`; `CUSTOM` behaves like `DAILY` (purely descriptive); `DAYS_ON_OFF` cycles `intakeDays` on then `breakDays` off from `startDate`.
- `ProjectDrugStockUseCase` — day-by-day consumption simulation (720-day horizon cap for open-ended periods) yielding per-period stock-at-start/at-end plus an overall run-out date or "sufficient long-term" verdict.
- `CheckLowStockRemindersUseCase` — reuses `ProjectDrugStockUseCase` per-batch to decide whether a configured low-stock reminder should fire, deduping via `lowStockReminderFiredForRunOutDate`.
- `FindDoseCombosUseCase` — backtracking search over up to 4 distinct on-hand strengths (batches with `quantity <= 0` excluded), 0–6 units in 0.5 steps, ranked by `(total pieces, distinct strengths, dominant-piece share)`, top 2 returned.
- `ResolveEffectiveStrengthUseCase` — picks the batch with the max `addedAt` as "the" strength for unit<->strength dose conversion.
- `ScheduleAlarmsForWindowUseCase` — expands occurrences over the next few days into `AlarmSpec`s for the notification layer.
- `Export`/`ImportBackupUseCase` — map entities <-> `BackupPayload` DTOs (epoch-primitive fields only, since `java.time` types aren't natively kotlinx.serialization-able).

## Notifications

Rolling-window exact-alarm design: `DailyRescheduleWorker` (periodic + enqueued on every period/time CRUD and on boot via `BootRescheduleReceiver`) reconciles the `scheduled_alarms` registry against a fresh few-day window from `ScheduleAlarmsForWindowUseCase`, registering `AlarmManager` exact alarms per occurrence (request code = stable hash of the occurrence triple). `NotificationPostReceiver` builds the reminder notification (channel: `medication_reminders`); `IntakeActionReceiver` handles the 3 actions, delegating the actual Room write to `LogIntakeActionWorker` (kept out of `onReceive`'s execution budget) and snooze to `SnoozeWorker` (re-arms a single alarm at `now + snoozeMinutes` from `SettingsRepository`, default 15 min, no DB write). `LowStockCheckWorker` runs `CheckLowStockRemindersUseCase` periodically on a separate channel (`low_stock_reminders`).

Exact-alarm permission (API 31+) is requested via `AlarmPermissions`; falls back to inexact alarms if declined.

## Backup (Google Drive)

`DriveAuthManager` wraps the Play Services **Authorization API** (`Identity.getAuthorizationClient`), requesting only the `drive.appdata` scope — never full Drive access. Requires a one-time Google Cloud Console OAuth client matching this app's package + signing SHA-1 (see doc comment in `DriveAuthManager.kt`); without it, authorization returns `DriveAuthResult.Error`. `BackupRepository` talks to the Drive v3 REST API directly via Retrofit/OkHttp (`DriveApi`) rather than the generated Drive client SDK. Backup payload schema is versioned (`BackupPayload.SCHEMA_VERSION`, currently 3); restore is always a full wipe-and-replace inside one Room transaction, followed by cancelling+re-registering every alarm via `DailyRescheduleWorker.enqueueNow`.

## Architecture Principles

- **DI**: Hilt throughout (`@HiltAndroidApp`, `@AndroidEntryPoint` on activity/receivers, `@HiltWorker` on WorkManager workers). Modules in `di/`.
- **Reactive state**: repositories expose `Flow`s over DAOs; ViewModels `combine()` them into a single `StateFlow<UiState>` per screen, collected via `collectAsStateWithLifecycle()`. Persisting a change (e.g. `updateBatch`) is sufficient to recompute all dependent projections — no manual recalculation call needed.
- **Stateless composables + hoisted state**: purely cosmetic local state stays `remember`; anything needing domain computation is hoisted to the ViewModel.
- **Validation**: centralized in `util/ValidationUtils.kt`, not scattered parse-or-fallback logic.
- **Testability**: `domain/usecase/` has zero Android imports and is unit-tested with JUnit + `kotlinx-coroutines-test` + Turbine/MockK; time is injected via `NowProvider` (bound to `SystemNowProvider` in `UtilModule`), never read directly from `Instant.now()`/`LocalDate.now()` inside use cases.
- **Enums stored as `.name`** in Room (via `Converters.kt`), not ordinal, so future enum additions don't corrupt existing rows.

## Localization

`values/` (English, source of truth) + `values-uk/`, `values-ru/`, `values-cs/` — flat `snake_case` string keys, all four locale files must stay in parity (same key set). `plurals.xml` exists per-locale alongside `strings.xml`. Locale switching is per-app (`AppLanguage`/`LanguageManager` in `ui/settings/Language.kt`, wrapping `AppCompatDelegate.setApplicationLocales`), independent of system language, declared in `locales_config.xml` + manifest `android:localeConfig`.

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

Also implemented beyond the original scope: a `DAYS_ON_OFF` cycle type, per-batch low-stock reminders (`CheckLowStockRemindersUseCase` + `LowStockCheckWorker`), a restock ("+") quick action on stock rows, and Czech (`cs`) as a fourth locale.
