package vn.unicom.thi360.feature.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class PlayerAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun touchTargetsHeadingsAndInteractiveLabelsMeetMinimums() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(attempt),
                    localUi = PlayerLocalUi(),
                )
            }
        }

        val root = composeRule.onRoot(useUnmergedTree = false).fetchSemanticsNode()
        val allNodes = collectNodes(root)
        assertTrue(
            "PlayerScreen must expose at least one TalkBack heading",
            allNodes.any { it.config.contains(SemanticsProperties.Heading) },
        )

        val interactive = allNodes.filter {
            it.config.contains(SemanticsActions.OnClick) || it.config.contains(SemanticsProperties.ToggleableState)
        }
        assertTrue("PlayerScreen must have interactive nodes", interactive.isNotEmpty())
        interactive.forEach { node ->
            val hasText = node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.isNotBlank() }
            val hasDesc = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.isNotBlank() }
            val hasEdit = node.config.getOrNull(SemanticsProperties.EditableText)?.text?.isNotBlank() == true
            val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: "id=${node.id}"
            assertTrue(
                "Interactive node '$tag' must have Text or ContentDescription for TalkBack",
                hasText || hasDesc || hasEdit,
            )
        }

        val requiredTargets = listOf(
            PlayerTestTags.TOP_BAR_BACK,
            PlayerTestTags.TOP_BAR_COLLAPSE_TOGGLE,
            PlayerTestTags.TOP_BAR_ACTION_BUTTON,
            PlayerTestTags.NARROW_GRID_BUTTON,
        )
        requiredTargets.forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
            val w = with(composeRule.density) { bounds.width.toDp() }
            val h = with(composeRule.density) { bounds.height.toDp() }
            assertTrue("Touch target '$tag' width=$w must be >= 48dp", w >= 47.5.dp)
            assertTrue("Touch target '$tag' height=$h must be >= 48dp", h >= 47.5.dp)
        }
    }

    @Test
    fun fontScale130NoClippingAndActionClickable() {
        verifyAtFontScale(1.3f)
    }

    @Test
    fun fontScale200NoClippingAndActionClickable() {
        verifyAtFontScale(2.0f)
    }

    private fun verifyAtFontScale(scale: Float) {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        var actionClicked = false

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = scale),
            ) {
                PlayerTestTheme {
                    PlayerScreen(
                        attemptId = attempt.attemptId.toString(),
                        engineState = PlayerTestFixtures.activeEngineState(attempt),
                        localUi = PlayerLocalUi(),
                        onOpenDialog = { actionClicked = true },
                    )
                }
            }
        }

        val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
        listOf(
            PlayerTestTags.TOP_BAR_BACK,
            PlayerTestTags.TOP_BAR_COLLAPSE_TOGGLE,
            PlayerTestTags.TOP_BAR_ACTION_BUTTON,
            PlayerTestTags.NARROW_GRID_BUTTON,
        ).forEach { tag ->
            val b = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("Node '$tag' left=${b.left} clips outside root left=${rootBounds.left} at scale=$scale", b.left >= rootBounds.left - 1.dp)
            assertTrue("Node '$tag' right=${b.right} clips outside root right=${rootBounds.right} at scale=$scale", b.right <= rootBounds.right + 1.dp)
        }

        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_ACTION_BUTTON)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue("Primary action CTA must stay clickable at fontScale=$scale", actionClicked)
    }

    private fun collectNodes(node: SemanticsNode): List<SemanticsNode> {
        val list = mutableListOf(node)
        for (child in node.children) {
            list.addAll(collectNodes(child))
        }
        return list
    }
}
