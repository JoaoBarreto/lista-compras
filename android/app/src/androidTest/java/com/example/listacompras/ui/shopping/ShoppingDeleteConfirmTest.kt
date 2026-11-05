package com.example.listacompras.ui.shopping

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.listacompras.R
import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.domain.model.ShoppingItem
import com.example.listacompras.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Task 9.13: swiping an item asks for confirmation; only "Apagar" in the dialog deletes it. */
class ShoppingDeleteConfirmTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deleted = mutableListOf<String>()

    private val milk = ShoppingItem(
        id = "a", listId = "l", name = "Leite", quantity = null, checked = false, category = null,
        addedBy = "u", checkedBy = null, updatedAt = Instant.EPOCH, syncState = SyncState.SYNCED,
    )

    private var leftBefore = 0.dp

    private fun showAndSwipe() {
        rule.setContent {
            ShoppingScreen(
                state = ShoppingUiState(toBuy = listOf(milk), canAdd = true, isLoading = false),
                texts = shoppingTexts(ListKind.SHOPPING),
                tabs = {},
                onAdd = { _, _ -> },
                onCheckedChange = { _, _ -> },
                onDelete = { deleted += it },
                onEdit = { _, _, _ -> },
                onClearChecked = {},
                onErrorAcknowledged = {},
                onOpenSettings = {},
            )
        }
        leftBefore = rule.onNodeWithText("Leite").getUnclippedBoundsInRoot().left
        rule.onNodeWithText("Leite").performTouchInput { swipeLeft() }
        rule.waitForIdle()
    }

    @Test
    fun swipeOnlyAsksAndCancelKeepsTheItem() {
        showAndSwipe()
        rule.onNodeWithText(context.getString(R.string.shopping_delete_title)).assertExists()
        assertEquals(emptyList<String>(), deleted)
        rule.onNodeWithText(context.getString(R.string.shopping_edit_cancel)).performClick()
        rule.waitForIdle()
        // The row must snap back: its left edge is where it was before the swipe.
        assertEquals(leftBefore, rule.onNodeWithText("Leite").getUnclippedBoundsInRoot().left)

        rule.onNodeWithText("Leite").assertExists()
        rule.onNodeWithText(context.getString(R.string.shopping_delete_title)).assertDoesNotExist()
        assertEquals(emptyList<String>(), deleted)
    }

    @Test
    fun confirmingDeletesOnce() {
        showAndSwipe()
        rule.onNode(hasText(context.getString(R.string.shopping_delete)) and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("a"), deleted)
    }
}
