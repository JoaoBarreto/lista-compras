package com.example.listacompras.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import com.example.listacompras.R
import com.example.listacompras.ui.shopping.EmptyMessage
import com.example.listacompras.ui.shopping.SyncBanner
import com.example.listacompras.ui.shopping.SyncStatusLabel
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val NEW_EVENT = "\u0000new"

/** The calendar tab: the events of one month, grouped by day. Stateless apart from which dialog is open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    tabs: @Composable () -> Unit,
    onOpenSettings: () -> Unit,
    onErrorAcknowledged: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onSaveEvent: (id: String?, draft: EventDraft, onResult: (Boolean) -> Unit) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onSaveType: (id: String?, name: String, color: String, onResult: (Boolean) -> Unit) -> Unit,
    onDeleteType: (id: String, onResult: (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The id of the event being edited, NEW_EVENT for a new one, null when no editor is open.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var showTypes by rememberSaveable { mutableStateOf(false) }
    val typesById = state.types.associateBy { it.id }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // Opaque, so the agenda scrolling underneath never shows through the month buttons.
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.app_name))
                            SyncStatusLabel(state.syncStatus)
                        }
                    },
                    actions = {
                        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.shopping_button_settings)) }
                    },
                )
                tabs()
                SyncBanner(state.syncStatus, onErrorClick = onErrorAcknowledged)
                MonthBar(state, onPreviousMonth, onNextMonth)
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editing = NEW_EVENT }) {
                Text(stringResource(R.string.calendar_new_event), modifier = Modifier.padding(horizontal = 8.dp))
            }
        },
    ) { innerPadding ->
        if (state.isLoading) return@Scaffold
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        // Back to the grid when the month changes.
        LaunchedEffect(state.month) { listState.scrollToItem(0) }
        // Position of each day's header in the list: item 0 is the grid.
        val headerIndex = remember(state.days) {
            var index = 1
            state.days.associate { day -> (day.date to index).also { index += 1 + day.events.size } }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + FAB_CLEARANCE,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "grid") {
                // Not pinned: these two only matter when the grid is in view, and the bar stays slim.
                MonthActions(onToday = onToday, onManageTypes = { showTypes = true })
                MonthGrid(
                    month = state.month,
                    today = state.today,
                    selected = state.selectedDate,
                    eventsByDay = state.days.associate { it.date to it.events },
                    typesById = typesById,
                    onSelect = { date ->
                        onSelectDate(date)
                        val index = headerIndex[date]
                        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
                        if (index != null && !visible) scope.launch { listState.animateScrollToItem(index) }
                    },
                )
            }
            if (state.days.isEmpty()) {
                item(key = "empty") { EmptyMessage(stringResource(R.string.calendar_empty_month)) }
            }
            state.days.forEach { day ->
                item(key = "day-${day.date}") {
                    Text(
                        text = CalendarFormats.fullDate(day.date),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                            .semantics { heading() },
                    )
                }
                items(day.events, key = { it.id }) { event ->
                    EventRow(event, typesById[event.typeId], state.zone, onClick = { editing = event.id })
                }
            }
        }
    }

    val editingId = editing
    if (editingId != null) {
        val event = state.days.flatMap { it.events }.firstOrNull { it.id == editingId }
        // An event removed meanwhile (for example by the other phone) closes its editor.
        if (editingId == NEW_EVENT || event != null) {
            EventEditorDialog(
                event = event,
                types = state.types,
                defaultDate = state.selectedDate,
                zone = state.zone,
                onSave = { draft, onResult -> onSaveEvent(event?.id, draft, onResult) },
                onDelete = { event?.let { onDeleteEvent(it.id) } },
                onDismiss = { editing = null },
            )
        } else {
            editing = null
        }
    }
    if (showTypes) {
        EventTypesDialog(types = state.types, onSave = onSaveType, onDelete = onDeleteType, onDismiss = { showTypes = false })
    }
}

private val FAB_CLEARANCE = 88.dp

/** Pinned under the tabs: previous month, the month's name, next month. */
@Composable
private fun MonthBar(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        TextButton(onClick = onPreviousMonth) { Text(stringResource(R.string.calendar_previous_month)) }
        Text(
            CalendarFormats.month(state.month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        TextButton(onClick = onNextMonth) { Text(stringResource(R.string.calendar_next_month)) }
    }
}

/** First thing in the list, above the grid; scrolls away with it. */
@Composable
private fun MonthActions(onToday: () -> Unit, onManageTypes: () -> Unit) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        TextButton(onClick = onToday) { Text(stringResource(R.string.calendar_today)) }
        TextButton(onClick = onManageTypes) { Text(stringResource(R.string.calendar_manage_types)) }
    }
}
