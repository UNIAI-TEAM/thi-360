package vn.unicom.thi360.feature.prepare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
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
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h915dp")
class PrepareStateRestorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Thi360Application
    private lateinit var server: ContractFakeServer

    private val entitlementId = UUID.fromString("00000000-0000-7000-8000-000000000001")
    private val preparePath = "/api/v1/me/entitlements/$entitlementId/prepare"

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = ContractFakeServer().start()
        app.setTestOverrides(connectivityMonitor = MutableConnectivityMonitor(initialOnline = true))
    }

    @After
    fun tearDown() {
        PrepareSessionMemory.clearAllForTesting()
        AppSessionContainer.setTestStack(app, null)
        app.setTestOverrides()
        server.shutdown()
    }

    private fun createActiveStack() = Thi360ApiFactory.createSessionStack(
        baseUrl = server.url("/"),
        environment = Environment.DEV,
        tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "restore-access",
                refreshToken = "restore-refresh",
                accessExpiresIn = 3600,
                refreshExpiresIn = 86400,
                mustChangePassword = false,
                accountId = "acc-restore",
                username = "hv01",
            ),
        ),
        localeProvider = { AppLocale.VI },
    )

    @Test
    fun rotationFoldAndResizePreserveSelectionWithoutDuplicateApiCalls() {
        val response = PrepareTestFixtures.prepareWithSubjectChoice(entitlementId = entitlementId)
        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(response))

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)
        var widthDp by mutableStateOf(412.dp)
        var heightDp by mutableStateOf(915.dp)
        lateinit var navigator: AppNavigator
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            val navController = rememberNavController()
            navigator = AppNavigator(navController)
            CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                Box(modifier = Modifier.size(width = widthDp, height = heightDp)) {
                    AppRoot(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        onSelectLocale = {},
                        onSelectThemeMode = {},
                        navController = navController,
                        navigator = navigator,
                        sessionManager = stack.sessionManager,
                        validateOnLaunch = false,
                    )
                }
            }
        }

        composeRule.runOnIdle {
            navigator.switchTab(Destination.MyExams, reload = false)
            navigator.openPrepare(entitlementId.toString())
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.preChoiceOption("SCIENCE")).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("GET", preparePath))

        composeRule.onNodeWithTag(PrepareTestTags.preChoiceOption("SCIENCE")).performClick()
        composeRule.onNodeWithTag(PrepareTestTags.LIST)
            .performScrollToNode(hasTestTag(PrepareTestTags.subjectCheckbox("LY")))
        composeRule.onNodeWithTag(PrepareTestTags.subjectCheckbox("LY")).performClick()
        composeRule.onNodeWithTag(PrepareTestTags.subjectCheckbox("HOA")).performClick()
        composeRule.onNodeWithTag(PrepareTestTags.SUBJECT_PROGRESS).assertTextContains("2/3", substring = true)

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PrepareTestTags.preChoiceOption("SCIENCE")).assertIsSelected()
        composeRule.onNodeWithTag(PrepareTestTags.SUBJECT_PROGRESS).assertTextContains("2/3", substring = true)
        assertEquals(1, server.requestCount("GET", preparePath))

        for ((w, h) in listOf(840.dp to 720.dp, 1280.dp to 800.dp, 360.dp to 640.dp)) {
            composeRule.runOnIdle {
                widthDp = w
                heightDp = h
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PrepareTestTags.preChoiceOption("SCIENCE")).assertIsSelected()
            composeRule.onNodeWithTag(PrepareTestTags.SUBJECT_PROGRESS).assertTextContains("2/3", substring = true)
            assertEquals(1, server.requestCount("GET", preparePath))
        }
    }

    @Test
    fun rotationAndResizePreserveWaitingPhaseWithoutDuplicateStartCalls() {
        val perPart = PrepareTestFixtures.loadPrepare("per-part")
        val abortedStart = PrepareTestFixtures.loadStart(200, "aborted")
        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(perPart))
        server.respondJson("POST", "/api/v1/me/attempts", 200, PrepareTestFixtures.encodeStart(abortedStart))

        val stack = createActiveStack()
        AppSessionContainer.setTestStack(app, stack)
        var widthDp by mutableStateOf(412.dp)
        var heightDp by mutableStateOf(915.dp)
        lateinit var navigator: AppNavigator
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            val navController = rememberNavController()
            navigator = AppNavigator(navController)
            CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                Box(modifier = Modifier.size(width = widthDp, height = heightDp)) {
                    AppRoot(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        onSelectLocale = {},
                        onSelectThemeMode = {},
                        navController = navController,
                        navigator = navigator,
                        sessionManager = stack.sessionManager,
                        validateOnLaunch = false,
                    )
                }
            }
        }

        composeRule.runOnIdle {
            navigator.switchTab(Destination.MyExams, reload = false)
            navigator.openPrepare(entitlementId.toString())
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.LIST)
                    .performScrollToNode(hasTestTag(PrepareTestTags.START_BUTTON))
                composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_SECTION).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("POST", "/api/v1/me/attempts"))
        assertEquals(1, server.requestCount("GET", preparePath))

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_REASON)
            .assertTextContains("Chưa tạo được bộ câu hỏi cho đề này.")
        assertEquals(1, server.requestCount("POST", "/api/v1/me/attempts"))
        assertEquals(1, server.requestCount("GET", preparePath))

        composeRule.runOnIdle {
            widthDp = 840.dp
            heightDp = 720.dp
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_SECTION).assertIsDisplayed()
        assertEquals(1, server.requestCount("POST", "/api/v1/me/attempts"))
        assertEquals(1, server.requestCount("GET", preparePath))
    }
}
