package com.example.listacompras.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.listacompras.R
import com.example.listacompras.domain.model.CalendarLimits
import com.example.listacompras.domain.model.CalendarEvent
import com.example.listacompras.domain.model.EventType
import com.example.listacompras.domain.model.Reminder
import java.time.LocalDate
import java.time.LocalTime

/**
 * Creates ([event] null) or edits an event. The dialog stays open if the repository refuses the
 * save. Saving needs a type, because an event always has one.
 */
@Composable
fun EventEditorDialog(
    event: CalendarEvent?,
    types: List<EventType>,
    today: LocalDate,
    zone: java.time.ZoneId,
    onSave: (EventDraft, (Boolean) -> Unit) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(event?.title.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(event?.notes.orEmpty()) }
    var epochDay by rememberSaveable {
        mutableLongStateOf((event?.let { CalendarDates.dateOf(it, zone) } ?: today).toEpochDay())
    }
    var minuteOfDay by rememberSaveable {
        mutableIntStateOf((event?.let { CalendarDates.timeOf(it, zone) } ?: LocalTime.of(9, 0)).toSecondOfDay() / 60)
    }
    var allDay by rememberSaveable { mutableStateOf(event?.allDay ?: false) }
    var typeId by rememberSaveable { mutableStateOf(event?.typeId ?: types.firstOrNull()?.id) }
    var reminder by rememberSaveable { mutableStateOf(event?.reminder?.slug) }
    var failed by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(Picker.NONE) }

    val date = LocalDate.ofEpochDay(epochDay)
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    val selectedType = types.firstOrNull { it.id == typeId }
    val canSave = title.isNotBlank() && selectedType != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (event == null) R.string.event_editor_new else R.string.event_editor_edit)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(CalendarLimits.TITLE_MAX); failed = false },
                    label = { Text(stringResource(R.string.event_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(CalendarLimits.NOTES_MAX) },
                    label = { Text(stringResource(R.string.event_notes_label)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { picking = Picker.DATE }) {
                        Text(stringResource(R.string.event_date_label, CalendarFormats.shortDate(date)))
                    }
                    if (!allDay) {
                        TextButton(onClick = { picking = Picker.TIME }) {
                            Text(stringResource(R.string.event_time_label, CalendarFormats.time(time)))
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = allDay, role = Role.Switch, onValueChange = { allDay = it }),
                ) {
                    Text(stringResource(R.string.event_all_day), modifier = Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = null)
                }
                ChoiceField(
                    label = stringResource(R.string.event_type_label, selectedType?.name ?: stringResource(R.string.calendar_unknown_type)),
                    options = types.map { type -> type.name to { typeId = type.id } },
                    enabled = types.isNotEmpty(),
                )
                if (types.isEmpty()) {
                    Text(stringResource(R.string.event_no_types), style = MaterialTheme.typography.bodySmall)
                }
                ChoiceField(
                    label = stringResource(R.string.event_reminder_label, reminderLabel(Reminder.fromSlug(reminder))),
                    options = listOf<Reminder?>(null).plus(Reminder.entries).map { option ->
                        reminderLabel(option) to { reminder = option?.slug }
                    },
                )
                if (failed) {
                    Text(
                        stringResource(R.string.event_save_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val chosenType = typeId ?: return@TextButton
                    onSave(EventDraft(title, notes, date, time, allDay, chosenType, Reminder.fromSlug(reminder))) { saved ->
                        if (saved) onDismiss() else failed = true
                    }
                },
            ) { Text(stringResource(R.string.event_save)) }
        },
        dismissButton = {
            Row {
                if (event != null) {
                    TextButton(onClick = { onDelete(); onDismiss() }) { Text(stringResource(R.string.event_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.event_cancel)) }
            }
        },
    )
    when (picking) {
        Picker.DATE -> DatePickerPopup(date, onPicked = { epochDay = it.toEpochDay() }, onDismiss = { picking = Picker.NONE })
        Picker.TIME -> TimePickerPopup(
            time,
            onPicked = { minuteOfDay = it.hour * 60 + it.minute },
            onDismiss = { picking = Picker.NONE },
        )
        Picker.NONE -> Unit
    }
}

private enum class Picker { NONE, DATE, TIME }
