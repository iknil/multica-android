package ai.multica.android

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Device regression for Activity recreation triggered by fold/unfold window changes. */
class FoldableStateRegressionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun resizeRecreationRetainsInputNavigationScrollAndTouchBounds() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Sign out")).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasTestTag("login_primary_action")).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodes(hasText("Sign out")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodes(hasText("Sign out"))[0].performClick()
        }
        compose.onNodeWithTag("login_primary_action")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("One-time code")).fetchSemanticsNodes().isNotEmpty()
        }
        assertPrimaryActionFullyVisible()
        compose.onNodeWithText("One-time code").performTextInput("123456")

        // Two recreations represent narrow -> wide -> narrow while the IME/input is active.
        repeat(2) {
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("One-time code").assertTextContains("123456")
            assertPrimaryActionFullyVisible()
        }
        compose.onNodeWithTag("login_primary_action").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Android delivery")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Android delivery").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Verify Android session recovery")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Verify Android session recovery").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("created this task")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("issue_detail_list").performScrollToIndex(9)

        // Second recreation represents wide -> narrow and must retain detail plus its list position.
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("updated priority to high").assertIsDisplayed()

        compose.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithText("3 tasks").assertIsDisplayed()
        compose.onNodeWithText("Sign out").performClick()
    }

    private fun assertPrimaryActionFullyVisible() {
        compose.waitForIdle()
        val action = compose.onNodeWithTag("login_primary_action")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode()
            .boundsInRoot
        val viewport = compose.onNodeWithTag("login_viewport").fetchSemanticsNode().boundsInRoot
        val minTouchHeightPx = 48f * compose.activity.resources.displayMetrics.density

        assertTrue("clickable action is clipped above the viewport: $action vs $viewport", action.top >= viewport.top)
        assertTrue("clickable action is clipped below the viewport: $action vs $viewport", action.bottom <= viewport.bottom)
        assertTrue("clickable action is under 48dp: $action", action.height >= minTouchHeightPx)
    }

}
