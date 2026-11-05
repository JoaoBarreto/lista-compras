package com.example.listacompras.ui.shopping

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.listacompras.R

/** Asks for confirmation before deleting the item called [itemName]. */
@Composable
fun ItemDeleteDialog(
    itemName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_delete_title)) },
        text = { Text(stringResource(R.string.shopping_delete_message, itemName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.shopping_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.shopping_edit_cancel)) }
        },
    )
}
