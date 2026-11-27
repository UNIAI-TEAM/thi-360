package vn.unicom.thi360.feature.myexams

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
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
import vn.unicom.thi360.core.network.generated.model.MyExamCard
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class MyExamsScreenTest {

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
    fun firstOpen() {
        val meWithWhitespace = MyExamsTestFixtures.loadDefaultMe().copy(
            fullName = "  Nguyễn Văn An  ",
            username = "hv01",
        )
        server.respondJson("GET", "/api/v1/me", 200, MyExamsTestFixtures.encodeMe(meWithWhitespace))
        server.respondIndexed("listMyExams", 200, fixtureNameFilter = "my-exams-list-200.json")

        val stack = createActiveStack(AppLocale.VI, username = "hv01")
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
                composeRule.onNodeWithTag(MyExamsTestTags.SECTION_HEADER).assertIsDisplayed()
                composeRule.onNodeWithTag(MyExamsTestTags.SUBTITLE)
                    .assertTextContains("Chào Nguyễn Văn An.", substring = true)
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.MyExams))
            .assertTextEquals("Bài thi của tôi")
        composeRule.onNodeWithTag(ShellTestTags.PLACEHOLDER_EMPTY_TITLE).assertDoesNotExist()

        val examsRequests = server.requests("GET", "/api/v1/me/exams")
        assertEquals(1, examsRequests.size)
        assertTrue(examsRequests.first().getHeader("Authorization").orEmpty().startsWith("Bearer "))
    }

    @Test
    fun greetingFallback() {
        val blankFullNameMe = MyExamsTestFixtures.loadDefaultMe().copy(
            fullName = "   ",
            username = "hv01",
        )
        server.respondJson("GET", "/api/v1/me", 200, MyExamsTestFixtures.encodeMe(blankFullNameMe))
        server.respondIndexed("listMyExams", 200, fixtureNameFilter = "my-exams-list-200.json")

        val stack = createActiveStack(AppLocale.VI, username = "hv01")
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
                composeRule.onNodeWithTag(MyExamsTestTags.SECTION_HEADER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(MyExamsTestTags.SUBTITLE)
            .assertIsDisplayed()
            .assertTextContains("Chào hv01.", substring = true)
    }

    @Test
    fun english() {
        val singleCard = listOf(MyExamsTestFixtures.makeCard(code = "EN-1", name = "HSA 01"))
        val threeCards = listOf(
            MyExamsTestFixtures.makeCard(code = "EN-1", name = "HSA 01"),
            MyExamsTestFixtures.makeCard(code = "EN-2", name = "HSA 02"),
            MyExamsTestFixtures.makeCard(code = "EN-3", name = "HSA 03"),
        )
        server.respondSequence(
            "GET",
            "/api/v1/me/exams",
            { ContractFakeServer.jsonResponse(200, MyExamsTestFixtures.encodeResponse(singleCard)) },
            { ContractFakeServer.jsonResponse(200, MyExamsTestFixtures.encodeResponse(threeCards)) },
        )

        val stack = createActiveStack(AppLocale.EN, username = "hv01")
        AppSessionContainer.setTestStack(app, stack)

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.EN),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.SECTION_COUNT).assertTextEquals("1 exam")
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.MyExams))
            .assertTextEquals("My exams")
        composeRule.onNodeWithText("Purchased exams").assertIsDisplayed()
        composeRule.onNodeWithTag(MyExamsTestTags.EXPLORE_BUTTON).assertTextContains("Explore exams")

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.SECTION_COUNT).assertTextEquals("3 exams")
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun groups() {
        val bundleX = MyExamsTestFixtures.makeBundle("X", "BUNDLE-X", "Bộ đề X")
        val bundleY = MyExamsTestFixtures.makeBundle("Y", "BUNDLE-Y", "Bộ đề Y")
        val cardA = MyExamsTestFixtures.makeCard(
            code = "A",
            name = "Bài A",
            entitlementId = null,
            state = MyExamCard.State.CHECKING,
            pendingOrderCode = "DH001",
            hasPendingFulfillment = true,
            actions = listOf(MyExamCard.Actions.CHECKING),
        )
        val cardB = MyExamsTestFixtures.makeCard(code = "B", name = "Bài B", bundles = listOf(bundleX))
        val cardC = MyExamsTestFixtures.makeCard(code = "C", name = "Bài C", bundles = emptyList())
        val cardD = MyExamsTestFixtures.makeCard(code = "D", name = "Bài D", bundles = listOf(bundleY))
        val cardE = MyExamsTestFixtures.makeCard(code = "E", name = "Bài E", bundles = listOf(bundleX))

        server.respondJson(
            "GET",
            "/api/v1/me/exams",
            200,
            MyExamsTestFixtures.encodeResponse(listOf(cardA, cardB, cardC, cardD, cardE)),
        )

        val stack = createActiveStack(AppLocale.VI)
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
                composeRule.onNodeWithTag(MyExamsTestTags.SECTION_COUNT).assertTextEquals("5 bài")
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(MyExamsTestTags.CHECKING_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(MyExamsTestTags.checkingCard("A")).assertIsDisplayed()

        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.bundleGroup(bundleX.bundleId)))
        composeRule.onNodeWithTag(MyExamsTestTags.bundleHeading(bundleX.bundleId)).assertTextEquals("Bộ đề X")
        composeRule.onNodeWithTag(MyExamsTestTags.bundleCount(bundleX.bundleId)).assertTextEquals("2 bài")
        composeRule.onNodeWithTag(MyExamsTestTags.examCard("B")).assertIsDisplayed()
        composeRule.onNodeWithTag(MyExamsTestTags.examCard("E")).assertIsDisplayed()

        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.bundleGroup(bundleY.bundleId)))
        composeRule.onNodeWithTag(MyExamsTestTags.bundleHeading(bundleY.bundleId)).assertTextEquals("Bộ đề Y")
        composeRule.onNodeWithTag(MyExamsTestTags.examCard("D")).assertIsDisplayed()

        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.STANDALONE_SECTION_HEADER))
        composeRule.onNodeWithTag(MyExamsTestTags.STANDALONE_SECTION_COUNT).assertTextEquals("1 bài")
        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.examCard("C")))
        composeRule.onNodeWithTag(MyExamsTestTags.examCard("C")).assertIsDisplayed()
    }

    @Test
    fun noBundlesHidesStandaloneHeadingAndCheckingOnlyHidesOtherGroups() {
        val standaloneOnly = listOf(MyExamsTestFixtures.makeCard(code = "SOLO-1", name = "Bài lẻ 1"))
        val checkingOnly = listOf(
            MyExamsTestFixtures.makeCard(
                code = "CHK-1",
                name = "Bài kiểm tra 1",
                entitlementId = null,
                state = MyExamCard.State.CHECKING,
                pendingOrderCode = "DH777",
                hasPendingFulfillment = true,
                actions = listOf(MyExamCard.Actions.CHECKING),
            ),
        )
        server.respondSequence(
            "GET",
            "/api/v1/me/exams",
            { ContractFakeServer.jsonResponse(200, MyExamsTestFixtures.encodeResponse(standaloneOnly)) },
            { ContractFakeServer.jsonResponse(200, MyExamsTestFixtures.encodeResponse(checkingOnly)) },
        )

        val stack = createActiveStack(AppLocale.VI)
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
                composeRule.onNodeWithTag(MyExamsTestTags.examCard("SOLO-1")).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(MyExamsTestTags.STANDALONE_SECTION_HEADER).assertDoesNotExist()
        composeRule.onNodeWithTag(MyExamsTestTags.CHECKING_SECTION).assertDoesNotExist()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.checkingCard("CHK-1")).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(MyExamsTestTags.SECTION_HEADER).assertIsDisplayed()
        composeRule.onNodeWithTag(MyExamsTestTags.CHECKING_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(MyExamsTestTags.STANDALONE_SECTION_HEADER).assertDoesNotExist()
        composeRule.onNodeWithTag(MyExamsTestTags.examCard("SOLO-1")).assertDoesNotExist()
    }

    private fun createActiveStack(
        locale: AppLocale,
        username: String = "student.demo",
    ) = Thi360ApiFactory.createSessionStack(
        baseUrl = server.url("/"),
        environment = Environment.DEV,
        tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "active-access",
                refreshToken = "active-refresh",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = username,
                accountId = "acc-demo",
            ),
        ),
        localeProvider = { locale },
    )
}
