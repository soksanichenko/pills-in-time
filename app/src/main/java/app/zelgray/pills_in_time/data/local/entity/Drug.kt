package app.zelgray.pills_in_time.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "drugs",
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("patientId")],
)
data class Drug(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val name: String,
    val form: DrugForm,
    val customFormText: String? = null,
    val createdAt: Instant,
)
