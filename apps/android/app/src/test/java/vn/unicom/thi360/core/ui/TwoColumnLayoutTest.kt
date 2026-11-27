package vn.unicom.thi360.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme

@RunWith(RobolectricTestRunner::class)
class TwoColumnLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun placesRegionsSideBySideAtTwoColumnAndStacksBelow840dp() {
        var widthDp by mutableStateOf(900.dp)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                val shellWidth = shellWidthForDp(widthDp.value.toInt())
                CompositionLocalProvider(LocalShellWidth provides shellWidth) {
                    Box(modifier = Modifier.size(width = widthDp, height = 600.dp)) {
                        TwoColumnLayout(
                            start = { Text("Start region", modifier = Modifier.testTag("start_content")) },
                            end = { Text("End region", modifier = Modifier.testTag("end_content")) },
                        )
                    }
                }
            }
        }

        // At 900dp (TwoColumn): side by side (end.left >= start.right).
        composeRule.onNodeWithTag(TwoColumnTestTags.SIDE_BY_SIDE).assertIsDisplayed()
        val startBoundsWide = composeRule.onNodeWithTag(TwoColumnTestTags.START_PANE).getUnclippedBoundsInRoot()
        val endBoundsWide = composeRule.onNodeWithTag(TwoColumnTestTags.END_PANE).getUnclippedBoundsInRoot()
        assertTrue(
            "Expected start and end regions side by side (startRight=${startBoundsWide.right}, endLeft=${endBoundsWide.left})",
            endBoundsWide.left >= startBoundsWide.right,
        )

        // At 600dp (Wide < 840dp): stacked vertically (end.top >= start.bottom).
        composeRule.runOnIdle { widthDp = 600.dp }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TwoColumnTestTags.STACKED).assertIsDisplayed()
        val startBoundsStacked = composeRule.onNodeWithTag(TwoColumnTestTags.START_PANE).getUnclippedBoundsInRoot()
        val endBoundsStacked = composeRule.onNodeWithTag(TwoColumnTestTags.END_PANE).getUnclippedBoundsInRoot()
        assertTrue(
            "Expected stacked regions below 840dp (startBottom=${startBoundsStacked.bottom}, endTop=${endBoundsStacked.top})",
            endBoundsStacked.top >= startBoundsStacked.bottom,
        )
    }
}
