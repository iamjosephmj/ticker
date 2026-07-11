package tech.ssemaj.processclock

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end: kernel tick → ViewModel → Compose → pixels-worth of text. */
@RunWith(AndroidJUnit4::class)
class ClockUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun clockText(): String =
        rule.onNodeWithTag("clock").fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString { it.text }

    @Test
    fun clockDigitsAdvanceEverySecond() {
        rule.waitForIdle()
        val first = clockText()
        assertTrue("not a HH:mm:ss clock: '$first'", first.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))

        // Two wall-clock seconds later the digits must have changed.
        Thread.sleep(2_100)
        rule.waitForIdle()
        val second = clockText()
        assertNotEquals("clock frozen at $first", first, second)
    }
}
