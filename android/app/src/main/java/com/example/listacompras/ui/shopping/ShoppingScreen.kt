package com.example.listacompras.ui.shopping

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.listacompras.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    state: ShoppingUiState,
    texts: ShoppingTexts,
    tabs: @Composable () -> Unit,
    onAdd: (String, String) -> Unit,
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
                            Text(stringResource(R.string.app_name))
                            SyncStatusLabel(state.syncStatus)
                        }
                    },
                    actions = {
                        TextButton(onClick = onLogout) {
                            Text(stringResource(R.string.shopping_button_logout))
                        }
                    },
                )
                tabs()
                SyncBanner(state.syncStatus, onErrorClick = onErrorAcknowledged)
            }
        },
        bottomBar = {
            AddItemBar(
                onAdd = { name, quantity -> scrollToNewItem = true; onAdd(name, quantity) },
                label = texts.addLabel,
                enabled = state.canAdd,
                quantityLabel = texts.quantityLabel,
            )
        },
    ) { innerPadding ->
        // Nothing until the local database has answered, so the empty message does not flash.
        if (state.isLoading) return@Scaffold
        LazyColumn(
            state = listState,
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.toBuy.isEmpty() && state.bought.isEmpty()) {
                item(key = "empty") { EmptyMessage(
                        if (state.canAdd) texts.empty else texts.waitingList,
                        Modifier.fillParentMaxSize(),
                    ) }
            }
            if (state.toBuy.isEmpty() && state.bought.isNotEmpty()) {
                item(key = "all-bought") { EmptyMessage(texts.allDone) }
            }
            if (state.toBuy.isNotEmpty()) {
                item(key = "header-to-buy") { SectionHeader(texts.sectionOpen) }
                items(state.toBuy, key = { it.id }) { item ->
                    SwipeableRow(item, onCheckedChange, onDelete)
                }
            }
            if (state.bought.isNotEmpty()) {
                item(key = "header-bought") {
                    SectionHeader(
                        title = texts.sectionDone,
                        action = texts.clearDone,
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
