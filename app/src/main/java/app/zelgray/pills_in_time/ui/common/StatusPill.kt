package app.zelgray.pills_in_time.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.domain.model.OccurrenceStatus

@Composable
fun StatusPill(status: OccurrenceStatus) {
    val (labelRes, color) = when (status) {
        OccurrenceStatus.UPCOMING -> R.string.status_upcoming to MaterialTheme.colorScheme.onSurfaceVariant
        OccurrenceStatus.OVERDUE -> R.string.status_overdue to MaterialTheme.colorScheme.error
        OccurrenceStatus.TAKEN -> R.string.status_taken to Color(0xFF2E7D32)
        OccurrenceStatus.SKIPPED -> R.string.status_skipped to MaterialTheme.colorScheme.error
        OccurrenceStatus.MISSED -> R.string.status_missed to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
