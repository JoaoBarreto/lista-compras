package com.example.listacompras.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import com.example.listacompras.R

/** The single entry point for settings; logout stays reachable from here. */
@Composable
fun SettingsDialog(
    shoppingNotifications: Boolean,
    onShoppingNotificationsChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = shoppingNotifications,
                        role = Role.Switch,
                        onValueChange = onShoppingNotificationsChange,
                    ),
            ) {
                Text(stringResource(R.string.settings_notify_shopping), modifier = Modifier.weight(1f))
                Switch(checked = shoppingNotifications, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) } },
        dismissButton = { TextButton(onClick = onLogout) { Text(stringResource(R.string.shopping_button_logout)) } },
    )
}
