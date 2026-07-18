package app.zelgray.pills_in_time.data.repository

import android.content.Context
import androidx.room.withTransaction
import app.zelgray.pills_in_time.data.local.MedTrackerDatabase
import app.zelgray.pills_in_time.data.local.dao.DrugDao
import app.zelgray.pills_in_time.data.local.dao.IntakeLogDao
import app.zelgray.pills_in_time.data.local.dao.IntakeTimeDao
import app.zelgray.pills_in_time.data.local.dao.ScheduleDao
import app.zelgray.pills_in_time.data.local.dao.ScheduledAlarmDao
import app.zelgray.pills_in_time.data.local.dao.StockBatchDao
import app.zelgray.pills_in_time.data.remote.drive.DriveApi
import app.zelgray.pills_in_time.data.remote.drive.DriveAuthManager
import app.zelgray.pills_in_time.domain.model.BackupPayload
import app.zelgray.pills_in_time.domain.usecase.ExportBackupUseCase
import app.zelgray.pills_in_time.domain.usecase.ImportBackupUseCase
import app.zelgray.pills_in_time.notification.AlarmScheduler
import app.zelgray.pills_in_time.notification.DailyRescheduleWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import javax.inject.Inject

private const val BACKUP_FILE_NAME = "medtracker_backup.json"

class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MedTrackerDatabase,
    private val drugDao: DrugDao,
    private val stockBatchDao: StockBatchDao,
    private val scheduleDao: ScheduleDao,
    private val intakeTimeDao: IntakeTimeDao,
    private val intakeLogDao: IntakeLogDao,
    private val scheduledAlarmDao: ScheduledAlarmDao,
    private val alarmScheduler: AlarmScheduler,
    private val driveAuthManager: DriveAuthManager,
    private val driveApi: DriveApi,
    private val exportBackupUseCase: ExportBackupUseCase,
    private val importBackupUseCase: ImportBackupUseCase,
    private val json: Json,
) {
    suspend fun requestAuthorization() = driveAuthManager.requestAuthorization()

    fun resultFromIntent(data: android.content.Intent?) = driveAuthManager.resultFromIntent(data)

    suspend fun backup(accessToken: String) {
        val payload = exportBackupUseCase(
            drugs = drugDao.getAllOnce(),
            stockBatches = stockBatchDao.getAllOnce(),
            scheduledIntakes = scheduleDao.getAllOnce(),
            intakeTimes = intakeTimeDao.getAllOnce(),
            intakeLogs = intakeLogDao.getAllLogsOnce(),
            exportedAt = Instant.now(),
        )
        val jsonText = json.encodeToString(BackupPayload.serializer(), payload)
        val authHeader = "Bearer $accessToken"
        val existingFileId = findExistingBackupFileId(authHeader)

        val mediaType = "application/json; charset=UTF-8".toMediaType()
        val metadataJson = if (existingFileId == null) {
            """{"name":"$BACKUP_FILE_NAME","parents":["appDataFolder"]}"""
        } else {
            "{}"
        }
        val metadataPart = MultipartBody.Part.createFormData(
            "metadata",
            null,
            metadataJson.toRequestBody(mediaType),
        )
        val filePart = MultipartBody.Part.createFormData(
            "file",
            null,
            jsonText.toRequestBody(mediaType),
        )

        if (existingFileId == null) {
            driveApi.uploadNewFile(authHeader, metadataPart, filePart)
        } else {
            driveApi.updateFile(authHeader, existingFileId, metadataPart, filePart)
        }
    }

    suspend fun restore(accessToken: String) {
        val authHeader = "Bearer $accessToken"
        val fileId = findExistingBackupFileId(authHeader) ?: error("No backup file found in Google Drive")
        val jsonText = driveApi.downloadFile(authHeader, fileId).use { it.string() }
        val payload = json.decodeFromString(BackupPayload.serializer(), jsonText)
        val imported = importBackupUseCase(payload)

        // Cancel every currently-registered alarm before wiping the registry —
        // otherwise stale AlarmManager alarms referencing pre-restore IDs would
        // still fire (harmlessly failing to find their drug, but wastefully).
        scheduledAlarmDao.getAll().forEach { alarmScheduler.cancel(it.requestCode) }

        database.withTransaction {
            intakeLogDao.deleteAllLogs()
            intakeTimeDao.deleteAllTimes()
            scheduleDao.deleteAllSchedules()
            stockBatchDao.deleteAllBatches()
            drugDao.deleteAllDrugs()
            scheduledAlarmDao.deleteAll()

            drugDao.insertAll(imported.drugs)
            stockBatchDao.insertAll(imported.stockBatches)
            scheduleDao.insertAll(imported.scheduledIntakes)
            intakeTimeDao.insertAll(imported.intakeTimes)
            intakeLogDao.insertAll(imported.intakeLogs)
        }

        DailyRescheduleWorker.enqueueNow(context)
    }

    private suspend fun findExistingBackupFileId(authHeader: String): String? {
        val response = driveApi.listAppDataFiles(authHeader, query = "name='$BACKUP_FILE_NAME'")
        return response.files.firstOrNull()?.id
    }
}
