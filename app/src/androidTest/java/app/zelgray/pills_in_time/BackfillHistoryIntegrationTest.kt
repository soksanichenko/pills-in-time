package app.zelgray.pills_in_time

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DrugForm
import app.zelgray.pills_in_time.data.local.entity.IntakeStatus
import app.zelgray.pills_in_time.data.local.entity.IntakeSource
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.IntakeRepository
import app.zelgray.pills_in_time.data.repository.IntakeTimeInput
import app.zelgray.pills_in_time.data.repository.PatientRepository
import app.zelgray.pills_in_time.data.repository.ScheduleRepository
import app.zelgray.pills_in_time.data.repository.StockRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Integration test (real in-memory Room DB, see TestDatabaseModule) for
 * "fill in history" (BackfillHistoryUseCase + IntakeRepository.backfillHistoryForPeriod)
 * — offered when saving a brand-new, backdated period, so re-adding a period
 * that was deleted (which cascades away its whole history) doesn't leave a
 * permanent gap for the days already covered by the backdated start date.
 * Also covers IntakeRepository.writeLog's backdated-doesn't-touch-stock rule
 * more generally, since backfill is just one caller of it.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackfillHistoryIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var patientRepository: PatientRepository
    @Inject lateinit var drugRepository: DrugRepository
    @Inject lateinit var stockRepository: StockRepository
    @Inject lateinit var scheduleRepository: ScheduleRepository
    @Inject lateinit var intakeRepository: IntakeRepository

    @Test
    fun backfillingA3DayBackdatedPeriod_fillsThreeTakenLogsWithoutTouchingStock() = runBlocking {
        hiltRule.inject()

        val patientId = patientRepository.createPatient("Test", 0)
        val drugId = drugRepository.createDrug(patientId, "Metformin", DrugForm.TABLET, null)
        val batchId = stockRepository.createBatch(drugId, quantity = 30.0, strengthValue = null, strengthUnit = null)

        val today = LocalDate.now()
        val startDate = today.minusDays(3)
        val scheduleId = scheduleRepository.savePeriod(
            scheduleId = null,
            drugId = drugId,
            startDate = startDate,
            endMode = EndMode.NONE,
            endDate = null,
            durationDays = null,
            cycleType = CycleType.DAILY,
            specificDays = null,
            customCycleText = null,
            times = listOf(
                IntakeTimeInput(id = 0, timeOfDay = LocalTime.of(8, 0), doseMode = DoseMode.UNITS, doseValue = 1.0),
            ),
        )

        val filled = intakeRepository.backfillHistoryForPeriod(scheduleId, drugId)

        assertEquals(3, filled)

        // Each of the 3 past days got its own TAKEN, MANUAL-sourced log.
        val timeId = scheduleRepository.getTimesForSchedule(scheduleId).first().id
        for (offset in 0..2) {
            val log = intakeRepository.getLogForOccurrenceOnce(scheduleId, timeId, startDate.plusDays(offset.toLong()))
            assertEquals(IntakeStatus.TAKEN, log?.status)
            assertEquals(IntakeSource.MANUAL, log?.source)
        }
        // Today itself is untouched by the backfill.
        assertEquals(null, intakeRepository.getLogForOccurrenceOnce(scheduleId, timeId, today))

        // Backdated doses reflect consumption that already happened before
        // today's physical count, so current stock is left untouched.
        val batch = stockRepository.getById(batchId)
        assertEquals(30.0, batch?.quantity)
    }

    @Test
    fun manualEntry_backdatedLeavesStockUntouched_sameDayStillConsumesIt() = runBlocking {
        hiltRule.inject()

        val patientId = patientRepository.createPatient("Test", 0)
        val drugId = drugRepository.createDrug(patientId, "Metformin", DrugForm.TABLET, null)
        val batchId = stockRepository.createBatch(drugId, quantity = 10.0, strengthValue = null, strengthUnit = null)
        val today = LocalDate.now()
        val scheduleId = scheduleRepository.savePeriod(
            scheduleId = null,
            drugId = drugId,
            startDate = today.minusDays(1),
            endMode = EndMode.NONE,
            endDate = null,
            durationDays = null,
            cycleType = CycleType.DAILY,
            specificDays = null,
            customCycleText = null,
            times = listOf(IntakeTimeInput(id = 0, timeOfDay = LocalTime.of(8, 0), doseMode = DoseMode.UNITS, doseValue = 1.0)),
        )
        val timeId = scheduleRepository.getTimesForSchedule(scheduleId).first().id

        intakeRepository.recordManualEntry(
            drugId = drugId,
            scheduledIntakeId = scheduleId,
            intakeTimeId = timeId,
            occurrenceDate = today.minusDays(1),
            actualDateTime = today.minusDays(1).atTime(8, 0).atZone(java.time.ZoneId.systemDefault()).toInstant(),
            doseValue = 1.0,
            doseMode = DoseMode.UNITS,
            status = IntakeStatus.TAKEN,
        )
        assertEquals(10.0, stockRepository.getById(batchId)?.quantity)

        intakeRepository.recordManualEntry(
            drugId = drugId,
            scheduledIntakeId = scheduleId,
            intakeTimeId = timeId,
            occurrenceDate = today,
            actualDateTime = today.atTime(8, 0).atZone(java.time.ZoneId.systemDefault()).toInstant(),
            doseValue = 1.0,
            doseMode = DoseMode.UNITS,
            status = IntakeStatus.TAKEN,
        )
        assertEquals(9.0, stockRepository.getById(batchId)?.quantity)
    }
}
