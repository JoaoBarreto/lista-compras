package com.example.listacompras.ui.shopping

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.listacompras.R
import com.example.listacompras.domain.model.ListKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Task 6.4: before the active list is known (first run, server not reached yet) adding is blocked,
 * what was typed is kept and the reason is shown; once the list exists everything works as usual.
 */
class ShoppingAddWithoutListTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val added = mutableListOf<Pair<String, String>>()

    private fun show(canAdd: Boolean) = rule.setContent {
        ShoppingScreen(
            state = ShoppingUiState(canAdd = canAdd, isLoading = false),
            texts = shoppingTexts(ListKind.SHOPPING),
            tabs = {},
            onAdd = { name, quantity -> added += name to quantity },
            onCheckedChange = { _, _ -> },
            onDelete = {},
            onClearChecked = {},
            onErrorAcknowledged = {},
            onOpenSettings = {},
        )
    }

    @Test
    fun withoutAnActiveListTheButtonIsDisabledTheTypedTextStaysAndTheReasonIsShown() {
        show(canAdd = false)

        rule.onAllNodesWithSetText()[0].performTextInput("Leite")

        rule.onNodeWithText(context.getString(R.string.shopping_add_button)).assertIsNotEnabled()
        rule.onNodeWithText("Leite").assertExists()
        rule.onNodeWithText(context.getString(R.string.shopping_add_waiting_list)).assertExists()
        assertEquals(emptyList<Pair<String, String>>(), added)
    }

    @Test
    fun withAnActiveListAddingCallsOnAdd() {
        show(canAdd = true)

        rule.onAllNodesWithSetText()[0].performTextInput("Leite")
        rule.onNodeWithText(context.getString(R.string.shopping_add_button)).assertIsEnabled().performClick()

        assertEquals(listOf("Leite" to ""), added)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithSetText() = onAllNodes(hasSetTextAction())
}
