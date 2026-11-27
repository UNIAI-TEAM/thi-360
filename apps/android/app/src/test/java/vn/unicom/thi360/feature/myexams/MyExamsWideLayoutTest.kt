package vn.unicom.thi360.feature.myexams

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.ui.TwoColumnTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class MyExamsWideLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Thi360Application
    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = ContractFakeServer().start()
        app.setTestOverrides(connectivityMonitor = MutableConnectivityMonitor(initialOnline = true))
    }

    @After
    fun tearDown() {
        MyExamsSessionMemory.clearAllForTesting()
        AppSessionContainer.setTestStack(app, null)
        app.setTestOverrides()
        server.shutdown()
    }

    @Test
    @Config(qualifiers = "w600dp-h900dp")
    fun medium600dpLaysOutCardsSideBySideInReadingOrder() {
        renderFourCardsAndAssertMultiColumn(expectTwoColumnLayout = false)
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun wide840dpUsesTwoColumnLayoutWithoutDetailPane() {
        renderFourCardsAndAssertMultiColumn(expectTwoColumnLayout = true)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun desktop1280dpUsesTwoColumnLayoutFullWidthWithoutDetailPane() {
        renderFourCardsAndAssertMultiColumn(expectTwoColumnLayout = true)
    }

    private fun renderFourCardsAndAssertMultiColumn(expectTwoColumnLayout: Boolean) {
        val cards = listOf(
            MyExamsTestFixtures.makeCard(code = "C1", name = "Bài 1"),
            MyExamsTestFixtures.makeCard(code = "C2", name = "Bài 2"),
            MyExamsTestFixtures.makeCard(code = "C3", name = "Bài 3"),
            MyExamsTestFixtures.makeCard(code = "C4", name = "Bài 4"),
        )
        server.respondJson("GET", "/api/v1/me/exams", 200, MyExamsTestFixtures.encodeResponse(cards))
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens("access", "refresh", 300, 604800, false, "hv01", "acc-1"),
            ),
            localeProvider = { AppLocale.VI },
        )
        AppSessionContainer.setTestStack(app, stack)

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.examCard("C1")).assertIsDisplayed()
                composeRule.onNodeWithTag(MyExamsTestTags.examCard("C2")).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        val c1Bounds = composeRule.onNodeWithTag(MyExamsTestTags.examCard("C1")).getUnclippedBoundsInRoot()
        val c2Bounds = composeRule.onNodeWithTag(MyExamsTestTags.examCard("C2")).getUnclippedBoundsInRoot()
        val c3Bounds = composeRule.onNodeWithTag(MyExamsTestTags.examCard("C3")).getUnclippedBoundsInRoot()

        assertTrue("C2 must be to the right of C1", c2Bounds.left > c1Bounds.left)
        assertTrue(
            "C1 and C2 must share the same row top (c1=${c1Bounds.top}, c2=${c2Bounds.top})",
            kotlin.math.abs((c1Bounds.top - c2Bounds.top).value) < 4f,
        )
        assertTrue("C3 must be below C1 in reading order", c3Bounds.top > c1Bounds.top)

        if (expectTwoColumnLayout) {
            val sideBySideCount = composeRule.onAllNodesWithTag(TwoColumnTestTags.SIDE_BY_SIDE)
                .fetchSemanticsNodes().size
            assertTrue("Expected TwoColumnLayout side-by-side rows at >= 840dp", sideBySideCount > 0)
            composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_TWO_PANE).assertDoesNotExist()
        }
    }
}
