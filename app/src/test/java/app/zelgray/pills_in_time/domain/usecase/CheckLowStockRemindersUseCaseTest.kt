package app.zelgray.pills_in_time.domain.usecase

import app.zelgray.pills_in_time.data.local.entity.CycleType
import app.zelgray.pills_in_time.data.local.entity.DoseMode
import app.zelgray.pills_in_time.data.local.entity.DrugStockBatch
import app.zelgray.pills_in_time.data.local.entity.EndMode
import app.zelgray.pills_in_time.data.local.entity.IntakeTime
import app.zelgray.pills_in_time.data.local.entity.ScheduledIntake
import app.zelgray.pills_in_time.data.local.entity.StrengthUnit
import app.zelgray.pills_in_time.data.local.relation.ScheduledIntakeWithTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class CheckLowStockRemindersUseCaseTest {

    private val useCase = CheckLowStockRemindersUseCase(ProjectDrugStockUseCase(ResolveDoseConsumptionUseCase(FindDoseCombosUseCase())))
    private val today = LocalDate.of(2026, 7, 17)

    private fun batch(
        id: Long = 1,
        drugId: Long = 1,
        quantity: Double = 10.0,
        strength: Double = 1.0,
        daysBefore: Int? = 3,
        firedFor: LocalDate? = null,
        unitsBefore: Double? = null,
        unitsAlreadyFired: Boolean = false,
        addedAt: Instant = Instant.EPOCH,
    ) = DrugStockBatch(
        id = id,
        drugId = drugId,
        quantity = quantity,
        strengthValue = strength,
        strengthUnit = StrengthUnit.MG,
        addedAt = addedAt,
        lowStockReminderDaysBefore = daysBefore,
        lowStockReminderFiredForRunOutDate = firedFor,
        lowStockReminderUnitsBefore = unitsBefore,
        lowStockReminderUnitsAlreadyFired = unitsAlreadyFired,
    )

    private fun dailyPeriod(drugId: Long = 1, dosePerDay: Double = 1.0) = ScheduledIntakeWithTimes(
        scheduledIntake = ScheduledIntake(
            id = 1,
            drugId = drugId,
            startDate = today,
            endMode = EndMode.NONE,
            endDate = null,
            durationDays = null,
            cycleType = CycleType.DAILY,
            specificDays = null,
            customCycleText = null,
            createdAt = Instant.EPOCH,
        ),
        times = listOf(
            IntakeTime(id = 1, scheduledIntakeId = 1, timeOfDay = LocalTime.of(8, 0), doseMode = DoseMode.UNITS, doseValue = dosePerDay),
        ),
    )

    @Test
    fun `batch without a configured reminder is ignored`() {
        val alerts = useCase(
            listOf(batch(daysBefore = null)),
            mapOf(1L to listOf(dailyPeriod())),
            today,
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `run-out within the notice window fires an alert`() {
        // quantity=10, 1/day -> exhausts on today+9, notice window is 3 days, not within range yet
        val alertsFar = useCase(listOf(batch(quantity = 10.0, daysBefore = 3)), mapOf(1L to listOf(dailyPeriod())), today)
        assertTrue(alertsFar.isEmpty())

        // quantity=2, 1/day -> the batch's own quantity hits zero after today+1's dose, within a 3-day notice window
        val alertsNear = useCase(listOf(batch(id = 2, quantity = 2.0, daysBefore = 3)), mapOf(1L to listOf(dailyPeriod())), today)
        assertEquals(1, alertsNear.size)
        assertEquals(today.plusDays(1), alertsNear.single().runOutDate)
    }

    @Test
    fun `already notified for this exact run-out date is not repeated`() {
        val runOutDate = today.plusDays(1)
        val alerts = useCase(
            listOf(batch(quantity = 2.0, daysBefore = 3, firedFor = runOutDate)),
            mapOf(1L to listOf(dailyPeriod())),
            today,
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `forecast shifting to a new run-out date re-fires even if previously notified`() {
        // previously notified for an older forecast; consumption rate changed so the real run-out date differs now
        val alerts = useCase(
            listOf(batch(quantity = 2.0, daysBefore = 3, firedFor = today.plusDays(99))),
            mapOf(1L to listOf(dailyPeriod())),
            today,
        )
        assertEquals(1, alerts.size)
        assertEquals(today.plusDays(1), alerts.single().runOutDate)
    }

    @Test
    fun `no periods for the drug yields no alert`() {
        val alerts = useCase(listOf(batch(quantity = 1.0, daysBefore = 30)), emptyMap(), today)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `sufficient long term stock yields no alert`() {
        val alerts = useCase(
            listOf(batch(quantity = 100_000.0, daysBefore = 30)),
            mapOf(1L to listOf(dailyPeriod())),
            today,
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `each batch is evaluated within the drug's combined FIFO consumption`() {
        // Same strength, same addedAt -> the older-in-list batch is consumed
        // first by FIFO, so only it is projected to run low within the window.
        val alerts = useCase(
            listOf(
                batch(id = 1, drugId = 1, quantity = 2.0, daysBefore = 3),
                batch(id = 2, drugId = 1, quantity = 50.0, daysBefore = 3),
            ),
            mapOf(1L to listOf(dailyPeriod())),
            today,
        )
        assertEquals(1, alerts.size)
        assertEquals(1L, alerts.single().batchId)
    }

    @Test
    fun `units-before reminder fires once quantity drops to or below the threshold`() {
        val above = useCase(
            listOf(batch(quantity = 10.0, daysBefore = null, unitsBefore = 5.0)),
            mapOf(1L to listOf(dailyPeriod())),
            today,
        )
        assertTrue(above.isEmpty())

        val atThreshold = useCase(
            listOf(batch(quantity = 5.0, daysBefore = null, unitsBefore = 5.0)),
            mapOf(1L to listOf(dailyPeriod())),
            today,
        )
        assertEquals(1, atThreshold.size)
        assertEquals(1L, atThreshold.single().batchId)
    }

    @Test
    fun `units-before reminder does not repeat once already fired`() {
        val alerts = useCase(
            listOf(batch(quantity = 2.0, daysBefore = null, unitsBefore = 5.0, unitsAlreadyFired = true)),
            mapOf(1L to listOf(dailyPeriod())),
            today,
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `units-before reminder fires even without an active period to forecast against`() {
        val alerts = useCase(
            listOf(batch(quantity = 2.0, daysBefore = null, unitsBefore = 5.0)),
            emptyMap(),
            today,
        )
        assertEquals(1, alerts.size)
        assertEquals(null, alerts.single().runOutDate)
    }
}
