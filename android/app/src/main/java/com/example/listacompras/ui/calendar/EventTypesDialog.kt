package com.example.listacompras.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.listacompras.R
import com.example.listacompras.domain.model.CalendarLimits
import com.example.listacompras.domain.model.EventType

private const val NEW_TYPE = "\u0000new"

/** Lists the types; creating or editing one swaps the list for a small form inside the same dialog. */
@Composable
fun EventTypesDialog(
    types: List<EventType>,
    onSave: (id: String?, name: String, color: String, onResult: (Boolean) -> Unit) -> Unit,
    onDelete: (id: String, onResult: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var inUse by rememberSaveable { mutableStateOf(false) }
    val editedType = types.firstOrNull { it.id == editing }

    // A type deleted meanwhile (for example on the other phone) closes its form instead of creating a duplicate.
    if (editing != null && editing != NEW_TYPE && editedType == null) {
        editing = null
        return
    }
    if (editing != null) {
        TypeFormDialog(
            type = editedType,
            onSave = { name, color, onResult -> onSave(editedType?.id, name, color, onResult) },
            onDismiss = { editing = null },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.types_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                types.forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        ColorSwatch(typeColor(type))
                        Text(type.name, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        TextButton(onClick = { inUse = false; editing = type.id }) { Text(stringResource(R.string.types_edit)) }
                        TextButton(onClick = { onDelete(type.id) { deleted -> inUse = !deleted } }) {
                            Text(stringResource(R.string.event_delete))
                        }
                    }
                }
                if (inUse) {
                    Text(stringResource(R.string.types_in_use), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { inUse = false; editing = NEW_TYPE }) { Text(stringResource(R.string.types_new)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.types_close)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeFormDialog(
    type: EventType?,
    onSave: (name: String, color: String, onResult: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(type?.name.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(type?.color ?: typePalette.first().first) }
    var failed by rememberSaveable { mutableStateOf(false) }
    // A colour outside the palette (set elsewhere) stays selectable, so renaming never changes it.
    val options = if (typePalette.any { it.first.equals(color, ignoreCase = true) } || type == null) {
        typePalette
    } else {
        listOf(type.color to R.string.color_current) + typePalette
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (type == null) R.string.types_new else R.string.types_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(CalendarLimits.TYPE_NAME_MAX); failed = false },
                    label = { Text(stringResource(R.string.types_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.types_color_label), modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (hex, label) ->
                        ColorOption(hex, stringResource(label), selected = hex.equals(color, ignoreCase = true)) { color = hex }
                    }
                }
                if (failed) {
                    Text(stringResource(R.string.types_save_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(name, color) { saved -> if (saved) onDismiss() else failed = true }
            }) { Text(stringResource(R.string.event_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.event_cancel)) } },
    )
}

/** A selectable colour; the selection shows as a border and the colour name is read by TalkBack. */
@Composable
private fun ColorOption(hex: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = adaptToTheme(parseTypeColor(hex) ?: MaterialTheme.colorScheme.outline, isSystemInDarkTheme())
    val border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .border(border, RoundedCornerShape(8.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    )
}
