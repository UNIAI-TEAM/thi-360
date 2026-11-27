package vn.unicom.thi360.feature.prepare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.prepare.waiting.AttemptWaitingContent
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class PrepareAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val supportContact = SupportContact("1900 1234", "hotro@thi360.vn", "08:00-21:00")

    private fun samplePrepareState(): PrepareScreenState {
        val response = PrepareTestFixtures.prepareWithSubjectChoice(preselectedCode = "SCIENCE")
        val selection = PreChoiceSelection(optionCode = "SCIENCE", subjectCodes = linkedSetOf("LY", "HOA", "SINH"))
        return PrepareScreenState(
            load = LoadState.Loaded(response),
            selection = selection,
            gate = resolveStartGate(response, selection),
            start = StartState.Idle,
        )
    }

    @Test
    fun prepareTouchTargetsAndTalkBackLabels() {
        renderAtScale(1.0f) {
            PrepareSurface()
        }

        assertHasHeading("PrepareScreen")
        assertInteractiveNodesHaveAccessibleLabel("PrepareScreen")
        assertFixedTouchTargetAtLeast48Dp(PrepareTestTags.BACK_BUTTON)
        assertTouchTargetsAtLeast48Dp(
            listTag = PrepareTestTags.LIST,
            tags = listOf(
                PrepareTestTags.preChoiceOption("SCIENCE"),
                PrepareTestTags.preChoiceOption("ANH"),
                PrepareTestTags.subjectCheckbox("LY"),
                PrepareTestTags.START_BUTTON,
                PrepareTestTags.READY_BACK_BUTTON,
            ),
        )
    }

    @Test
    fun prepareFontScale130HasNoHorizontalClipping() {
        renderAtScale(1.3f) {
            PrepareSurface()
        }

        assertNoHorizontalClipping(
            listTag = PrepareTestTags.LIST,
            tags = listOf(
                PrepareTestTags.HEADER_TITLE,
                PrepareTestTags.PRE_CHOICE_SECTION,
                PrepareTestTags.READY_SECTION,
                PrepareTestTags.START_BUTTON,
                PrepareTestTags.STRUCTURE_SECTION,
                PrepareTestTags.INSTRUCTIONS_SECTION,
            ),
        )
    }

    @Test
    fun prepareFontScale200RemainsScrollableAndClickable() {
        var started = false
        renderAtScale(2.0f) {
            PrepareSurface(onStartAttempt = { started = true })
        }

        composeRule.onNodeWithTag(PrepareTestTags.LIST)
            .performScrollToNode(hasTestTag(PrepareTestTags.START_BUTTON))
        composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertTrue("Start CTA must remain clickable at fontScale=2.0", started)
    }

    @Test
    fun waitingVariantsTouchTargetsLabelsAndFontScales() {
        renderAtScale(1.0f) {
            WaitingSurface(phase = WaitingPhase.Polling(2_000L))
        }
        assertHasHeading("WaitingPolling")
        assertInteractiveNodesHaveAccessibleLabel("WaitingPolling")
        assertFixedTouchTargetAtLeast48Dp(PrepareTestTags.WAITING_BACK_BUTTON)
        assertTouchTargetsAtLeast48Dp(
            listTag = PrepareTestTags.WAITING_LIST,
            tags = listOf(PrepareTestTags.WAITING_REASSURE_NOTE),
        )
    }

    @Test
    fun waitingBusyFontScale130HasNoHorizontalClipping() {
        renderAtScale(1.3f) {
            WaitingSurface(phase = WaitingPhase.Busy)
        }
        assertNoHorizontalClipping(
            listTag = PrepareTestTags.WAITING_LIST,
            tags = listOf(
                PrepareTestTags.WAITING_BUSY_TITLE,
                PrepareTestTags.WAITING_BUSY_DESCRIPTION,
                PrepareTestTags.WAITING_BUSY_BACK_BUTTON,
            ),
        )
    }

    @Test
    fun waitingFailedFontScale200RemainsScrollableAndClickable() {
        var retried = false
        renderAtScale(2.0f) {
            WaitingSurface(
                phase = WaitingPhase.Failed("STALE_STARTING"),
                onRetry = { retried = true },
            )
        }

        composeRule.onNodeWithTag(PrepareTestTags.WAITING_LIST)
            .performScrollToNode(hasTestTag(PrepareTestTags.WAITING_RETRY_BUTTON))
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_RETRY_BUTTON)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertTrue("Retry CTA must remain clickable at fontScale=2.0", retried)
    }

    @Composable
    private fun PrepareSurface(onStartAttempt: () -> Unit = {}) {
        Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
            CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                PrepareScreen(
                    state = samplePrepareState(),
                    supportContact = supportContact,
                    onBack = {},
                    onMyExams = {},
                    onRetryLoad = {},
                    onSelectOption = {},
                    onToggleSubject = {},
                    onStartAttempt = onStartAttempt,
                    onContinueAttempt = {},
                    onBuyMoreAttempts = {},
                    onRetryWaiting = {},
                    onLeaveWaiting = {},
                )
            }
        }
    }

    @Composable
    private fun WaitingSurface(
        phase: WaitingPhase,
        onRetry: () -> Unit = {},
    ) {
        Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
            AttemptWaitingContent(
                examTitle = "HSA đầy đủ (thu nhỏ)",
                phase = phase,
                abortReason = (phase as? WaitingPhase.Failed)?.abortReason,
                firstPartName = "Toán học và xử lí số liệu",
                firstPartMinutes = 75,
                isSharedTiming = false,
                supportContact = supportContact,
                onBack = {},
                onMyExams = {},
                onRetry = onRetry,
            )
        }
    }

    private fun renderAtScale(scale: Float, content: @Composable () -> Unit) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = scale),
            ) {
                content()
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertHasHeading(screenName: String) {
        val root = composeRule.onRoot(useUnmergedTree = false).fetchSemanticsNode()
        val headings = collectNodes(root).filter { it.config.contains(SemanticsProperties.Heading) }
        assertTrue("$screenName must expose at least one TalkBack heading", headings.isNotEmpty())
    }

    private fun assertInteractiveNodesHaveAccessibleLabel(screenName: String) {
        val root = composeRule.onRoot(useUnmergedTree = false).fetchSemanticsNode()
        val interactive = collectNodes(root).filter {
            it.config.contains(SemanticsActions.OnClick) || it.config.contains(SemanticsProperties.ToggleableState)
        }
        assertTrue("$screenName must contain interactive controls", interactive.isNotEmpty())
        interactive.forEach { node ->
            val hasText = node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.isNotBlank() }
            val hasDesc = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.isNotBlank() }
            val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: "id=${node.id}"
            assertTrue(
                "$screenName interactive node '$tag' must have TalkBack text or contentDescription",
                hasText || hasDesc,
            )
        }
    }

    private fun assertFixedTouchTargetAtLeast48Dp(tag: String) {
        val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
        val w = with(composeRule.density) { bounds.width.toDp() }
        val h = with(composeRule.density) { bounds.height.toDp() }
        assertTrue("Touch target '$tag' width=$w must be >= 48dp", w >= 47.5.dp)
        assertTrue("Touch target '$tag' height=$h must be >= 48dp", h >= 47.5.dp)
    }

    private fun assertTouchTargetsAtLeast48Dp(listTag: String, tags: List<String>) {
        tags.forEach { tag ->
            composeRule.onNodeWithTag(listTag).performScrollToNode(hasTestTag(tag))
            assertFixedTouchTargetAtLeast48Dp(tag)
        }
    }

    private fun assertNoHorizontalClipping(listTag: String, tags: List<String>) {
        val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
        tags.forEach { tag ->
            composeRule.onNodeWithTag(listTag).performScrollToNode(hasTestTag(tag))
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("Node '$tag' must have positive size", bounds.width > 0.dp && bounds.height > 0.dp)
            assertTrue("Node '$tag' left=${bounds.left} clips left", bounds.left >= rootBounds.left - 1.dp)
            assertTrue("Node '$tag' right=${bounds.right} clips right", bounds.right <= rootBounds.right + 1.dp)
        }
    }

    private fun collectNodes(node: SemanticsNode): List<SemanticsNode> {
        val out = mutableListOf(node)
        for (child in node.children) {
            out.addAll(collectNodes(child))
        }
        return out
    }
}
