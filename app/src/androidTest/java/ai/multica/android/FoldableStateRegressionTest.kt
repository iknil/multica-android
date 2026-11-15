package ai.multica.android

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
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
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
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
        compose.onNodeWithTag("login_otp_input").assertIsFocused()

        // Exercise real window-manager configuration changes rather than only Activity.recreate.
        // Always remove the override so a failed assertion cannot contaminate later device runs.
        try {
            setDisplaySize("540x1400")
            assertOtpSurvivesResize(requireNinetySixPixels = true)
            setDisplaySize("1400x1000")
            assertOtpSurvivesResize(requireNinetySixPixels = false)
            setDisplaySize("540x1400")
            assertOtpSurvivesResize(requireNinetySixPixels = true)
        } finally {
            setDisplaySize("reset")
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

    private fun assertOtpSurvivesResize(requireNinetySixPixels: Boolean) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag("login_otp_input")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("login_otp_input")
            .assertTextContains("123456")
            .assertIsFocused()
        assertPrimaryActionFullyVisible(requireNinetySixPixels)
    }

    private fun setDisplaySize(size: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("wm size $size")
        FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        descriptor.close()
        compose.waitForIdle()
    }

    private fun assertPrimaryActionFullyVisible(requireNinetySixPixels: Boolean = true) {
        compose.waitForIdle()
        val action = compose.onNodeWithTag("login_primary_action")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode()
            .boundsInRoot
        val viewport = compose.onNodeWithTag("login_viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("clickable action is clipped above the viewport: $action vs $viewport", action.top >= viewport.top)
        assertTrue("clickable action is clipped below the viewport: $action vs $viewport", action.bottom <= viewport.bottom)
        if (requireNinetySixPixels) {
            assertTrue("clickable action is under 96px at 320dpi: $action", action.height >= 96f)
        }
    }

}
