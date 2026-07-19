package app.zelgray.pills_in_time.data.local.converter

import androidx.room.TypeConverter
import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.DrugForm
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.local.entity.IntakeSource
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import app.zelgray.pills_in_time.domain.model.DoseComboPiece
import app.zelgray.pills_in_time.domain.model.decodeDoseAllocationCsv
import app.zelgray.pills_in_time.domain.model.encodeToCsv
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Converters {

    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun fromSecondOfDay(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter
    fun localTimeToSecondOfDay(time: LocalTime?): Int? = time?.toSecondOfDay()

    @TypeConverter
    fun fromEpochMilli(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun instantToEpochMilli(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun fromDayOfWeekCsv(value: String?): Set<DayOfWeek>? =
        value?.takeIf { it.isNotEmpty() }
            ?.split(",")
            ?.map { DayOfWeek.of(it.toInt()) }
            ?.toSet()

    @TypeConverter
    fun daysOfWeekToCsv(days: Set<DayOfWeek>?): String? =
        days?.joinToString(",") { it.value.toString() }

    @TypeConverter
    fun fromDrugForm(value: String?): DrugForm? = value?.let { DrugForm.valueOf(it) }

    @TypeConverter
    fun drugFormToString(form: DrugForm?): String? = form?.name

    @TypeConverter
    fun fromStrengthUnit(value: String?): StrengthUnit? = value?.let { StrengthUnit.valueOf(it) }

    @TypeConverter
    fun strengthUnitToString(unit: StrengthUnit?): String? = unit?.name

    @TypeConverter
    fun fromEndMode(value: String?): EndMode? = value?.let { EndMode.valueOf(it) }

    @TypeConverter
    fun endModeToString(mode: EndMode?): String? = mode?.name

    @TypeConverter
    fun fromCycleType(value: String?): CycleType? = value?.let { CycleType.valueOf(it) }

    @TypeConverter
    fun cycleTypeToString(type: CycleType?): String? = type?.name

    @TypeConverter
    fun fromDoseMode(value: String?): DoseMode? = value?.let { DoseMode.valueOf(it) }

    @TypeConverter
    fun doseModeToString(mode: DoseMode?): String? = mode?.name

    @TypeConverter
    fun fromIntakeStatus(value: String?): IntakeStatus? = value?.let { IntakeStatus.valueOf(it) }

    @TypeConverter
    fun intakeStatusToString(status: IntakeStatus?): String? = status?.name

    @TypeConverter
    fun fromIntakeSource(value: String?): IntakeSource? = value?.let { IntakeSource.valueOf(it) }

    @TypeConverter
    fun intakeSourceToString(source: IntakeSource?): String? = source?.name

    @TypeConverter
    fun fromDoseAllocationCsv(value: String?): List<DoseComboPiece>? = value.decodeDoseAllocationCsv()

    @TypeConverter
    fun doseAllocationToCsv(pieces: List<DoseComboPiece>?): String? = pieces?.encodeToCsv()
}
