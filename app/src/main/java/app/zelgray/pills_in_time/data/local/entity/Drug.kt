package app.zelgray.pills_in_time.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "drugs")
data class Drug(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val form: DrugForm,
    val customFormText: String? = null,
    val createdAt: Instant,
)
