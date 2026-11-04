package com.example.listacompras.ui.shopping

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.listacompras.R
import com.example.listacompras.domain.model.ShoppingItem

/** Edits the name and, when [showQuantity] is set, the quantity of [item]. Save needs a non-blank name. */
@Composable
fun ItemEditDialog(
    item: ShoppingItem,
    showQuantity: Boolean,
    nameLabel: String,
    quantityLabel: String?,
    onSave: (name: String, quantity: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    var quantity by rememberSaveable(item.id) { mutableStateOf(item.quantity.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                    label = { Text(nameLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showQuantity) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.take(MAX_QUANTITY_LENGTH) },
                        label = { Text(quantityLabel ?: stringResource(R.string.shopping_quantity_description)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, quantity) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.shopping_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.shopping_edit_cancel)) }
        },
    )
}
