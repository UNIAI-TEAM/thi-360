package vn.unicom.thi360.feature.myexams

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
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.generated.model.AttemptHistoryItem
import vn.unicom.thi360.core.network.generated.model.MyExamCard
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.history.HistoryFeedState
import vn.unicom.thi360.feature.history.HistoryFilter
import vn.unicom.thi360.feature.history.HistoryPage
import vn.unicom.thi360.feature.history.HistoryScreen
import vn.unicom.thi360.feature.history.HistoryTestFixtures
import vn.unicom.thi360.feature.history.HistoryTestTags
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class MyExamsAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val activeAttemptId = UUID.fromString("00000000-0000-7000-8000-000000000701")
    private val resultAttemptId = UUID.fromString("00000000-0000-7000-8000-000000000702")

    private fun sampleMyExamsState(): MyExamsFeedState {
        val checking = MyExamsTestFixtures.makeCard(
            code = "A11Y-CHK",
            name = "Đề chờ cấp quyền",
            entitlementId = null,
            state = MyExamCard.State.CHECKING,
            pendingOrderCode = "DH001",
            hasPendingFulfillment = true,
            actions = listOf(MyExamCard.Actions.CHECKING),
        )
        val exam = MyExamsTestFixtures.makeCard(
            code = "A11Y-EX",
            name = "Đề luyện thi Đánh giá năng lực HSA",
            state = MyExamCard.State.IN_PROGRESS,
            activeAttemptId = activeAttemptId,
            resultAttemptId = resultAttemptId,
            actions = listOf(
                MyExamCard.Actions.CONTINUE,
                MyExamCard.Actions.VIEW_RESULT,
                MyExamCard.Actions.HISTORY,
                MyExamCard.Actions.BUY_MORE,
            ),
        )
        return MyExamsFeedState(loadState = LoadState.Loaded(listOf(checking, exam), stale = true))
    }

    private fun sampleHistoryState(): HistoryFeedState {
        val attempt = HistoryTestFixtures.makeAttempt(
            idSeed = "a11y-1",
            examName = "Đề luyện thi HSA tổng hợp",
            state = AttemptHistoryItem.State.SCORED,
            displayScore = BigDecimal("82.5"),
            scaleMax = BigDecimal("100"),
            action = AttemptHistoryItem.Action.VIEW_RESULT,
        )
        return HistoryFeedState(
            searchInput = "hsa",
            filter = HistoryFilter(q = "hsa", from = LocalDate.of(2026, 11, 1)),
            loadState = LoadState.Loaded(
                value = HistoryPage(
                    items = listOf(attempt),
                    currentPage = 0,
                    totalItems = 1,
                    totalPages = 1,
                    hasMore = false,
                ),
                stale = false,
            ),
        )
    }

    @Test
    fun myExamsTouchTargetsAndTalkBackLabels() {
        renderAtScale(1.0f) {
            MyExamsSurface()
        }

        assertHasHeading("MyExams")
        assertInteractiveNodesHaveAccessibleLabel("MyExams")
        assertTouchTargetsAtLeast48Dp(
            listTag = MyExamsTestTags.LIST,
            tags = listOf(
                MyExamsTestTags.STALE_RETRY_BUTTON,
                MyExamsTestTags.EXPLORE_BUTTON,
                MyExamsTestTags.checkingViewOrderButton("A11Y-CHK"),
                MyExamsTestTags.checkingReloadButton("A11Y-CHK"),
                MyExamsTestTags.examCardContinue("A11Y-EX"),
                MyExamsTestTags.examCardViewResult("A11Y-EX"),
                MyExamsTestTags.examCardHistory("A11Y-EX"),
                MyExamsTestTags.examCardBuyMore("A11Y-EX"),
            ),
        )
    }

    @Test
    fun myExamsFontScale130HasNoHorizontalClipping() {
        renderAtScale(1.3f) {
            MyExamsSurface()
        }

        assertNoHorizontalClipping(
            listTag = MyExamsTestTags.LIST,
            tags = listOf(
                ShellTestTags.screenTitle(Destination.MyExams),
                MyExamsTestTags.STALE_BANNER,
                MyExamsTestTags.EXPLORE_BUTTON,
                MyExamsTestTags.examCardContinue("A11Y-EX"),
                MyExamsTestTags.examCardViewResult("A11Y-EX"),
                MyExamsTestTags.examCardHistory("A11Y-EX"),
                MyExamsTestTags.examCardBuyMore("A11Y-EX"),
            ),
        )
    }

    @Test
    fun myExamsFontScale200RemainsScrollableAndClickable() {
        var clickedBuyMore = false
        renderAtScale(2.0f) {
            MyExamsSurface(onOpenCheckout = { clickedBuyMore = true })
        }

        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.examCardBuyMore("A11Y-EX")))
        composeRule.onNodeWithTag(MyExamsTestTags.examCardBuyMore("A11Y-EX"))
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertTrue("Buy more CTA must remain clickable at fontScale=2.0", clickedBuyMore)
    }

    @Test
    fun historyTouchTargetsAndTalkBackLabels() {
        val state = sampleHistoryState()
        val attemptId = (state.loadState as LoadState.Loaded).value.items.first().attemptId.toString()

        renderAtScale(1.0f) {
            HistorySurface(state = state)
        }

        assertHasHeading("History")
        assertInteractiveNodesHaveAccessibleLabel("History")
        assertTouchTargetsAtLeast48Dp(
            listTag = HistoryTestTags.LIST,
            tags = listOf(
                HistoryTestTags.SEARCH_FIELD,
                HistoryTestTags.SEARCH_CLEAR_BUTTON,
                HistoryTestTags.KIND_DROPDOWN,
                HistoryTestTags.SCOPE_DROPDOWN,
                HistoryTestTags.FROM_DATE_FIELD,
                HistoryTestTags.FROM_DATE_CLEAR,
                HistoryTestTags.TO_DATE_FIELD,
                HistoryTestTags.CLEAR_FILTERS_BUTTON,
                HistoryTestTags.rowTitle(attemptId),
                HistoryTestTags.rowViewResultButton(attemptId),
            ),
        )
    }

    @Test
    fun historyFontScale130HasNoHorizontalClipping() {
        val state = sampleHistoryState()
        val attemptId = (state.loadState as LoadState.Loaded).value.items.first().attemptId.toString()

        renderAtScale(1.3f) {
            HistorySurface(state = state)
        }

        assertNoHorizontalClipping(
            listTag = HistoryTestTags.LIST,
            tags = listOf(
                ShellTestTags.screenTitle(Destination.History),
                HistoryTestTags.FILTER_SECTION,
                HistoryTestTags.CLEAR_FILTERS_BUTTON,
                HistoryTestTags.rowViewResultButton(attemptId),
            ),
        )
    }

    @Test
    fun historyFontScale200RemainsScrollableAndClickable() {
        val state = sampleHistoryState()
        val attemptId = (state.loadState as LoadState.Loaded).value.items.first().attemptId.toString()
        var openedResult = false

        renderAtScale(2.0f) {
            HistorySurface(state = state, onOpenResult = { openedResult = true })
        }

        composeRule.onNodeWithTag(HistoryTestTags.LIST)
            .performScrollToNode(hasTestTag(HistoryTestTags.rowViewResultButton(attemptId)))
        composeRule.onNodeWithTag(HistoryTestTags.rowViewResultButton(attemptId))
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertTrue("View result CTA must remain clickable at fontScale=2.0", openedResult)
    }

    @Composable
    private fun MyExamsSurface(onOpenCheckout: (String) -> Unit = {}) {
        Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
            MyExamsScreen(
                feedState = sampleMyExamsState(),
                greetingName = "Nguyễn Văn An",
                supportContact = SupportContact("1900 1234", "hotro@thi360.vn", "08:00-21:00"),
                showCompactBrandHeader = true,
                onPullToRefresh = {},
                onRetry = {},
                onExploreExams = {},
                onOpenOrder = {},
                onOpenProduct = {},
                onContinueAttempt = {},
                onStartPrepare = {},
                onOpenResult = {},
                onOpenHistoryReset = {},
                onOpenCheckout = onOpenCheckout,
            )
        }
    }

    @Composable
    private fun HistorySurface(
        state: HistoryFeedState,
        onOpenResult: (String) -> Unit = {},
    ) {
        Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
            HistoryScreen(
                feedState = state,
                showCompactBrandHeader = true,
                onSearchInputChanged = {},
                onSearchSubmit = {},
                onSelectKind = {},
                onSelectScope = {},
                onSelectFromDate = {},
                onSelectToDate = {},
                onClearAllFilters = {},
                onLoadNextPage = {},
                onRetry = {},
                onGoToMyExams = {},
                onContinueAttempt = {},
                onOpenResult = onOpenResult,
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
            val hasEditable = node.config.getOrNull(SemanticsProperties.EditableText)?.text?.isNotBlank() == true
            val hasDesc = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.isNotBlank() }
            val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: "id=${node.id}"
            assertTrue(
                "$screenName interactive node '$tag' must have TalkBack text or contentDescription",
                hasText || hasEditable || hasDesc,
            )
        }
    }

    private fun assertTouchTargetsAtLeast48Dp(listTag: String, tags: List<String>) {
        tags.forEach { tag ->
            composeRule.onNodeWithTag(listTag).performScrollToNode(hasTestTag(tag))
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
            val w = with(composeRule.density) { bounds.width.toDp() }
            val h = with(composeRule.density) { bounds.height.toDp() }
            assertTrue("Touch target '$tag' width=$w must be >= 48dp", w >= 47.5.dp)
            assertTrue("Touch target '$tag' height=$h must be >= 48dp", h >= 47.5.dp)
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
