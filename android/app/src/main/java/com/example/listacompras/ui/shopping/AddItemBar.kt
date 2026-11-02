package com.example.listacompras.ui.shopping

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

/** Longest quantity the server accepts (`items.quantity`, see DATA_MODEL). */
const val MAX_QUANTITY_LENGTH = 30

/**
 * Fixed quick-add field. Keeps focus after adding so several items can be typed in a row.
 * With a non-null [quantityLabel] a second, optional field is shown; blank means no quantity.
 */
@Composable
fun AddItemBar(
    onAdd: (name: String, quantity: String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    quantityLabel: String? = null,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    val submit = {
        if (enabled && text.isNotBlank()) {
            onAdd(text, if (quantityLabel == null) "" else quantity)
            text = ""
            quantity = ""
            // Back to the name, so several items can be typed in a row even after submitting from the quantity.
            nameFocus.requestFocus()
        }
    }

    Surface(tonalElevation = 3.dp, modifier = modifier) {
        // A single inset modifier: the IME inset already covers the navigation bar area.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    modifier = Modifier.weight(1f).focusRequester(nameFocus),
                )
                if (quantityLabel != null) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.take(MAX_QUANTITY_LENGTH) },
                        label = { Text(quantityLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.padding(start = 8.dp).width(104.dp),
                    )
                } else {
                    Button(onClick = { submit() }, enabled = enabled, modifier = Modifier.padding(start = 8.dp)) {
                        Text(stringResource(R.string.shopping_add_button))
                    }
                }
            }
            if (quantityLabel != null) {
                Button(
                    onClick = { submit() },
                    enabled = enabled,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.shopping_add_button))
                }
            }
        }
    }
}
