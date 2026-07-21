@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.zelgray.pills_in_time.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zelgray.pills_in_time.R
import app.zelgray.pills_in_time.domain.model.OccurrenceStatus
import app.zelgray.pills_in_time.notification.AlarmPermissions
import app.zelgray.pills_in_time.notification.OccurrenceRequest
import app.zelgray.pills_in_time.ui.common.PatientSwitcherAction
import app.zelgray.pills_in_time.ui.common.StatusPill
import app.zelgray.pills_in_time.ui.common.dayLabel
import app.zelgray.pills_in_time.ui.drugs.doseText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    pendingOccurrenceRequest: OccurrenceRequest? = null,
    onPendingOccurrenceConsumed: () -> Unit = {},
    onManagePatients: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toastMessageRes by viewModel.toastMessageRes.collectAsStateWithLifecycle()
    var sheetItem by remember { mutableStateOf<HomeListItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(toastMessageRes) {
        toastMessageRes?.let { res ->
            snackbarHostState.showSnackbar(message = context.getString(res))
            viewModel.consumeToast()
        }
    }

    LaunchedEffect(pendingOccurrenceRequest, state.date, state.items) {
        val request = pendingOccurrenceRequest ?: return@LaunchedEffect
        if (state.date != request.occurrenceDate) {
            viewModel.goToDate(request.occurrenceDate)
            return@LaunchedEffect
        }
        val match = state.items.firstOrNull {
            it.occurrence.scheduledIntakeId == request.scheduledIntakeId && it.occurrence.intakeTimeId == request.intakeTimeId
        }
        if (match != null) {
            sheetItem = match
            onPendingOccurrenceConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_home)) },
                actions = { PatientSwitcherAction(onManagePatients = onManagePatients) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ExactAlarmBanner()

            DayNavigator(
                date = state.date,
                today = state.today,
                onPrev = viewModel::onPrevDay,
                onNext = viewModel::onNextDay,
                onDateSelected = viewModel::goToDate,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .swipeDayNavigation(onPrev = viewModel::onPrevDay, onNext = viewModel::onNextDay),
            ) {
                if (state.items.isEmpty() && !state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.home_empty),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(state.items, key = { it.occurrence.intakeTimeId.toString() + "_" + it.occurrence.occurrenceDate }) { item ->
                            HomeRow(
                                item = item,
                                onCheckClick = { viewModel.onTookIt(item) },
                                onRowClick = { sheetItem = item },
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                )
            }
        }
    }

    sheetItem?.let { item ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { sheetItem = null }, sheetState = sheetState) {
            HomeActionSheetContent(
                item = item,
                onTookIt = {
                    viewModel.onTookIt(item)
                    sheetItem = null
                },
                onSkipped = {
                    viewModel.onSkipped(item)
                    sheetItem = null
                },
                onSnooze = {
                    viewModel.onSnooze(item)
                    sheetItem = null
                },
                onCancel = {
                    viewModel.onCancelStatus(item)
                    sheetItem = null
                },
            )
        }
    }
}

@Composable
private fun ExactAlarmBanner() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(AlarmPermissions.canScheduleExactAlarms(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = AlarmPermissions.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!granted) {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.exact_alarm_rationale),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = { context.startActivity(AlarmPermissions.exactAlarmSettingsIntent(context)) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.exact_alarm_enable))
                }
            }
        }
    }
}

@Composable
private fun DayNavigator(date: LocalDate, today: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onDateSelected: (LocalDate) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.previous_day))
        }
        Text(
            text = dayLabel(date, today),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clickable { showDatePicker = true },
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.next_day))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onDateSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * Swipe-to-change-day: only activates for a drag that starts in the bottom
 * half (matching the thin hint strip shown there); per-event dominant-axis
 * consumption lets the underlying LazyColumn's own vertical scroll still work
 * for drags that aren't clearly horizontal.
 */
private fun Modifier.swipeDayNavigation(onPrev: () -> Unit, onNext: () -> Unit): Modifier = pointerInput(onPrev, onNext) {
    val thresholdPx = 56.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        if (down.position.y < size.height / 2f) return@awaitEachGesture
        var totalDrag = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val delta = change.positionChange()
            if (abs(delta.x) > abs(delta.y)) {
                change.consume()
                totalDrag += delta.x
            }
        }
        if (totalDrag > thresholdPx) onPrev() else if (totalDrag < -thresholdPx) onNext()
    }
}

@Composable
private fun HomeRow(item: HomeListItem, onCheckClick: () -> Unit, onRowClick: () -> Unit) {
    val canCheck = item.occurrence.status == OccurrenceStatus.UPCOMING || item.occurrence.status == OccurrenceStatus.OVERDUE
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = onRowClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCheckClick, enabled = canCheck) {
                Icon(
                    if (item.occurrence.status == OccurrenceStatus.TAKEN) Icons.Filled.CheckCircle else Icons.Filled.CheckCircleOutline,
                    contentDescription = stringResource(R.string.action_took_it),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = item.occurrence.timeOfDay.format(DateTimeFormatter.ofPattern("HH:mm")) + "  " + item.drug.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = doseText(item.occurrence.doseValue, item.occurrence.doseMode, item.drug, item.stockBatches, item.effectiveStrength),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(item.occurrence.status)
        }
    }
}

@Composable
private fun HomeActionSheetContent(
    item: HomeListItem,
    onTookIt: () -> Unit,
    onSkipped: () -> Unit,
    onSnooze: () -> Unit,
    onCancel: () -> Unit,
) {
    val alreadyLogged = item.occurrence.status == OccurrenceStatus.TAKEN || item.occurrence.status == OccurrenceStatus.SKIPPED
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = item.occurrence.timeOfDay.format(DateTimeFormatter.ofPattern("HH:mm")) + " · " + item.drug.name + " · " +
                doseText(
                    item.occurrence.doseValue,
                    item.occurrence.doseMode,
                    item.drug,
                    item.stockBatches,
                    item.effectiveStrength,
                    item.occurrence.doseAllocation,
                ),
            style = MaterialTheme.typography.titleLarge,
        )
        Column(modifier = Modifier.padding(top = 16.dp)) {
            if (alreadyLogged) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_undo_status))
                }
            } else {
                Button(
                    onClick = onTookIt,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Text(stringResource(R.string.action_took_it))
                }
                OutlinedButton(onClick = onSkipped, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(stringResource(R.string.action_skipped))
                }
                OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_snooze))
                }
            }
        }
    }
}
