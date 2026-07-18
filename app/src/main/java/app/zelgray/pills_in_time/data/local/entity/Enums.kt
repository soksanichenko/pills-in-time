package app.zelgray.pills_in_time.data.local.entity

enum class DrugForm { TABLET, CAPSULE, DROPS, ML, OTHER }

enum class StrengthUnit { MG, MCG, IU }

enum class EndMode { DATE, DAYS, NONE }

enum class CycleType { DAILY, EVERY_OTHER_DAY, SPECIFIC_DAYS, DAYS_ON_OFF, CUSTOM }

enum class DoseMode { UNITS, STRENGTH }

enum class IntakeStatus { TAKEN, SKIPPED }

enum class IntakeSource { REMINDER, MANUAL }
