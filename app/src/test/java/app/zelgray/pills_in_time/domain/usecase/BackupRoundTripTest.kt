package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.DrugForm
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.local.entity.IntakeLog
import app.zelgray.pills_in_time.data.local.entity.IntakeSource
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.Patient
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class BackupRoundTripTest {

    private val exportUseCase = ExportBackupUseCase()
    private val importUseCase = ImportBackupUseCase()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `export then import reproduces the original entities`() {
        val patient = Patient(
            id = 1,
            name = "Me",
            color = -1699246,
            createdAt = Instant.ofEpochSecond(500),
        )
        val drug = Drug(
            id = 1,
            patientId = 1,
            name = "Prednisolone",
            form = DrugForm.OTHER,
            customFormText = "Patch",
            createdAt = Instant.ofEpochSecond(1_000),
        )
        val batch = DrugStockBatch(
            id = 1,
            drugId = 1,
            quantity = 30.0,
            strengthValue = 5.0,
            strengthUnit = StrengthUnit.MG,
            addedAt = Instant.ofEpochSecond(2_000),
        )
        val period = ScheduledIntake(
            id = 1,
            drugId = 1,
            startDate = LocalDate.of(2026, 7, 1),
            endMode = EndMode.DATE,
            endDate = LocalDate.of(2026, 7, 7),
            durationDays = 7,
            cycleType = CycleType.SPECIFIC_DAYS,
            specificDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            customCycleText = null,
            createdAt = Instant.ofEpochSecond(3_000),
        )
        val time = IntakeTime(
            id = 1,
            scheduledIntakeId = 1,
            timeOfDay = LocalTime.of(8, 30),
            doseMode = DoseMode.STRENGTH,
            doseValue = 10.0,
        )
        val log = IntakeLog(
            id = 1,
            drugId = 1,
            scheduledIntakeId = 1,
            intakeTimeId = 1,
            occurrenceDate = LocalDate.of(2026, 7, 3),
            status = IntakeStatus.TAKEN,
            actualDateTime = Instant.ofEpochSecond(4_000),
            actualDoseValue = 10.0,
            actualDoseMode = DoseMode.STRENGTH,
            source = IntakeSource.MANUAL,
            createdAt = Instant.ofEpochSecond(5_000),
            updatedAt = Instant.ofEpochSecond(6_000),
        )

        val payload = exportUseCase(
            patients = listOf(patient),
            drugs = listOf(drug),
            stockBatches = listOf(batch),
            scheduledIntakes = listOf(period),
            intakeTimes = listOf(time),
            intakeLogs = listOf(log),
            exportedAt = Instant.ofEpochSecond(7_000),
            snoozeMinutes = 15,
        )

        // Round-trip through actual JSON text, matching what really happens over the wire.
        val jsonText = json.encodeToString(payload)
        val decoded = json.decodeFromString<app.zelgray.pills_in_time.domain.model.BackupPayload>(jsonText)

        val imported = importUseCase(decoded)

        assertEquals(listOf(patient), imported.patients)
        assertEquals(listOf(drug), imported.drugs)
        assertEquals(listOf(batch), imported.stockBatches)
        assertEquals(listOf(period), imported.scheduledIntakes)
        assertEquals(listOf(time), imported.intakeTimes)
        assertEquals(listOf(log), imported.intakeLogs)
        assertEquals(15, imported.snoozeMinutes)
    }

    @Test
    fun `null-optional fields survive round trip`() {
        val period = ScheduledIntake(
            id = 2,
            drugId = 1,
            startDate = LocalDate.of(2026, 1, 1),
            endMode = EndMode.NONE,
            endDate = null,
            durationDays = null,
            cycleType = CycleType.DAILY,
            specificDays = null,
            customCycleText = null,
            createdAt = Instant.EPOCH,
        )
        val payload = exportUseCase(
            patients = emptyList(),
            drugs = emptyList(),
            stockBatches = emptyList(),
            scheduledIntakes = listOf(period),
            intakeTimes = emptyList(),
            intakeLogs = emptyList(),
            exportedAt = Instant.EPOCH,
            snoozeMinutes = 15,
        )
        val jsonText = json.encodeToString(payload)
        val decoded = json.decodeFromString<app.zelgray.pills_in_time.domain.model.BackupPayload>(jsonText)
        val imported = importUseCase(decoded)

        assertEquals(period, imported.scheduledIntakes.single())
    }

    @Test
    fun `days-on-off cycle fields survive round trip`() {
        val period = ScheduledIntake(
            id = 3,
            drugId = 1,
            startDate = LocalDate.of(2026, 1, 1),
            endMode = EndMode.NONE,
            endDate = null,
            durationDays = null,
            cycleType = CycleType.DAYS_ON_OFF,
            specificDays = null,
            customCycleText = null,
            intakeDays = 5,
            breakDays = 2,
            createdAt = Instant.EPOCH,
        )
        val payload = exportUseCase(
            patients = emptyList(),
            drugs = emptyList(),
            stockBatches = emptyList(),
            scheduledIntakes = listOf(period),
            intakeTimes = emptyList(),
            intakeLogs = emptyList(),
            exportedAt = Instant.EPOCH,
            snoozeMinutes = 15,
        )
        val jsonText = json.encodeToString(payload)
        val decoded = json.decodeFromString<app.zelgray.pills_in_time.domain.model.BackupPayload>(jsonText)
        val imported = importUseCase(decoded)

        assertEquals(period, imported.scheduledIntakes.single())
    }

    @Test
    fun `low-stock reminder fields survive round trip`() {
        val batch = DrugStockBatch(
            id = 4,
            drugId = 1,
            quantity = 16.0,
            strengthValue = 5.0,
            strengthUnit = StrengthUnit.MG,
            addedAt = Instant.EPOCH,
            lowStockReminderDaysBefore = 7,
            lowStockReminderFiredForRunOutDate = LocalDate.of(2026, 8, 1),
        )
        val payload = exportUseCase(
            patients = emptyList(),
            drugs = emptyList(),
            stockBatches = listOf(batch),
            scheduledIntakes = emptyList(),
            intakeTimes = emptyList(),
            intakeLogs = emptyList(),
            exportedAt = Instant.EPOCH,
            snoozeMinutes = 15,
        )
        val jsonText = json.encodeToString(payload)
        val decoded = json.decodeFromString<app.zelgray.pills_in_time.domain.model.BackupPayload>(jsonText)
        val imported = importUseCase(decoded)

        assertEquals(batch, imported.stockBatches.single())
    }

    @Test
    fun `a batch with no strength survives round trip`() {
        val batch = DrugStockBatch(
            id = 5,
            drugId = 1,
            quantity = 3.0,
            strengthValue = null,
            strengthUnit = null,
            addedAt = Instant.EPOCH,
        )
        val payload = exportUseCase(
            patients = emptyList(),
            drugs = emptyList(),
            stockBatches = listOf(batch),
            scheduledIntakes = emptyList(),
            intakeTimes = emptyList(),
            intakeLogs = emptyList(),
            exportedAt = Instant.EPOCH,
            snoozeMinutes = 15,
        )
        val jsonText = json.encodeToString(payload)
        val decoded = json.decodeFromString<app.zelgray.pills_in_time.domain.model.BackupPayload>(jsonText)
        val imported = importUseCase(decoded)

        assertEquals(batch, imported.stockBatches.single())
    }

    @Test
    fun `units-before low-stock reminder fields survive round trip`() {
        val batch = DrugStockBatch(
            id = 7,
            drugId = 1,
            quantity = 4.0,
            strengthValue = 5.0,
            strengthUnit = StrengthUnit.MG,
            addedAt = Instant.EPOCH,
            lowStockReminderUnitsBefore = 5.0,
            lowStockReminderUnitsAlreadyFired = true,
        )
        val payload = exportUseCase(
            patients = emptyList(),
            drugs = emptyList(),
            stockBatches = listOf(batch),
            scheduledIntakes = emptyList(),
            intakeTimes = emptyList(),
            intakeLogs = emptyList(),
            exportedAt = Instant.EPOCH,
            snoozeMinutes = 15,
        )
        val jsonText = json.encodeToString(payload)
        val decoded = json.decodeFromString<app.zelgray.pills_in_time.domain.model.BackupPayload>(jsonText)
        val imported = importUseCase(decoded)

        assertEquals(batch, imported.stockBatches.single())
    }

    @Test
    fun `durationOccurrences survives round trip`() {
        val period = ScheduledIntake(
            id = 6,
            drugId = 1,
            startDate = LocalDate.of(2026, 1, 1),
            endMode = EndMode.OCCURRENCES,
            endDate = LocalDate.of(2026, 3, 1),
            durationDays = null,
            durationOccurrences = 8,
            cycleType = CycleType.SPECIFIC_DAYS,
            specificDays = setOf(DayOfWeek.MONDAY),
            customCycleText = null,
            createdAt = Instant.EPOCH,
        )
        val payload = exportUseCase(
            patients = emptyList(),
            drugs = emptyList(),
            stockBatches = emptyList(),
            scheduledIntakes = listOf(period),
            intakeTimes = emptyList(),
            intakeLogs = emptyList(),
            exportedAt = Instant.EPOCH,
            snoozeMinutes = 15,
        )
        val jsonText = json.encodeToString(payload)
        val decoded = json.decodeFromString<app.zelgray.pills_in_time.domain.model.BackupPayload>(jsonText)
        val imported = importUseCase(decoded)

        assertEquals(period, imported.scheduledIntakes.single())
    }

    @Test
    fun `backup JSON from before snoozeMinutes existed still decodes, with it null`() {
        val legacyJson = """
            {
              "exportedAtEpochMilli": 0,
              "drugs": [],
              "stockBatches": [],
              "scheduledIntakes": [],
              "intakeTimes": [],
              "intakeLogs": []
            }
        """.trimIndent()
        val decoded = json.decodeFromString<app.zelgray.pills_in_time.domain.model.BackupPayload>(legacyJson)
        val imported = importUseCase(decoded)

        assertEquals(null, imported.snoozeMinutes)
    }
}
