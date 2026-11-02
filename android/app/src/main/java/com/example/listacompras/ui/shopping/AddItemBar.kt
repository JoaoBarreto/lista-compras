package com.example.listacompras.ui.shopping

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.listacompras.R

/** Fixed quick-add field. Keeps focus after adding so several items can be typed in a row. */
@Composable
fun AddItemBar(
    onAdd: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (enabled && text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }

    Surface(tonalElevation = 3.dp, modifier = modifier) {
        // A single inset modifier: the IME inset already covers the navigation bar area.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                supportingText = if (enabled) null else {
                    { Text(stringResource(R.string.shopping_add_waiting_list)) }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { submit() }, enabled = enabled, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.shopping_add_button))
            }
        }
    }
}
