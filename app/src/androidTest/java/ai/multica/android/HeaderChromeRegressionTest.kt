package ai.multica.android

import android.content.res.Configuration
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HeaderChromeRegressionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun statusAndTitleLayersRemainDistinctAcrossThemesAndWindowSizes() {
        try {
            setNightMode("no")
            assertNightMode(expected = false)
            assertHeaderLayout()
            assertStatusIconContrast(lightIcons = false, background = 0xFFF0F2F5.toInt())

            setDisplaySize("540x1400")
            assertHeaderLayout()
            setDisplaySize("1400x1000")
            assertHeaderLayout()

            setNightMode("yes")
            assertNightMode(expected = true)
            assertHeaderLayout()
            assertStatusIconContrast(lightIcons = true, background = 0xFF111318.toInt())
        } finally {
            setDisplaySize("reset")
            setNightMode("auto")
        }
    }

    private fun assertHeaderLayout() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasTestTag("app_title_bar")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        val status = compose.onNodeWithTag("status_bar_layer").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("app_title_bar").fetchSemanticsNode().boundsInRoot
        val topArea = compose.onNodeWithTag("app_top_area").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density

        assertTrue("status inset must be non-zero: $status", status.height > 0f)
        assertTrue("status and title layers overlap or have a gap: $status / $title", abs(status.bottom - title.top) < 1f)
        assertTrue("title bar changed from 56dp: $title", abs(title.height - (56f * density)) < 1.5f)
        assertTrue("header no longer spans the window: $topArea", topArea.left == 0f && topArea.right > 0f)
    }

    private fun assertStatusIconContrast(lightIcons: Boolean, background: Int) {
        var appearanceLight = false
        compose.activityRule.scenario.onActivity { activity ->
            appearanceLight = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .isAppearanceLightStatusBars
        }
        assertTrue("status icon appearance does not match theme", appearanceLight == !lightIcons)
        val foreground = if (lightIcons) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val ratio = ColorUtils.calculateContrast(foreground, background)
        assertTrue("status icon contrast is below 4.5:1 ($ratio)", ratio >= 4.5)
    }

    private fun assertNightMode(expected: Boolean) {
        compose.waitUntil(timeoutMillis = 10_000) {
            val mask = compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            mask == if (expected) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        compose.waitForIdle()
    }

    private fun setNightMode(mode: String) = shell("cmd uimode night $mode")

    private fun setDisplaySize(size: String) = shell("wm size $size")

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        descriptor.close()
        compose.waitForIdle()
    }
}
