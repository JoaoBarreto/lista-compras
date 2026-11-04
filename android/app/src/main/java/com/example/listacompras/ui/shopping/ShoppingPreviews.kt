package com.example.listacompras.ui.shopping

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.domain.model.ShoppingItem
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.model.SyncStatus
import com.example.listacompras.ui.theme.ListaComprasTheme
import java.time.Instant

private fun item(id: String, name: String, checked: Boolean, quantity: String? = null) = ShoppingItem(
    id = id, listId = "l", name = name, quantity = quantity, checked = checked, category = null,
    addedBy = "u", checkedBy = null, updatedAt = Instant.EPOCH, syncState = SyncState.SYNCED,
)

private val toBuy = listOf(item("a", "Leite", false, "2 L"), item("b", "Pão", false))
private val bought = listOf(item("c", "Ovos", true))

@Composable
private fun PreviewScreen(state: ShoppingUiState) {
    ListaComprasTheme {
        ShoppingScreen(
            state = state,
            texts = shoppingTexts(ListKind.SHOPPING),
            tabs = {},
            onAdd = { _, _ -> }, onCheckedChange = { _, _ -> }, onDelete = {}, onEdit = { _, _, _ -> }, onClearChecked = {},
            onErrorAcknowledged = {}, onOpenSettings = {},
        )
    }
}

private fun state(
    status: SyncStatus = SyncStatus.Synced,
    toBuy: List<ShoppingItem> = com.example.listacompras.ui.shopping.toBuy,
    bought: List<ShoppingItem> = com.example.listacompras.ui.shopping.bought,
) = ShoppingUiState(toBuy = toBuy, bought = bought, syncStatus = status, canAdd = true, isLoading = false)

@Preview(showBackground = true, name = "Synced")
@Composable
private fun SyncedPreview() = PreviewScreen(state())

@Preview(showBackground = true, name = "Syncing")
@Composable
private fun SyncingPreview() = PreviewScreen(state(SyncStatus.Syncing))

@Preview(showBackground = true, name = "Offline")
@Composable
private fun OfflinePreview() = PreviewScreen(state(SyncStatus.Offline))

@Preview(showBackground = true, name = "Error")
@Composable
private fun ErrorPreview() = PreviewScreen(state(SyncStatus.Error))

@Preview(showBackground = true, name = "Empty")
@Composable
private fun EmptyPreview() = PreviewScreen(state(toBuy = emptyList(), bought = emptyList()))

@Preview(showBackground = true, name = "All bought")
@Composable
private fun AllBoughtPreview() = PreviewScreen(state(toBuy = emptyList()))

@Preview(showBackground = true, name = "Dark, offline", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DarkPreview() = PreviewScreen(state(SyncStatus.Offline))

@Preview(showBackground = true, name = "Font x2, error", fontScale = 2f)
@Composable
private fun LargeFontPreview() = PreviewScreen(state(SyncStatus.Error))

@Preview(showBackground = true, name = "No list yet")
@Composable
private fun NoListPreview() = PreviewScreen(
    ShoppingUiState(syncStatus = SyncStatus.Offline, canAdd = false, isLoading = false),
)
