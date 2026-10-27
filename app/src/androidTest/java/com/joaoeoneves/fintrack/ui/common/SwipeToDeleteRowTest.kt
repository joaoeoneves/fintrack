package com.joaoeoneves.fintrack.ui.common

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [SwipeToDeleteRow] has no on-row delete button: deletion is an end-to-start swipe, and
 * assistive technologies get a custom accessibility action labelled with `deleteLabel`. Verifies
 * invoking that action calls `onDelete` and that no node carries a "Delete" content description.
 */
@RunWith(AndroidJUnit4::class)
class SwipeToDeleteRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var deleted = 0

    private fun setRowContent() {
        composeTestRule.setContent {
            FinTrackTheme {
                SwipeToDeleteRow(onDelete = { deleted++ }, deleteLabel = "Delete") {
                    Text("Groceries")
                }
            }
        }
    }

    private val hasDeleteCustomAction =
        SemanticsMatcher("has custom action \"Delete\"") { node ->
            SemanticsActions.CustomActions in node.config &&
                node.config[SemanticsActions.CustomActions].any { it.label == "Delete" }
        }

    @Test
    fun customDeleteAction_invokesOnDelete() {
        setRowContent()
        assertEquals(0, deleted)

        val node = composeTestRule.onNode(hasDeleteCustomAction)
        node.assertExists()
        val action =
            node
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .first { it.label == "Delete" }
        composeTestRule.runOnUiThread { action.action() }
        composeTestRule.waitForIdle()

        assertEquals(1, deleted)
    }

    @Test
    fun row_hasNoDeleteContentDescriptionNode() {
        setRowContent()

        composeTestRule.onNodeWithText("Groceries").assertExists()
        composeTestRule.onNodeWithContentDescription("Delete").assertDoesNotExist()
    }
}
