package com.example.listacompras.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
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
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shopping_title)) },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text(stringResource(R.string.shopping_button_logout))
                    }
                },
            )
        },
        bottomBar = { AddItemBar(onAdd = onAdd) },
    ) { innerPadding ->
        // Nothing until the local database has answered, so no empty list flashes (empty states: 5.4).
        if (state.isLoading) return@Scaffold
        LazyColumn(
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

@Preview(showBackground = true)
@Composable
private fun ShoppingScreenPreview() {
    ListaComprasTheme {
        ShoppingScreen(
            state = ShoppingUiState(
                toBuy = listOf(previewItem("a", "Leite", false, "2 L"), previewItem("b", "Pão", false)),
                bought = listOf(previewItem("c", "Ovos", true)),
                isLoading = false,
            ),
            onAdd = {}, onCheckedChange = { _, _ -> }, onDelete = {}, onClearChecked = {}, onLogout = {},
        )
    }
}
