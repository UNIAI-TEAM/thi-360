package vn.unicom.thi360.feature.history

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.catalog.CatalogOptionsCache
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.PageResponseAttemptHistoryItem
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class HistorySearchTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun debouncesRapidTypingSubmitsImmediatelyOnImeAndDropsWhitespaceOnly() {
        val dispatcher = StandardTestDispatcher()
        val testScope = TestScope(dispatcher)
        val recordedParams = mutableListOf<HistoryApiParams>()
        val page = PageResponseAttemptHistoryItem(
            items = listOf(HistoryTestFixtures.makeAttempt(idSeed = "search-1", examName = "Đề HSA 01")),
            page = 0,
            propertySize = 20,
            totalItems = 1,
            totalPages = 1,
        )
        val feed = HistoryFeed(
            fetchHistory = { params ->
                recordedParams.add(params)
                ApiResult.Success(page)
            },
            catalogOptionsCache = CatalogOptionsCache(fetcher = { ApiResult.Success(emptyList()) }),
            connectivityMonitor = MutableConnectivityMonitor(initialOnline = true),
            debounceDelay = { ms -> delay(ms) },
            scope = testScope,
        )

        feed.onAppear()
        testScope.runCurrent()
        assertEquals(1, recordedParams.size)
        assertNull(recordedParams.last().q)

        composeRule.setContent {
            val state by feed.state.collectAsState()
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                HistoryScreen(
                    feedState = state,
                    showCompactBrandHeader = false,
                    onSearchInputChanged = feed::onSearchInputChanged,
                    onSearchSubmit = feed::onSearchSubmit,
                    onSelectKind = feed::selectExamKind,
                    onSelectScope = feed::selectScope,
                    onSelectFromDate = feed::selectFromDate,
                    onSelectToDate = feed::selectToDate,
                    onClearAllFilters = feed::clearAllFilters,
                    onLoadNextPage = feed::loadNextPage,
                    onRetry = feed::retry,
                    onGoToMyExams = {},
                    onContinueAttempt = {},
                    onOpenResult = {},
                )
            }
        }

        composeRule.onNodeWithTag(HistoryTestTags.SEARCH_FIELD).performTextInput("h")
        testScope.advanceTimeBy(100L)
        composeRule.onNodeWithTag(HistoryTestTags.SEARCH_FIELD).performTextInput("s")
        testScope.advanceTimeBy(100L)
        composeRule.onNodeWithTag(HistoryTestTags.SEARCH_FIELD).performTextInput("a")
        testScope.advanceTimeBy(200L)
        assertEquals(1, recordedParams.size)

        testScope.advanceTimeBy(100L)
        testScope.runCurrent()
        assertEquals(2, recordedParams.size)
        assertEquals("hsa", recordedParams.last().q)
        assertEquals(0, recordedParams.last().page)
        assertEquals(20, recordedParams.last().size)
        composeRule.onNodeWithTag(HistoryTestTags.SEARCH_FIELD).assertTextContains("hsa")

        composeRule.onNodeWithTag(HistoryTestTags.SEARCH_FIELD).performTextClearance()
        composeRule.onNodeWithTag(HistoryTestTags.SEARCH_FIELD).performTextInput("tsa")
        testScope.advanceTimeBy(100L)
        composeRule.onNodeWithTag(HistoryTestTags.SEARCH_FIELD).performImeAction()
        testScope.runCurrent()
        assertEquals(3, recordedParams.size)
        assertEquals("tsa", recordedParams.last().q)

        testScope.advanceTimeBy(500L)
        testScope.runCurrent()
        assertEquals(3, recordedParams.size)

        composeRule.onNodeWithTag(HistoryTestTags.SEARCH_FIELD).performTextClearance()
        composeRule.onNodeWithTag(HistoryTestTags.SEARCH_FIELD).performTextInput("   ")
        testScope.advanceTimeBy(300L)
        testScope.runCurrent()
        assertEquals(4, recordedParams.size)
        assertNull(recordedParams.last().q)
    }
}
