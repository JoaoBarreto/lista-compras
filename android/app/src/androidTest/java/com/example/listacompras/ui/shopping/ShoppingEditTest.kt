package com.example.listacompras.ui.shopping

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.listacompras.R
import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.domain.model.ShoppingItem
import com.example.listacompras.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Task 9.11: a long press opens the edit dialog; a short tap still only toggles the item. */
class ShoppingEditTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val edits = mutableListOf<List<String>>()
    private val checks = mutableListOf<Pair<String, Boolean>>()

    private val milk = ShoppingItem(
        id = "a", listId = "l", name = "Leite", quantity = "2 L", checked = false, category = null,
        addedBy = "u", checkedBy = null, updatedAt = Instant.EPOCH, syncState = SyncState.SYNCED,
    )

    private fun show(kind: ListKind) = rule.setContent {
        ShoppingScreen(
            state = ShoppingUiState(toBuy = listOf(milk), canAdd = true, isLoading = false),
            texts = shoppingTexts(kind),
            tabs = {},
            onAdd = { _, _ -> },
            onCheckedChange = { id, c -> checks += id to c },
            onDelete = {},
            onEdit = { id, name, qty -> edits += listOf(id, name, qty) },
            onClearChecked = {},
            onErrorAcknowledged = {},
            onOpenSettings = {},
        )
    }

    private fun longPressMilk() =
        rule.onNodeWithText("Leite").performTouchInput { longClick() }

    private fun dialogFields() = rule.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))

    private fun dialogField(i: Int) = dialogFields()[i]

    private fun save() = rule.onNodeWithText(context.getString(R.string.shopping_edit_save)).performClick()

    @Test
    fun longPressEditsNameAndQuantityPrefilled() {
        show(ListKind.SHOPPING)
        longPressMilk()

        rule.onNodeWithText(context.getString(R.string.shopping_edit_title)).assertExists()
        dialogField(1).assertTextContains("2 L")
        dialogField(0).performTextClearance()
        dialogField(0).performTextInput("Leite magro")
        dialogField(1).performTextClearance()
        dialogField(1).performTextInput("6")
        save()

        assertEquals(listOf(listOf("a", "Leite magro", "6")), edits)
        assertEquals(emptyList<Pair<String, Boolean>>(), checks)
    }

    @Test
    fun clearingTheQuantitySendsBlank() {
        show(ListKind.SHOPPING)
        longPressMilk()
        dialogField(1).performTextClearance()
        save()

        assertEquals(listOf(listOf("a", "Leite", "")), edits)
    }

    @Test
    fun saveIsDisabledWithABlankNameAndCancelChangesNothing() {
        show(ListKind.SHOPPING)
        longPressMilk()
        dialogField(0).performTextClearance()
        rule.onNodeWithText(context.getString(R.string.shopping_edit_save)).assertIsNotEnabled()
        rule.onNodeWithText(context.getString(R.string.shopping_edit_cancel)).performClick()

        assertEquals(emptyList<List<String>>(), edits)
        rule.onNodeWithText(context.getString(R.string.shopping_edit_title)).assertDoesNotExist()
    }

    @Test
    fun aShortTapStillTogglesAndOpensNoDialog() {
        show(ListKind.SHOPPING)
        rule.onNodeWithText("Leite").performClick()

        assertEquals(listOf("a" to true), checks)
        rule.onAllNodesWithText(context.getString(R.string.shopping_edit_title)).assertCountEquals0()
    }

    @Test
    fun tasksHaveNoQuantityField() {
        show(ListKind.TASKS)
        longPressMilk()

                assertEquals(1, dialogFields().fetchSemanticsNodes().size)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals0() =
        assertEquals(0, fetchSemanticsNodes().size)
}
