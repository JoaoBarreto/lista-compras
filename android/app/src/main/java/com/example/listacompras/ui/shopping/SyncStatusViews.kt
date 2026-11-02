package com.example.listacompras.ui.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.listacompras.R
import com.example.listacompras.domain.model.SyncStatus

/** One-line status under the title. Always the same height, so it never moves the list. */
@Composable
fun SyncStatusLabel(status: SyncStatus, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        SyncStatus.Synced -> R.string.sync_status_synced to MaterialTheme.colorScheme.onSurfaceVariant
        SyncStatus.Syncing -> R.string.sync_status_syncing to MaterialTheme.colorScheme.onSurfaceVariant
        SyncStatus.Offline -> R.string.sync_status_offline to MaterialTheme.colorScheme.error
        SyncStatus.Error -> R.string.sync_status_error to MaterialTheme.colorScheme.error
    }
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
    )
}

/** Banner for the states that last: offline and rejected operations. Nothing for the others. */
@Composable
fun SyncBanner(status: SyncStatus, onErrorClick: () -> Unit, modifier: Modifier = Modifier) {
    when (status) {
        SyncStatus.Offline -> Banner(
            text = stringResource(R.string.sync_banner_offline),
            modifier = modifier,
        )
        SyncStatus.Error -> Banner(
            text = stringResource(R.string.sync_banner_error),
            modifier = modifier.clickable(role = Role.Button, onClick = onErrorClick),
        )
        SyncStatus.Synced, SyncStatus.Syncing -> Unit
    }
}

@Composable
private fun Banner(text: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
