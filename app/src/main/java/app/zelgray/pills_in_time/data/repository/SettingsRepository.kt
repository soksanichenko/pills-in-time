package app.zelgray.pills_in_time.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val snoozeMinutes: Flow<Int> = context.settingsDataStore.data.map { it[SNOOZE_MINUTES_KEY] ?: DEFAULT_SNOOZE_MINUTES }

    suspend fun getSnoozeMinutesOnce(): Int = snoozeMinutes.first()

    suspend fun setSnoozeMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[SNOOZE_MINUTES_KEY] = minutes }
    }

    val driveConnected: Flow<Boolean> = context.settingsDataStore.data.map { it[DRIVE_CONNECTED_KEY] ?: false }

    suspend fun setDriveConnected(connected: Boolean) {
        context.settingsDataStore.edit { it[DRIVE_CONNECTED_KEY] = connected }
    }

    val lastBackupEpochMilli: Flow<Long?> = context.settingsDataStore.data.map { it[LAST_BACKUP_KEY] }

    suspend fun setLastBackupEpochMilli(epochMilli: Long) {
        context.settingsDataStore.edit { it[LAST_BACKUP_KEY] = epochMilli }
    }

    // Null until the user ever switches patients, or if the previously
    // selected one no longer exists — PatientRepository falls back to the
    // first patient in either case.
    val selectedPatientId: Flow<Long?> = context.settingsDataStore.data.map { it[SELECTED_PATIENT_ID_KEY] }

    suspend fun setSelectedPatientId(patientId: Long) {
        context.settingsDataStore.edit { it[SELECTED_PATIENT_ID_KEY] = patientId }
    }

    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 15
        private val SNOOZE_MINUTES_KEY = intPreferencesKey("snooze_minutes")
        private val DRIVE_CONNECTED_KEY = booleanPreferencesKey("drive_connected")
        private val LAST_BACKUP_KEY = longPreferencesKey("last_backup_epoch_milli")
        private val SELECTED_PATIENT_ID_KEY = longPreferencesKey("selected_patient_id")
    }
}
