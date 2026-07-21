package app.zelgray.pills_in_time.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    // ARGB int chosen from PatientColorPalette — accents this patient's
    // notifications and the app-wide Material theme while they're selected.
    val color: Int,
    val createdAt: Instant,
)
