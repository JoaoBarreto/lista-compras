package com.example.listacompras.ui.shopping

import app.cash.turbine.test
import com.example.listacompras.domain.model.Category
import com.example.listacompras.domain.model.ListKind
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
        val observed = mutableListOf<ListKind>()
        override fun observeItems(kind: ListKind): Flow<List<ShoppingItem>> {
            observed += kind
            return items
        }
        override suspend fun add(kind: ListKind, name: String, quantity: String?, category: Category?): Boolean {
            calls += "add:$kind:$name" + (quantity?.let { ":$it" } ?: "")
            return true
        }
        override suspend fun update(id: String, name: String, quantity: String?) {
            calls += "update:$id:$name:${quantity ?: "-"}"
        }
        override suspend fun setChecked(id: String, checked: Boolean) { calls += "check:$id:$checked" }
        override suspend fun delete(id: String) { calls += "delete:$id" }
        override suspend fun clearChecked(kind: ListKind) { calls += "clear:$kind" }
    }

    private class FakeSyncStatusProvider : SyncStatusProvider {
        override val status = MutableStateFlow(SyncStatus.Synced)
        var acknowledged = 0
        override suspend fun acknowledgeErrors() { acknowledged++ }
    }

    private class FakeListRepository : ListRepository {
        val has = MutableStateFlow(true)
        val asked = mutableListOf<ListKind>()
        override fun hasList(kind: ListKind): Flow<Boolean> {
            asked += kind
            return has
        }
        override suspend fun ensureLists() = has.value
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
        val vm = ShoppingViewModel(ListKind.SHOPPING, repo, lists, sync)

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
            ListKind.SHOPPING,
            object : ItemRepository by repo {
                override fun observeItems(kind: ListKind): Flow<List<ShoppingItem>> = source
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
        val vm = ShoppingViewModel(ListKind.SHOPPING, repo, lists, sync)

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
        val vm = ShoppingViewModel(ListKind.SHOPPING, repo, lists, sync)

        vm.onAdd("   ")
        vm.onAdd("  leite ")

        assertEquals(listOf("add:SHOPPING:leite"), repo.calls)
    }

    @Test
    fun `edit trims, ignores blank names, caps lengths and keeps tasks without quantity`() = runTest {
        val shopping = ShoppingViewModel(ListKind.SHOPPING, repo, lists, sync)
        shopping.onEdit("a", "  leite ", " 2 L ")
        shopping.onEdit("b", "ovos", "   ")
        shopping.onEdit("c", "   ", "3")
        shopping.onEdit("d", "n".repeat(120), "x".repeat(40))
        ShoppingViewModel(ListKind.TASKS, repo, lists, sync).onEdit("e", "Limpar", "3")

        assertEquals(
            listOf(
                "update:a:leite:2 L", "update:b:ovos:", "update:d:" + "n".repeat(100) + ":" + "x".repeat(30),
                "update:e:Limpar:-",
            ),
            repo.calls,
        )
    }

    @Test
    fun `quantity is optional, trimmed and capped on shopping, and never stored on tasks`() = runTest {
        val shopping = ShoppingViewModel(ListKind.SHOPPING, repo, lists, sync)
        shopping.onAdd("leite")
        shopping.onAdd("ovos", "")
        shopping.onAdd("pão", "   ")
        shopping.onAdd("arroz", " 2 kg ")
        shopping.onAdd("água", "x".repeat(40))
        ShoppingViewModel(ListKind.TASKS, repo, lists, sync).onAdd("Limpar", "3")

        assertEquals(
            listOf(
                "add:SHOPPING:leite", "add:SHOPPING:ovos", "add:SHOPPING:pão", "add:SHOPPING:arroz:2 kg",
                "add:SHOPPING:água:" + "x".repeat(30), "add:TASKS:Limpar",
            ),
            repo.calls,
        )
    }

    @Test
    fun `events call the repository`() = runTest {
        val vm = ShoppingViewModel(ListKind.SHOPPING, repo, lists, sync)

        vm.onCheckedChange("a", true)
        vm.onDelete("b")
        vm.onClearChecked()

        assertEquals(listOf("check:a:true", "delete:b", "clear:SHOPPING"), repo.calls)
    }

    @Test
    fun `acknowledging the error is forwarded to the status provider`() = runTest {
        val vm = ShoppingViewModel(ListKind.SHOPPING, repo, lists, sync)

        vm.onErrorAcknowledged()

        assertEquals(1, sync.acknowledged)
    }

    @Test
    fun `canAdd follows the active list and adding is ignored without one`() = runTest {
        lists.has.value = false
        val vm = ShoppingViewModel(ListKind.SHOPPING, repo, lists, sync)

        vm.uiState.test {
            assertEquals(false, expectMostRecentItem().canAdd)

            vm.onAdd("leite")
            assertEquals(emptyList<String>(), repo.calls)

            lists.has.value = true
            assertEquals(true, awaitItem().canAdd)

            vm.onAdd("leite")
            assertEquals(listOf("add:SHOPPING:leite"), repo.calls)
        }
    }

    @Test
    fun `a tasks view model reads and writes the tasks list`() = runTest {
        val vm = ShoppingViewModel(ListKind.TASKS, repo, lists, sync)

        vm.uiState.test {
            expectMostRecentItem()
            vm.onAdd("Limpar grelhador")
            vm.onClearChecked()
        }

        assertEquals(listOf(ListKind.TASKS), repo.observed)
        assertEquals(setOf(ListKind.TASKS), lists.asked.toSet())
        assertEquals(listOf("add:TASKS:Limpar grelhador", "clear:TASKS"), repo.calls)
    }
}
