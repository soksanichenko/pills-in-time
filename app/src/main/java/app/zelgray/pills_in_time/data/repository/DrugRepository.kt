package app.zelgray.pills_in_time.data.repository

import android.content.Context
import app.zelgray.pills_in_time.data.local.dao.DrugDao
import app.zelgray.pills_in_time.data.local.entity.Drug
import app.zelgray.pills_in_time.data.local.entity.DrugForm
import app.zelgray.pills_in_time.notification.DailyRescheduleWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

class DrugRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val drugDao: DrugDao,
) {
    fun observeAllDrugs(): Flow<List<Drug>> = drugDao.observeAllDrugs()

    fun observeById(drugId: Long): Flow<Drug?> = drugDao.observeById(drugId)

    suspend fun getById(drugId: Long): Drug? = drugDao.getById(drugId)

    suspend fun hasSchedulesOrStock(drugId: Long): Boolean = drugDao.hasSchedulesOrStock(drugId)

    suspend fun createDrug(name: String, form: DrugForm, customFormText: String?): Long =
        drugDao.insert(
            Drug(
                name = name,
                form = form,
                customFormText = customFormText,
                createdAt = Instant.now(),
            ),
        )

    suspend fun updateDrug(drug: Drug) = drugDao.update(drug)

    suspend fun deleteDrug(drug: Drug) {
        drugDao.delete(drug)
        // Cascade-deletes the drug's periods (FK CASCADE) — alarms for them
        // must be cancelled too (spec 4.5: recalculate on schedule changes).
        DailyRescheduleWorker.enqueueNow(context)
    }
}
