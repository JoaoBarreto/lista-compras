package com.example.listacompras.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.listacompras.R
import com.example.listacompras.domain.model.ShoppingItem
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.model.SyncStatus
import com.example.listacompras.ui.theme.ListaComprasTheme
import java.time.Instant

@Composable
fun ShoppingRoute(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShoppingScreen(
        state = state,
        onAdd = viewModel::onAdd,
        onCheckedChange = viewModel::onCheckedChange,
        onDelete = viewModel::onDelete,
        onClearChecked = viewModel::onClearChecked,
        onErrorAcknowledged = viewModel::onErrorAcknowledged,
        onLogout = onLogout,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    state: ShoppingUiState,
    onAdd: (String) -> Unit,
    onCheckedChange: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onClearChecked: () -> Unit,
    onErrorAcknowledged: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Only items added from this screen scroll into view; ones arriving from the other phone must not.
    var scrollToNewItem by remember { mutableStateOf(false) }
    LaunchedEffect(state.toBuy.size) {
        if (scrollToNewItem && state.toBuy.isNotEmpty()) {
            scrollToNewItem = false
            listState.revealItem(index = state.toBuy.lastIndex + 1) // +1: the section header
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // The banner lives in the top bar so the list is laid out below it, never under it.
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.shopping_title))
                            SyncStatusLabel(state.syncStatus)
                        }
                    },
                    actions = {
                        TextButton(onClick = onLogout) {
                            Text(stringResource(R.string.shopping_button_logout))
                        }
                    },
                )
                SyncBanner(state.syncStatus, onErrorClick = onErrorAcknowledged)
            }
        },
        bottomBar = { AddItemBar(onAdd = { scrollToNewItem = true; onAdd(it) }) },
    ) { innerPadding ->
        // Nothing until the local database has answered, so no empty list flashes (empty states: 5.4).
        if (state.isLoading) return@Scaffold
        LazyColumn(
            state = listState,
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.toBuy.isNotEmpty()) {
                item(key = "header-to-buy") { SectionHeader(stringResource(R.string.shopping_section_to_buy)) }
                items(state.toBuy, key = { it.id }) { item ->
                    SwipeableRow(item, onCheckedChange, onDelete)
                }
            }
            if (state.bought.isNotEmpty()) {
                item(key = "header-bought") {
                    SectionHeader(
                        title = stringResource(R.string.shopping_section_bought),
                        action = stringResource(R.string.shopping_clear_bought),
                        onAction = onClearChecked,
                    )
                }
                items(state.bought, key = { it.id }) { item ->
                    SwipeableRow(item, onCheckedChange, onDelete)
                }
            }
        }
    }
}

/** Brings the item at [index] into view, as close to the bottom edge as possible; no-op if visible. */
private suspend fun LazyListState.revealItem(index: Int) {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.any { it.index == index && it.offset + it.size <= info.viewportEndOffset - info.afterContentPadding }) return
    val itemSize = visible.lastOrNull()?.size ?: 0
    val viewport = info.viewportEndOffset - info.viewportStartOffset - info.beforeContentPadding - info.afterContentPadding
    animateScrollToItem(index, scrollOffset = -(viewport - itemSize).coerceAtLeast(0))
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(
    item: ShoppingItem,
    onCheckedChange: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete(item.id)
            // The row leaves the list through the database; never keep it in a dismissed state.
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.shopping_delete),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        ShoppingItemRow(item = item, onCheckedChange = { onCheckedChange(item.id, it) })
    }
}

private fun previewItem(id: String, name: String, checked: Boolean, quantity: String? = null) = ShoppingItem(
    id = id, listId = "l", name = name, quantity = quantity, checked = checked, category = null,
    addedBy = "u", checkedBy = null, updatedAt = Instant.EPOCH, syncState = SyncState.SYNCED,
)

private fun previewState(status: SyncStatus) = ShoppingUiState(
    toBuy = listOf(previewItem("a", "Leite", false, "2 L"), previewItem("b", "Pão", false)),
    bought = listOf(previewItem("c", "Ovos", true)),
    syncStatus = status,
    isLoading = false,
)

@Composable
private fun PreviewScreen(status: SyncStatus) {
    ListaComprasTheme {
        ShoppingScreen(
            state = previewState(status),
            onAdd = {}, onCheckedChange = { _, _ -> }, onDelete = {}, onClearChecked = {},
            onErrorAcknowledged = {}, onLogout = {},
        )
    }
}

@Preview(showBackground = true, name = "Synced")
@Composable
private fun SyncedPreview() = PreviewScreen(SyncStatus.Synced)

@Preview(showBackground = true, name = "Syncing")
@Composable
private fun SyncingPreview() = PreviewScreen(SyncStatus.Syncing)

@Preview(showBackground = true, name = "Offline")
@Composable
private fun OfflinePreview() = PreviewScreen(SyncStatus.Offline)

@Preview(showBackground = true, name = "Error")
@Composable
private fun ErrorPreview() = PreviewScreen(SyncStatus.Error)
