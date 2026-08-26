package app.zelgray.pills_in_time.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.zelgray.pills_in_time.data.repository.DailySessionRepository
import app.zelgray.pills_in_time.data.repository.DrugRepository
import app.zelgray.pills_in_time.data.repository.PatientRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Posts the ongoing "start your day?" prompt for a session-based IntakeTime (see IntakeTime.isSession). */
@HiltWorker
class PostSessionPromptWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val drugRepository: DrugRepository,
    private val patientRepository: PatientRepository,
    private val dailySessionRepository: DailySessionRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val drugId = inputData.getLong(NotificationContracts.EXTRA_DRUG_ID, -1)
        val scheduledIntakeId = inputData.getLong(NotificationContracts.EXTRA_SCHEDULED_INTAKE_ID, -1)
        val intakeTimeId = inputData.getLong(NotificationContracts.EXTRA_INTAKE_TIME_ID, -1)
        val occurrenceDateEpochDay = inputData.getLong(NotificationContracts.EXTRA_OCCURRENCE_DATE_EPOCH_DAY, -1)
        if (drugId < 0 || scheduledIntakeId < 0 || intakeTimeId < 0 || occurrenceDateEpochDay < 0) return Result.failure()

        val date = NotificationContracts.occurrenceDateOf(occurrenceDateEpochDay)
        // Already started (e.g. via the Home "Start day" button before this
        // alarm fired) — nothing left to prompt for today.
        if (dailySessionRepository.getForDate(scheduledIntakeId, date) != null) return Result.success()

        val drug = drugRepository.getById(drugId) ?: return Result.failure()
        val patients = patientRepository.getAllOnce()
        val patient = patients.find { it.id == drug.patientId }
        SessionStatusNotifications.postPrompt(applicationContext, drug, patient, patients.size > 1, scheduledIntakeId, intakeTimeId, date)
        return Result.success()
    }
}
