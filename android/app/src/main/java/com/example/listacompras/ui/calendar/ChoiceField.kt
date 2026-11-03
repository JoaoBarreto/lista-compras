package com.example.listacompras.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** A button showing [label] that opens a menu of [options]; each option is its text and what to do on pick. */
@Composable
fun ChoiceField(label: String, options: List<Pair<String, () -> Unit>>, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, onPick) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onPick() })
            }
        }
    }
}
