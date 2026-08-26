package app.zelgray.pills_in_time.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * One day's run of a session-based IntakeTime (see IntakeTime.isSession):
 * anchors when the patient actually started their day (startedAt, set by the
 * "День начат" action) and, once closed, when it ended (endedAt — either the
 * explicit "Иду спать" action, or automatic once a COUNT_PER_DAY session's
 * target dose count is reached). Null endedAt means the session is still
 * active. At most one row per (scheduledIntakeId, date).
 */
@Entity(
    tableName = "daily_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledIntake::class,
            parentColumns = ["id"],
            childColumns = ["scheduledIntakeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["scheduledIntakeId", "date"], unique = true)],
)
data class DailySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledIntakeId: Long,
    val date: LocalDate,
    val startedAt: Instant,
    val endedAt: Instant? = null,
)
