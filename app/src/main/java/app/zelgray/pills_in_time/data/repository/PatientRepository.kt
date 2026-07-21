package app.zelgray.pills_in_time.data.repository

import android.content.Context
import app.zelgray.pills_in_time.data.local.dao.PatientDao
import app.zelgray.pills_in_time.data.local.entity.Patient
import app.zelgray.pills_in_time.domain.model.PatientColorPalette
import app.zelgray.pills_in_time.notification.DailyRescheduleWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject

class PatientRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val patientDao: PatientDao,
    private val settingsRepository: SettingsRepository,
) {
    fun observeAll(): Flow<List<Patient>> = patientDao.observeAll()

    suspend fun getAllOnce(): List<Patient> = patientDao.getAllOnce()

    fun observeById(patientId: Long): Flow<Patient?> = patientDao.observeById(patientId)

    suspend fun getById(patientId: Long): Patient? = patientDao.getById(patientId)

    /**
     * The patient every screen should currently be scoped to: the last one
     * the user explicitly switched to, falling back to the first patient by
     * creation order if none was ever picked (or the picked one got deleted).
     * There is always at least one patient (seeded on first DB creation), so
     * this never has to represent "no patient selected".
     */
    fun observeCurrentPatientId(): Flow<Long> =
        combine(settingsRepository.selectedPatientId, observeAll()) { selectedId, patients ->
            (patients.firstOrNull { it.id == selectedId } ?: patients.first()).id
        }

    suspend fun setCurrentPatientId(patientId: Long) = settingsRepository.setSelectedPatientId(patientId)

    suspend fun createPatient(name: String, color: Int): Long =
        patientDao.insert(Patient(name = name, color = color, createdAt = Instant.now()))

    suspend fun nextDefaultColor(): Int = PatientColorPalette.colorForIndex(patientDao.count())

    suspend fun updatePatient(patient: Patient) = patientDao.update(patient)

    suspend fun deletePatient(patient: Patient) {
        patientDao.delete(patient)
        // Cascade-deletes the patient's drugs and everything under them (FK
        // CASCADE) — alarms for those must be cancelled too, same reasoning
        // as DrugRepository.deleteDrug.
        DailyRescheduleWorker.enqueueNow(context)
    }
}
