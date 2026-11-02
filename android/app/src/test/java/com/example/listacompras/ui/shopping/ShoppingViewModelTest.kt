package com.example.listacompras.ui.shopping

import app.cash.turbine.test
import com.example.listacompras.domain.model.Category
import com.example.listacompras.domain.model.ShoppingItem
import com.example.listacompras.domain.model.SyncState
import com.example.listacompras.domain.model.SyncStatus
import com.example.listacompras.domain.repository.ItemRepository
import com.example.listacompras.domain.repository.ListRepository
import com.example.listacompras.domain.repository.SyncStatusProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {

    private class FakeItemRepository : ItemRepository {
        val items = MutableStateFlow<List<ShoppingItem>>(emptyList())
        val calls = mutableListOf<String>()
        override fun observeItems(): Flow<List<ShoppingItem>> = items
        override suspend fun add(name: String, quantity: String?, category: Category?): Boolean {
            calls += "add:$name"
            return true
        }
        override suspend fun setChecked(id: String, checked: Boolean) { calls += "check:$id:$checked" }
        override suspend fun delete(id: String) { calls += "delete:$id" }
        override suspend fun clearChecked() { calls += "clear" }
    }

    private class FakeSyncStatusProvider : SyncStatusProvider {
        override val status = MutableStateFlow(SyncStatus.Synced)
        var acknowledged = 0
        override suspend fun acknowledgeErrors() { acknowledged++ }
    }

    private class FakeListRepository : ListRepository {
        val has = MutableStateFlow(true)
        override val hasActiveList: Flow<Boolean> = has
        override suspend fun ensureActiveList() = has.value
    }

    private val repo = FakeItemRepository()
    private val lists = FakeListRepository()
    private val sync = FakeSyncStatusProvider()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun item(id: String, checked: Boolean = false) = ShoppingItem(
        id = id, listId = "l1", name = id, quantity = null, checked = checked, category = null,
        addedBy = "u1", checkedBy = null, updatedAt = Instant.EPOCH, syncState = SyncState.SYNCED,
    )

    @Test
    fun `starts loading then splits items keeping repository order`() = runTest {
        repo.items.value = listOf(item("a"), item("b", checked = true), item("c"), item("d", checked = true))
        val vm = ShoppingViewModel(repo, lists, sync)

        vm.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(false, state.isLoading)
            assertEquals(listOf("a", "c"), state.toBuy.map { it.id })
            assertEquals(listOf("b", "d"), state.bought.map { it.id })
        }
    }

    @Test
    fun `is loading until the repository emits`() = runTest {
        val source = MutableSharedFlow<List<ShoppingItem>>()
        val vm = ShoppingViewModel(
            object : ItemRepository by repo {
                override fun observeItems(): Flow<List<ShoppingItem>> = source
            },
            lists,
            sync,
        )

        vm.uiState.test {
            assertEquals(true, awaitItem().isLoading)

            source.emit(listOf(item("a"), item("b", checked = true)))
            val loaded = awaitItem()
            assertEquals(false, loaded.isLoading)
            assertEquals(listOf("a"), loaded.toBuy.map { it.id })
            assertEquals(listOf("b"), loaded.bought.map { it.id })
        }
    }

    @Test
    fun `reacts to item and sync status changes`() = runTest {
        val vm = ShoppingViewModel(repo, lists, sync)

        vm.uiState.test {
            assertEquals(emptyList<ShoppingItem>(), expectMostRecentItem().toBuy)

            repo.items.value = listOf(item("a"))
            assertEquals(listOf("a"), awaitItem().toBuy.map { it.id })

            repo.items.value = listOf(item("a", checked = true))
            val moved = awaitItem()
            assertEquals(emptyList<ShoppingItem>(), moved.toBuy)
            assertEquals(listOf("a"), moved.bought.map { it.id })

            sync.status.value = SyncStatus.Offline
            assertEquals(SyncStatus.Offline, awaitItem().syncStatus)
        }
    }

    @Test
    fun `blank names are ignored and others are trimmed`() = runTest {
        val vm = ShoppingViewModel(repo, lists, sync)

        vm.onAdd("   ")
        vm.onAdd("  leite ")

        assertEquals(listOf("add:leite"), repo.calls)
    }

    @Test
    fun `events call the repository`() = runTest {
        val vm = ShoppingViewModel(repo, lists, sync)

        vm.onCheckedChange("a", true)
        vm.onDelete("b")
        vm.onClearChecked()

        assertEquals(listOf("check:a:true", "delete:b", "clear"), repo.calls)
    }

    @Test
    fun `acknowledging the error is forwarded to the status provider`() = runTest {
        val vm = ShoppingViewModel(repo, lists, sync)

        vm.onErrorAcknowledged()

        assertEquals(1, sync.acknowledged)
    }

    @Test
    fun `canAdd follows the active list and adding is ignored without one`() = runTest {
        lists.has.value = false
        val vm = ShoppingViewModel(repo, lists, sync)

        vm.uiState.test {
            assertEquals(false, expectMostRecentItem().canAdd)

            vm.onAdd("leite")
            assertEquals(emptyList<String>(), repo.calls)

            lists.has.value = true
            assertEquals(true, awaitItem().canAdd)

            vm.onAdd("leite")
            assertEquals(listOf("add:leite"), repo.calls)
        }
    }
}
