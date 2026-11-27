package vn.unicom.thi360.feature.myexams

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
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
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.routes.AppRoute
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class BundleGroupTest {

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
    fun bundleGroupShowsCountCodeFootnoteOpensBundleCodeAndCrossRefFocusesTargetHeading() {
        val bundleX = MyExamsTestFixtures.makeBundle("X", "BUNDLE-CODE-X", "Bộ đề X")
        val bundleY = MyExamsTestFixtures.makeBundle("Y", "BUNDLE-CODE-Y", "Bộ đề Y")
        val bundleZ = MyExamsTestFixtures.makeBundle("Z", "BUNDLE-CODE-Z", "Bộ đề Z")

        val card1 = MyExamsTestFixtures.makeCard(code = "EXAM-1", name = "Bài 1", bundles = listOf(bundleX))
        val card2 = MyExamsTestFixtures.makeCard(code = "EXAM-2", name = "Bài 2", bundles = listOf(bundleX))
        val card3 = MyExamsTestFixtures.makeCard(code = "EXAM-3", name = "Bài 3", bundles = listOf(bundleZ, bundleX))
        val cardF = MyExamsTestFixtures.makeCard(code = "EXAM-F", name = "Bài F", bundles = listOf(bundleX, bundleY))

        server.respondJson(
            "GET",
            "/api/v1/me/exams",
            200,
            MyExamsTestFixtures.encodeResponse(listOf(card1, card2, card3, cardF)),
        )

        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens("access", "refresh", 300, 604800, false, "hv01", "acc-1"),
            ),
            localeProvider = { AppLocale.VI },
        )
        AppSessionContainer.setTestStack(app, stack)
        var capturedNavController: NavHostController? = null

        composeRule.setContent {
            val navController = rememberNavController()
            capturedNavController = navController
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                navController = navController,
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.bundleHeading(bundleX.bundleId)).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(MyExamsTestTags.bundleCount(bundleX.bundleId)).assertTextEquals("4 bài")
        composeRule.onNodeWithText("BUNDLE-CODE-X").assertIsDisplayed()
        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.bundleFootnote(bundleX.bundleId)))
        composeRule.onNodeWithTag(MyExamsTestTags.bundleFootnote(bundleX.bundleId)).assertIsDisplayed()

        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.bundleViewButton(bundleX.bundleId)))
        composeRule.onNodeWithTag(MyExamsTestTags.bundleViewButton(bundleX.bundleId)).performClick()
        composeRule.waitForIdle()
        val routeX = checkNotNull(capturedNavController).currentBackStackEntry?.arguments
            ?.getString(AppRoute.ARG_CODE)
        assertEquals("BUNDLE-CODE-X", routeX)

        composeRule.runOnIdle { checkNotNull(capturedNavController).popBackStack() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.bundleGroup(bundleY.bundleId)))
        composeRule.onNodeWithTag(MyExamsTestTags.bundleHeading(bundleY.bundleId)).assertTextEquals("Bộ đề Y")
        composeRule.onNodeWithTag(MyExamsTestTags.bundleCount(bundleY.bundleId)).assertTextEquals("1 bài")
        composeRule.onNodeWithTag(MyExamsTestTags.bundleFootnote(bundleY.bundleId)).assertIsDisplayed()
        composeRule.onNodeWithTag(MyExamsTestTags.bundleCrossRef(bundleY.bundleId, "EXAM-F"))
            .assertIsDisplayed()

        composeRule.onNodeWithTag(MyExamsTestTags.bundleViewButton(bundleY.bundleId)).performClick()
        composeRule.waitForIdle()
        val routeY = checkNotNull(capturedNavController).currentBackStackEntry?.arguments
            ?.getString(AppRoute.ARG_CODE)
        assertEquals("BUNDLE-CODE-Y", routeY)

        composeRule.runOnIdle { checkNotNull(capturedNavController).popBackStack() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.bundleCrossRef(bundleY.bundleId, "EXAM-F")))
        composeRule.onNodeWithTag(MyExamsTestTags.bundleCrossRef(bundleY.bundleId, "EXAM-F"))
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.bundleHeading(bundleX.bundleId))
                    .assertIsDisplayed()
                    .assertIsFocused()
                true
            }.getOrDefault(false)
        }
    }
}
