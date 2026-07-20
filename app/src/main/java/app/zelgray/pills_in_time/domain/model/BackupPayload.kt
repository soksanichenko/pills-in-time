package app.zelgray.pills_in_time.domain.model

import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugForm
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.entity.IntakeSource
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * JSON export/import shape, mirroring the Room entities 1:1 including their
 * original primary keys (safe because restore always wipes-and-replaces).
 * java.time types aren't natively kotlinx.serialization-able, so every
 * date/time/instant field is carried as a plain epoch primitive instead of
 * requiring custom contextual serializers.
 */
@Serializable
data class BackupPayload(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAtEpochMilli: Long,
    val drugs: List<DrugDto>,
    val stockBatches: List<StockBatchDto>,
    val scheduledIntakes: List<ScheduledIntakeDto>,
    val intakeTimes: List<IntakeTimeDto>,
    val intakeLogs: List<IntakeLogDto>,
    // Absent on backups made before this field existed.
    val snoozeMinutes: Int? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 8
    }
}

@Serializable
data class DrugDto(
    val id: Long,
    val name: String,
    val form: String,
    val customFormText: String? = null,
    val createdAtEpochMilli: Long,
)

@Serializable
data class StockBatchDto(
    val id: Long,
    val drugId: Long,
    val quantity: Double,
    val strengthValue: Double? = null,
    val strengthUnit: String? = null,
    val addedAtEpochMilli: Long,
    val lowStockReminderDaysBefore: Int? = null,
    val lowStockReminderFiredForRunOutDateEpochDay: Long? = null,
    val lowStockReminderUnitsBefore: Double? = null,
    val lowStockReminderUnitsAlreadyFired: Boolean = false,
)

@Serializable
data class ScheduledIntakeDto(
    val id: Long,
    val drugId: Long,
    val startDateEpochDay: Long,
    val endMode: String,
    val endDateEpochDay: Long? = null,
    val durationDays: Int? = null,
    val cycleType: String,
    val specificDays: List<Int>? = null,
    val customCycleText: String? = null,
    val intakeDays: Int? = null,
    val breakDays: Int? = null,
    val durationOccurrences: Int? = null,
    val createdAtEpochMilli: Long,
    // Absent on backups made before this field existed.
    val pinnedBatchId: Long? = null,
)

@Serializable
data class IntakeTimeDto(
    val id: Long,
    val scheduledIntakeId: Long,
    val timeOfDaySecond: Int,
    val doseMode: String,
    val doseValue: Double,
    val doseAllocationCsv: String? = null,
)

@Serializable
data class IntakeLogDto(
    val id: Long,
    val drugId: Long,
    val scheduledIntakeId: Long,
    val intakeTimeId: Long,
    val occurrenceDateEpochDay: Long,
    val status: String,
    val actualDateTimeEpochMilli: Long,
    val actualDoseValue: Double,
    val actualDoseMode: String,
    val source: String,
    val createdAtEpochMilli: Long,
    val updatedAtEpochMilli: Long,
)

fun Drug.toDto() = DrugDto(
    id = id,
    name = name,
    form = form.name,
    customFormText = customFormText,
    createdAtEpochMilli = createdAt.toEpochMilli(),
)

fun DrugDto.toEntity() = Drug(
    id = id,
    name = name,
    form = DrugForm.valueOf(form),
    customFormText = customFormText,
    createdAt = Instant.ofEpochMilli(createdAtEpochMilli),
)

fun DrugStockBatch.toDto() = StockBatchDto(
    id = id,
    drugId = drugId,
    quantity = quantity,
    strengthValue = strengthValue,
    strengthUnit = strengthUnit?.name,
    addedAtEpochMilli = addedAt.toEpochMilli(),
    lowStockReminderDaysBefore = lowStockReminderDaysBefore,
    lowStockReminderFiredForRunOutDateEpochDay = lowStockReminderFiredForRunOutDate?.toEpochDay(),
    lowStockReminderUnitsBefore = lowStockReminderUnitsBefore,
    lowStockReminderUnitsAlreadyFired = lowStockReminderUnitsAlreadyFired,
)

fun StockBatchDto.toEntity() = DrugStockBatch(
    id = id,
    drugId = drugId,
    quantity = quantity,
    strengthValue = strengthValue,
    strengthUnit = strengthUnit?.let { StrengthUnit.valueOf(it) },
    addedAt = Instant.ofEpochMilli(addedAtEpochMilli),
    lowStockReminderDaysBefore = lowStockReminderDaysBefore,
    lowStockReminderFiredForRunOutDate = lowStockReminderFiredForRunOutDateEpochDay?.let { LocalDate.ofEpochDay(it) },
    lowStockReminderUnitsBefore = lowStockReminderUnitsBefore,
    lowStockReminderUnitsAlreadyFired = lowStockReminderUnitsAlreadyFired,
)

fun ScheduledIntake.toDto() = ScheduledIntakeDto(
    id = id,
    drugId = drugId,
    startDateEpochDay = startDate.toEpochDay(),
    endMode = endMode.name,
    endDateEpochDay = endDate?.toEpochDay(),
    durationDays = durationDays,
    cycleType = cycleType.name,
    specificDays = specificDays?.map { it.value },
    customCycleText = customCycleText,
    intakeDays = intakeDays,
    breakDays = breakDays,
    durationOccurrences = durationOccurrences,
    createdAtEpochMilli = createdAt.toEpochMilli(),
    pinnedBatchId = pinnedBatchId,
)

fun ScheduledIntakeDto.toEntity() = ScheduledIntake(
    id = id,
    drugId = drugId,
    startDate = LocalDate.ofEpochDay(startDateEpochDay),
    endMode = EndMode.valueOf(endMode),
    endDate = endDateEpochDay?.let { LocalDate.ofEpochDay(it) },
    durationDays = durationDays,
    cycleType = CycleType.valueOf(cycleType),
    specificDays = specificDays?.map { DayOfWeek.of(it) }?.toSet(),
    customCycleText = customCycleText,
    intakeDays = intakeDays,
    breakDays = breakDays,
    durationOccurrences = durationOccurrences,
    createdAt = Instant.ofEpochMilli(createdAtEpochMilli),
    pinnedBatchId = pinnedBatchId,
)

fun IntakeTime.toDto() = IntakeTimeDto(
    id = id,
    scheduledIntakeId = scheduledIntakeId,
    timeOfDaySecond = timeOfDay.toSecondOfDay(),
    doseMode = doseMode.name,
    doseValue = doseValue,
    doseAllocationCsv = doseAllocation?.encodeToCsv(),
)

fun IntakeTimeDto.toEntity() = IntakeTime(
    id = id,
    scheduledIntakeId = scheduledIntakeId,
    timeOfDay = LocalTime.ofSecondOfDay(timeOfDaySecond.toLong()),
    doseMode = DoseMode.valueOf(doseMode),
    doseValue = doseValue,
    doseAllocation = doseAllocationCsv.decodeDoseAllocationCsv(),
)

fun IntakeLog.toDto() = IntakeLogDto(
    id = id,
    drugId = drugId,
    scheduledIntakeId = scheduledIntakeId,
    intakeTimeId = intakeTimeId,
    occurrenceDateEpochDay = occurrenceDate.toEpochDay(),
    status = status.name,
    actualDateTimeEpochMilli = actualDateTime.toEpochMilli(),
    actualDoseValue = actualDoseValue,
    actualDoseMode = actualDoseMode.name,
    source = source.name,
    createdAtEpochMilli = createdAt.toEpochMilli(),
    updatedAtEpochMilli = updatedAt.toEpochMilli(),
)

fun IntakeLogDto.toEntity() = IntakeLog(
    id = id,
    drugId = drugId,
    scheduledIntakeId = scheduledIntakeId,
    intakeTimeId = intakeTimeId,
    occurrenceDate = LocalDate.ofEpochDay(occurrenceDateEpochDay),
    status = IntakeStatus.valueOf(status),
    actualDateTime = Instant.ofEpochMilli(actualDateTimeEpochMilli),
    actualDoseValue = actualDoseValue,
    actualDoseMode = DoseMode.valueOf(actualDoseMode),
    source = IntakeSource.valueOf(source),
    createdAt = Instant.ofEpochMilli(createdAtEpochMilli),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMilli),
)
