package app.zelgray.pills_in_time.data.local.entity

enum class DrugForm { TABLET, CAPSULE, DROPS, ML, AMPOULE, SACHET, OTHER }

enum class StrengthUnit { MG, MCG, IU }

enum class EndMode { DATE, DAYS, OCCURRENCES, NONE }

enum class CycleType { DAILY, EVERY_OTHER_DAY, SPECIFIC_DAYS, DAYS_ON_OFF, CUSTOM }

enum class DoseMode { UNITS, STRENGTH }

enum class IntakeStatus { TAKEN, SKIPPED }

enum class IntakeSource { REMINDER, MANUAL }

/**
 * What an alarm firing actually means. DOSE_REMINDER/SESSION_START_PROMPT are
 * the two kinds registered in the ScheduledAlarm table (the 3-day rolling
 * window); SESSION_TICK is an ad-hoc, self-rescheduling hourly session alarm
 * that's never persisted there (see StartDayWorker/PostSessionStatusWorker) —
 * it still needs its own tag so NotificationPostReceiver can route it.
 */
enum class AlarmKind { DOSE_REMINDER, SESSION_START_PROMPT, SESSION_TICK }
