package app.zelgray.pills_in_time.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ChipOption<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true,
)

@Composable
fun <T> ChipSelector(
    options: List<ChipOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.value.toString() }) { option ->
            FilterChip(
                selected = option.value == selected,
                onClick = { onSelect(option.value) },
                enabled = option.enabled,
                label = { Text(option.label) },
            )
        }
    }
}
