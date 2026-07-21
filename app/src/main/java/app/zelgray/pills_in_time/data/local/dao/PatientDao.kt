package app.zelgray.pills_in_time.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.zelgray.pills_in_time.data.local.entity.Patient
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {

    @Query("SELECT * FROM patients ORDER BY createdAt")
    fun observeAll(): Flow<List<Patient>>

    @Query("SELECT * FROM patients ORDER BY createdAt")
    suspend fun getAllOnce(): List<Patient>

    @Query("SELECT * FROM patients WHERE id = :patientId")
    suspend fun getById(patientId: Long): Patient?

    @Query("SELECT * FROM patients WHERE id = :patientId")
    fun observeById(patientId: Long): Flow<Patient?>

    @Query("SELECT COUNT(*) FROM patients")
    suspend fun count(): Int

    @Insert
    suspend fun insert(patient: Patient): Long

    @Insert
    suspend fun insertAll(patients: List<Patient>): List<Long>

    @Update
    suspend fun update(patient: Patient)

    @Delete
    suspend fun delete(patient: Patient)

    @Query("DELETE FROM patients")
    suspend fun deleteAllPatients()
}
