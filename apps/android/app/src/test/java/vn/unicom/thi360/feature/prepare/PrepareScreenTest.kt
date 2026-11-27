package vn.unicom.thi360.feature.prepare

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
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
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.generated.model.AttemptPrepareResponse
import vn.unicom.thi360.core.network.generated.model.GrantSourceView
import vn.unicom.thi360.core.network.generated.model.OmittedGroupView
import vn.unicom.thi360.core.network.generated.model.PreparePartView
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.ui.LoadStateTestTags
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.RichContentTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.AppRoute
import vn.unicom.thi360.shell.routes.LocalPrepareSupportContactOverride
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class PrepareScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Thi360Application
    private lateinit var server: ContractFakeServer

    private val entitlementId = UUID.fromString("00000000-0000-7000-8000-000000000001")
    private val preparePath = "/api/v1/me/entitlements/$entitlementId/prepare"
    private val sampleSupport = SupportContact(
        phone = "1900 1234",
        email = "hotro@thi360.vn",
        hours = "08:00-21:00",
    )

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

    private fun createActiveStack(locale: AppLocale = AppLocale.VI) =
        Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens(
                    accessToken = "prep-access",
                    refreshToken = "prep-refresh",
                    accessExpiresIn = 3600,
                    refreshExpiresIn = 86400,
                    mustChangePassword = false,
                    accountId = "acc-prepare",
                    username = "hv01",
                ),
            ),
            localeProvider = { locale },
        )

    private fun mountPrepareRoute(
        locale: AppLocale = AppLocale.VI,
        supportContact: SupportContact = sampleSupport,
        entId: UUID = entitlementId,
    ): AppNavigator {
        val stack = createActiveStack(locale)
        AppSessionContainer.setTestStack(app, stack)
        lateinit var navigator: AppNavigator
        composeRule.setContent {
            val navController = rememberNavController()
            navigator = AppNavigator(navController)
            CompositionLocalProvider(
                LocalFormulaEngine provides SourceBoxFormulaEngine(),
                LocalPrepareSupportContactOverride provides supportContact,
            ) {
                AppRoot(
                    preferences = AppPreferences(locale = locale),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    navController = navController,
                    navigator = navigator,
                    sessionManager = stack.sessionManager,
                    validateOnLaunch = false,
                )
            }
        }
        composeRule.runOnIdle {
            navigator.switchTab(Destination.MyExams, reload = false)
            navigator.openPrepare(entId.toString())
        }
        return navigator
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    private fun scrollToTag(tag: String) {
        composeRule.onNodeWithTag(PrepareTestTags.LIST).performScrollToNode(hasTestTag(tag))
    }

    private fun assertSectionContainsText(sectionTag: String, expectedSubstring: String) {
        val matches = composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(sectionTag)) and hasText(expectedSubstring, substring = true),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue(
            "Expected section '$sectionTag' to contain descendant text '$expectedSubstring'",
            matches.isNotEmpty(),
        )
    }

    @Test
    fun loadingAndNotFound() {
        val requestStarted = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        server.respondDynamic("GET", preparePath) { _ ->
            requestStarted.countDown()
            releaseResponse.await(3, TimeUnit.SECONDS)
            ContractFakeServer.jsonResponse(404, PrepareTestFixtures.readPrepareRaw("not-found", 404))
        }

        val navigator = mountPrepareRoute()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            requestStarted.count == 0L
        }
        composeRule.onNodeWithTag(LoadStateTestTags.LOADING).assertIsDisplayed()
        composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).assertDoesNotExist()

        releaseResponse.countDown()
        waitForTag(PrepareTestTags.NOT_FOUND_STATE)

        composeRule.onNodeWithText("Không tìm thấy bài thi trong tài khoản đang đăng nhập").assertIsDisplayed()
        composeRule.onNodeWithText("Quyền làm bài không tồn tại hoặc thuộc về tài khoản khác.")
            .assertIsDisplayed()
        assertEquals(1, server.requestCount("GET", preparePath))

        composeRule.onNodeWithTag(PrepareTestTags.ERROR_BACK_BUTTON).performClick()
        composeRule.waitForIdle()
        assertEquals(
            AppRoute.rootRouteFor(Destination.MyExams),
            navigator.navController.currentDestination?.route,
        )
    }

    @Test
    fun loadErrorRetriesOnce() {
        val ent500 = UUID.fromString("00000000-0000-7000-8000-000000000500")
        val path500 = "/api/v1/me/entitlements/$ent500/prepare"
        val ent403 = UUID.fromString("00000000-0000-7000-8000-000000000403")
        val path403 = "/api/v1/me/entitlements/$ent403/prepare"
        val entNet = UUID.fromString("00000000-0000-7000-8000-000000000599")
        val pathNet = "/api/v1/me/entitlements/$entNet/prepare"

        server.respond("GET", path500) {
            ContractFakeServer.jsonResponse(500, """{"code":"INTERNAL_ERROR","message":"boom"}""")
        }
        server.respond("GET", path403) {
            ContractFakeServer.jsonResponse(403, PrepareTestFixtures.readPrepareRaw("attempt-access-forbidden", 403))
        }
        server.respond("GET", pathNet) {
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        }

        val navigator = mountPrepareRoute(entId = ent500)
        waitForTag(PrepareTestTags.ERROR_LOAD_STATE)

        composeRule.onNodeWithText("Không tải được thông tin chuẩn bị bài thi").assertIsDisplayed()
        composeRule.onNodeWithText("Vui lòng kiểm tra kết nối mạng và thử lại.").assertIsDisplayed()
        composeRule.onNodeWithTag(PrepareTestTags.ERROR_BACK_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(PrepareTestTags.ERROR_RETRY_BUTTON).assertIsDisplayed()
        assertEquals(2, server.requestCount("GET", path500))

        composeRule.onNodeWithTag(PrepareTestTags.ERROR_RETRY_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("GET", path500) == 3
        }
        assertEquals(3, server.requestCount("GET", path500))

        composeRule.runOnIdle { navigator.openPrepare(ent403.toString()) }
        waitForTag(PrepareTestTags.ERROR_LOAD_STATE)
        assertEquals(2, server.requestCount("GET", path403))

        composeRule.runOnIdle { navigator.openPrepare(entNet.toString()) }
        waitForTag(PrepareTestTags.ERROR_LOAD_STATE)
        assertEquals(2, server.requestCount("GET", pathNet))
    }

    @Test
    fun header() {
        val reviewSubject = PrepareTestFixtures.prepareWithListeningAndReview().copy(
            durationMinutes = null,
            parts = listOf(
                PreparePartView(
                    partCode = "P1",
                    ord = 0,
                    name = "Phần I",
                    required = true,
                    durationMinutes = null,
                    questionCount = 12,
                    maxPoints = BigDecimal("10"),
                ),
            ),
            preChoice = null,
        )
        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(reviewSubject))

        mountPrepareRoute(locale = AppLocale.VI)
        waitForTag(PrepareTestTags.HEADER_TITLE)

        composeRule.onNodeWithTag(PrepareTestTags.HEADER_KIND_CHIP)
            .assertTextContains("Đánh giá năng lực ĐH Sư phạm Hà Nội (SPT)")
        composeRule.onNodeWithTag(PrepareTestTags.HEADER_SCOPE_CHIP)
            .assertTextContains("Bài thi theo môn · Tin học")
        composeRule.onNodeWithTag(PrepareTestTags.HEADER_REVIEW_CHIP)
            .assertTextContains("Bài ôn")
        composeRule.onNodeWithTag(PrepareTestTags.HEADER_TITLE)
            .assertTextContains(reviewSubject.examName)
        composeRule.onNodeWithTag(PrepareTestTags.HEADER_SUMMARY)
            .assertTextContains("1 phần · 12 câu · 10 điểm")
    }

    @Test
    fun structureBlock() {
        val freeWithExtras = PrepareTestFixtures.loadPrepare("free-navigation").copy(
            timing = AttemptPrepareResponse.Timing.PER_PART,
            navigationMode = AttemptPrepareResponse.NavigationMode.FREE,
            parts = listOf(
                PreparePartView(
                    partCode = "P2",
                    ord = 1,
                    name = "Phần sau",
                    required = false,
                    durationMinutes = 20,
                    questionCount = 8,
                    maxPoints = BigDecimal("4"),
                ),
                PreparePartView(
                    partCode = "P1",
                    ord = 0,
                    name = "Phần đầu",
                    required = true,
                    durationMinutes = 30,
                    questionCount = 12,
                    maxPoints = BigDecimal("6"),
                ),
            ),
            answerFormats = listOf("SINGLE_CHOICE", "UNKNOWN_CODE_99"),
            listeningPolicy = vn.unicom.thi360.core.network.generated.model.ListeningPolicyView(
                maxPlays = null,
                seekable = false,
            ),
            omittedGroups = listOf(
                OmittedGroupView(name = "Tự luận mở rộng", points = BigDecimal("2.5")),
            ),
        )
        val entPerPart = UUID.fromString("00000000-0000-7000-8000-000000000011")
        val perPartResponse = PrepareTestFixtures.loadPrepare("per-part").copy(entitlementId = entPerPart)
        val entShared = UUID.fromString("00000000-0000-7000-8000-000000000010")
        val sharedEmptyExtras = PrepareTestFixtures.loadPrepare("shared").copy(
            entitlementId = entShared,
            answerFormats = emptyList(),
            listeningPolicy = null,
            omittedGroups = emptyList(),
        )
        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(freeWithExtras))
        server.respondJson(
            "GET",
            "/api/v1/me/entitlements/$entPerPart/prepare",
            200,
            PrepareTestFixtures.encodePrepare(perPartResponse),
        )
        server.respondJson(
            "GET",
            "/api/v1/me/entitlements/$entShared/prepare",
            200,
            PrepareTestFixtures.encodePrepare(sharedEmptyExtras),
        )

        val navigator = mountPrepareRoute()
        waitForTag(PrepareTestTags.HEADER_TITLE)

        scrollToTag(PrepareTestTags.TIMING_BANNER)
        composeRule.onNodeWithTag(PrepareTestTags.TIMING_BANNER)
            .assertTextContains("Một đồng hồ chung, chuyển phần tự do", substring = true)

        scrollToTag(PrepareTestTags.partRow(0))
        composeRule.onNodeWithTag(PrepareTestTags.partRow(0))
            .assertTextContains("Phần đầu", substring = true)
        assertSectionContainsText(PrepareTestTags.partRow(0), "Bắt buộc")
        composeRule.onNodeWithTag(PrepareTestTags.partRow(1))
            .assertTextContains("Phần sau", substring = true)
        assertSectionContainsText(PrepareTestTags.partRow(1), "Tự chọn")

        scrollToTag(PrepareTestTags.ANSWER_FORMATS_SECTION)
        assertSectionContainsText(PrepareTestTags.ANSWER_FORMATS_SECTION, "Trắc nghiệm một đáp án")
        assertSectionContainsText(PrepareTestTags.ANSWER_FORMATS_SECTION, "UNKNOWN_CODE_99")

        scrollToTag(PrepareTestTags.LISTENING_POLICY_BANNER)
        composeRule.onNodeWithTag(PrepareTestTags.LISTENING_POLICY_BANNER)
            .assertTextContains("Mỗi tệp âm thanh được nghe tối đa 2 lần.", substring = true)
            .assertTextContains("Không có thanh tua, không tạm dừng và không đổi tốc độ phát.", substring = true)

        scrollToTag(PrepareTestTags.OMITTED_GROUPS_SECTION)
        composeRule.onNodeWithTag(PrepareTestTags.OMITTED_GROUPS_SECTION)
            .assertTextContains("Tự luận mở rộng", substring = true)
            .assertTextContains("2,5 điểm", substring = true)

        composeRule.runOnIdle { navigator.openPrepare(entPerPart.toString()) }
        waitForTag(PrepareTestTags.HEADER_TITLE)
        scrollToTag(PrepareTestTags.TIMING_BANNER)
        composeRule.onNodeWithTag(PrepareTestTags.TIMING_BANNER)
            .assertTextContains("Tính giờ từng phần", substring = true)

        composeRule.runOnIdle { navigator.openPrepare(entShared.toString()) }
        waitForTag(PrepareTestTags.HEADER_TITLE)
        scrollToTag(PrepareTestTags.TIMING_BANNER)
        composeRule.onNodeWithTag(PrepareTestTags.TIMING_BANNER)
            .assertTextContains("Một đồng hồ chung cho cả bài", substring = true)
        composeRule.onNodeWithTag(PrepareTestTags.ANSWER_FORMATS_SECTION).assertDoesNotExist()
        composeRule.onNodeWithTag(PrepareTestTags.LISTENING_POLICY_BANNER).assertDoesNotExist()
        composeRule.onNodeWithTag(PrepareTestTags.OMITTED_GROUPS_SECTION).assertDoesNotExist()
    }

    @Test
    fun afterStartChoices() {
        val perPart = PrepareTestFixtures.loadPrepare("per-part")
        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(perPart))

        mountPrepareRoute()
        waitForTag(PrepareTestTags.HEADER_TITLE)

        scrollToTag(PrepareTestTags.AFTER_START_CHOICES_SECTION)
        composeRule.onNodeWithTag(PrepareTestTags.AFTER_START_CHOICES_SECTION).assertIsDisplayed()
        assertSectionContainsText(PrepareTestTags.AFTER_START_CHOICES_SECTION, "Khoa học hoặc Tiếng Anh")
        assertSectionContainsText(PrepareTestTags.AFTER_START_CHOICES_SECTION, "SCIENCE")
        assertSectionContainsText(PrepareTestTags.AFTER_START_CHOICES_SECTION, "chọn đúng 3 trong 5 môn")
        assertSectionContainsText(PrepareTestTags.AFTER_START_CHOICES_SECTION, "Vật lí, Hóa học, Sinh học, Lịch sử, Địa lí")
        assertSectionContainsText(PrepareTestTags.AFTER_START_CHOICES_SECTION, "tối đa 15 phút")
        assertSectionContainsText(
            PrepareTestTags.AFTER_START_CHOICES_SECTION,
            "Đồng hồ của phần tự chọn chỉ chạy khi bộ câu hỏi đã sẵn sàng.",
        )
    }

    @Test
    fun instructions() {
        val withHtml = PrepareTestFixtures.prepareWithListeningAndReview()
        val entNoInstr = UUID.fromString("00000000-0000-7000-8000-000000000012")
        val withoutHtml = PrepareTestFixtures.loadPrepare("per-part").copy(
            entitlementId = entNoInstr,
            instructions = "   ",
        )
        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(withHtml))
        server.respondJson(
            "GET",
            "/api/v1/me/entitlements/$entNoInstr/prepare",
            200,
            PrepareTestFixtures.encodePrepare(withoutHtml),
        )

        val navigator = mountPrepareRoute()
        waitForTag(PrepareTestTags.HEADER_TITLE)

        scrollToTag(PrepareTestTags.INSTRUCTIONS_SECTION)
        composeRule.onNodeWithTag(PrepareTestTags.INSTRUCTIONS_RICH_CONTENT).assertIsDisplayed()
        composeRule.onNodeWithTag(RichContentTestTags.IMAGE_FIGURE).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("đề bài", substring = true).assertIsDisplayed()
        assertSectionContainsText(PrepareTestTags.INSTRUCTIONS_SECTION, "Câu trả lời được lưu tự động.")
        assertSectionContainsText(PrepareTestTags.INSTRUCTIONS_SECTION, "Thời gian còn lại lấy theo giờ máy chủ")
        assertSectionContainsText(PrepareTestTags.INSTRUCTIONS_SECTION, "Mất kết nối vẫn làm bài tiếp được.")
        assertSectionContainsText(
            PrepareTestTags.INSTRUCTIONS_SECTION,
            "Mỗi lượt chỉ làm trên một thiết bị tại một thời điểm.",
        )

        composeRule.runOnIdle { navigator.openPrepare(entNoInstr.toString()) }
        waitForTag(PrepareTestTags.HEADER_TITLE)
        scrollToTag(PrepareTestTags.INSTRUCTIONS_SECTION)
        composeRule.onNodeWithTag(PrepareTestTags.INSTRUCTIONS_RICH_CONTENT).assertDoesNotExist()
        assertSectionContainsText(PrepareTestTags.INSTRUCTIONS_SECTION, "Câu trả lời được lưu tự động.")
    }

    @Test
    fun readyColumn() {
        val base = PrepareTestFixtures.loadPrepare("per-part")
        val grantedAt = OffsetDateTime.parse("2026-11-20T10:00:00Z")
        val variants = listOf(
            UUID.fromString("00000000-0000-7000-8000-000000000701") to Pair(
                base.copy(
                    attemptsLeft = 2,
                    durationMinutes = null,
                    parts = listOf(PreparePartView(partCode = "P1", ord = 0, name = "A")),
                    grantSource = GrantSourceView(source = GrantSourceView.Source.COMPENSATION),
                ),
                "Cấp bù",
            ),
            UUID.fromString("00000000-0000-7000-8000-000000000702") to Pair(
                base.copy(
                    attemptsLeft = 2,
                    grantSource = GrantSourceView(source = GrantSourceView.Source.CLASS, grantedAt = grantedAt),
                ),
                "Giao theo lớp ngày 20/11",
            ),
            UUID.fromString("00000000-0000-7000-8000-000000000703") to Pair(
                base.copy(
                    attemptsLeft = 2,
                    grantSource = GrantSourceView(source = GrantSourceView.Source.CLASS, grantedAt = null),
                ),
                "Giao theo lớp",
            ),
            UUID.fromString("00000000-0000-7000-8000-000000000704") to Pair(
                base.copy(
                    attemptsLeft = 2,
                    grantSource = GrantSourceView(
                        source = GrantSourceView.Source.PURCHASE,
                        orderCode = "DH261126000001",
                        grantedAt = grantedAt,
                    ),
                ),
                "Mua ngày 20/11, đơn DH261126000001",
            ),
            UUID.fromString("00000000-0000-7000-8000-000000000705") to Pair(
                base.copy(
                    attemptsLeft = 2,
                    grantSource = GrantSourceView(
                        source = GrantSourceView.Source.PURCHASE,
                        orderCode = null,
                        grantedAt = grantedAt,
                    ),
                ),
                "Mua ngày 20/11",
            ),
        )

        variants.forEach { (id, pair) ->
            val resp = pair.first.copy(entitlementId = id)
            server.respondJson("GET", "/api/v1/me/entitlements/$id/prepare", 200, PrepareTestFixtures.encodePrepare(resp))
        }

        val navigator = mountPrepareRoute(entId = variants.first().first)
        waitForTag(PrepareTestTags.READY_ATTEMPTS_LEFT)
        composeRule.onNodeWithTag(PrepareTestTags.READY_ATTEMPTS_LEFT).assertTextContains("2 lượt")
        composeRule.onNodeWithTag(PrepareTestTags.READY_QUESTIONS).assertTextContains("—")
        composeRule.onNodeWithTag(PrepareTestTags.READY_DURATION).assertTextContains("—")
        composeRule.onNodeWithTag(PrepareTestTags.READY_MAX_POINTS).assertTextContains("—")
        composeRule.onNodeWithTag(PrepareTestTags.READY_GRANT_SOURCE)
            .assertTextContains(variants.first().second.second)

        variants.drop(1).forEach { (id, pair) ->
            composeRule.runOnIdle { navigator.openPrepare(id.toString()) }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(PrepareTestTags.READY_GRANT_SOURCE)
                        .assertTextContains(pair.second)
                    true
                }.getOrDefault(false)
            }
        }
    }

    @Test
    fun subjectPicking() {
        val response = PrepareTestFixtures.prepareWithSubjectChoice(entitlementId = entitlementId)
        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(response))
        server.respondJson(
            "POST",
            "/api/v1/me/attempts",
            422,
            PrepareTestFixtures.readStartRaw(422, "choice-not-allowed"),
        )

        mountPrepareRoute()
        waitForTag(PrepareTestTags.preChoiceOption("SCIENCE"))

        composeRule.onNodeWithTag(PrepareTestTags.preChoiceOption("SCIENCE")).performClick()
        scrollToTag(PrepareTestTags.SUBJECT_PROGRESS)
        composeRule.onNodeWithTag(PrepareTestTags.SUBJECT_PROGRESS).assertTextContains("Đã chọn 0/3")

        composeRule.onNodeWithTag(PrepareTestTags.subjectCheckbox("LY")).performClick()
        composeRule.onNodeWithTag(PrepareTestTags.subjectCheckbox("HOA")).performClick()
        composeRule.onNodeWithTag(PrepareTestTags.subjectCheckbox("SINH")).performClick()
        composeRule.onNodeWithTag(PrepareTestTags.SUBJECT_PROGRESS).assertTextContains("Đã chọn 3/3")
        composeRule.onNodeWithTag(PrepareTestTags.subjectCheckbox("SU")).assertIsNotEnabled()
        composeRule.onNodeWithTag(PrepareTestTags.subjectCheckbox("DIA")).assertIsNotEnabled()

        composeRule.onNodeWithTag(PrepareTestTags.subjectCheckbox("HOA")).performClick()
        composeRule.onNodeWithTag(PrepareTestTags.SUBJECT_PROGRESS).assertTextContains("Đã chọn 2/3")
        composeRule.onNodeWithTag(PrepareTestTags.subjectCheckbox("SU")).assertIsEnabled()

        scrollToTag(PrepareTestTags.preChoiceOption("ANH"))
        composeRule.onNodeWithTag(PrepareTestTags.preChoiceOption("ANH")).performClick()
        scrollToTag(PrepareTestTags.START_BUTTON)
        composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).performClick()
        waitForTag(PrepareTestTags.START_ERROR_BANNER)

        scrollToTag(PrepareTestTags.preChoiceOption("SCIENCE"))
        composeRule.onNodeWithTag(PrepareTestTags.preChoiceOption("SCIENCE")).performClick()
        scrollToTag(PrepareTestTags.SUBJECT_PROGRESS)
        composeRule.onNodeWithTag(PrepareTestTags.SUBJECT_PROGRESS).assertTextContains("Đã chọn 0/3")
        composeRule.onNodeWithTag(PrepareTestTags.START_ERROR_BANNER).assertDoesNotExist()
    }

    @Test
    fun startBlockedHints() {
        val multi = PrepareTestFixtures.prepareWithSubjectChoice(entitlementId = entitlementId)
        val entZero = UUID.fromString("00000000-0000-7000-8000-000000000090")
        val zeroOptions = PrepareTestFixtures.prepareWithZeroPreChoiceOptions(entitlementId = entZero)

        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(multi))
        server.respondJson(
            "GET",
            "/api/v1/me/entitlements/$entZero/prepare",
            200,
            PrepareTestFixtures.encodePrepare(zeroOptions),
        )

        val navigator = mountPrepareRoute()
        waitForTag(PrepareTestTags.PRE_CHOICE_SECTION)

        scrollToTag(PrepareTestTags.START_BUTTON)
        composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(PrepareTestTags.START_HINT)
            .assertTextContains("Vui lòng chọn một phương án trước khi bắt đầu.")

        scrollToTag(PrepareTestTags.preChoiceOption("SCIENCE"))
        composeRule.onNodeWithTag(PrepareTestTags.preChoiceOption("SCIENCE")).performClick()
        scrollToTag(PrepareTestTags.START_HINT)
        composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(PrepareTestTags.START_HINT)
            .assertTextContains("Vui lòng chọn đủ 3 môn trước khi bắt đầu.")

        composeRule.runOnIdle { navigator.openPrepare(entZero.toString()) }
        waitForTag(PrepareTestTags.NO_SERVING_OPTION_ALERT)
        composeRule.onNodeWithTag(PrepareTestTags.NO_SERVING_OPTION_ALERT)
            .assertTextContains("Các mã bạn đã mua hiện không còn được phục vụ.", substring = true)
            .assertTextContains("1900 1234", substring = true)
        scrollToTag(PrepareTestTags.START_BUTTON)
        composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).assertIsNotEnabled()
        assertEquals(0, server.requestCount("POST", "/api/v1/me/attempts"))
    }

    @Test
    fun startBlockedNoServingOptionWithoutSupportContact() {
        val zeroOptions = PrepareTestFixtures.prepareWithZeroPreChoiceOptions(entitlementId = entitlementId)
        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(zeroOptions))

        mountPrepareRoute(supportContact = SupportContact())
        waitForTag(PrepareTestTags.NO_SERVING_OPTION_ALERT)
        composeRule.onNodeWithTag(PrepareTestTags.NO_SERVING_OPTION_ALERT)
            .assertTextContains("Thông tin liên hệ hỗ trợ đang được cập nhật.", substring = true)
        scrollToTag(PrepareTestTags.START_BUTTON)
        composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).assertIsNotEnabled()
        assertEquals(0, server.requestCount("POST", "/api/v1/me/attempts"))
    }

    @Test
    fun openAttemptStates() {
        val openInProgress = PrepareTestFixtures.loadPrepare("open-in-progress").copy(
            preChoice = PrepareTestFixtures.loadPrepare("pre-choice").preChoice,
        )
        val inProgressAttemptId = openInProgress.openAttempt!!.attemptId!!
        val entStarting = UUID.fromString("00000000-0000-7000-8000-000000000088")
        val openStarting = PrepareTestFixtures.loadPrepare("open-starting").copy(entitlementId = entStarting)
        val startingAttemptId = openStarting.openAttempt!!.attemptId!!

        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(openInProgress))
        server.respondJson(
            "GET",
            "/api/v1/me/entitlements/$entStarting/prepare",
            200,
            PrepareTestFixtures.encodePrepare(openStarting),
        )
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/$startingAttemptId",
            200,
            PrepareTestFixtures.readAttemptStateRaw(200, "starting"),
        )

        val navigator = mountPrepareRoute()
        waitForTag(PrepareTestTags.IN_PROGRESS_BANNER)

        composeRule.onNodeWithTag(PrepareTestTags.PRE_CHOICE_SECTION).assertDoesNotExist()
        composeRule.onNodeWithTag(PrepareTestTags.CONTINUE_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navigator.navController.currentDestination?.route == AppRoute.ATTEMPT_PATTERN
        }
        assertEquals(
            inProgressAttemptId.toString(),
            navigator.navController.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_ATTEMPT_ID),
        )
        assertEquals(0, server.requestCount("POST", "/api/v1/me/attempts"))

        composeRule.runOnIdle {
            navigator.closeAttempt()
            navigator.openPrepare(entStarting.toString())
        }
        waitForTag(PrepareTestTags.WAITING_SCREEN)
        assertEquals(0, server.requestCount("POST", "/api/v1/me/attempts"))
    }

    @Test
    fun exhausted() {
        val entClass = UUID.fromString("00000000-0000-7000-8000-000000000801")
        val entDiscontinued = UUID.fromString("00000000-0000-7000-8000-000000000802")
        val entPurchase = UUID.fromString("00000000-0000-7000-8000-000000000803")

        val classExhausted = PrepareTestFixtures.prepareExhaustedVariant(
            source = GrantSourceView.Source.CLASS,
            servingStatus = PREPARE_SERVING_PUBLISHED,
        ).copy(entitlementId = entClass)
        val discontinuedExhausted = PrepareTestFixtures.prepareExhaustedVariant(
            source = GrantSourceView.Source.PURCHASE,
            servingStatus = "DISCONTINUED",
        ).copy(entitlementId = entDiscontinued)
        val purchaseExhausted = PrepareTestFixtures.loadPrepare("exhausted-purchase").copy(entitlementId = entPurchase)

        server.respondJson("GET", "/api/v1/me/entitlements/$entClass/prepare", 200, PrepareTestFixtures.encodePrepare(classExhausted))
        server.respondJson("GET", "/api/v1/me/entitlements/$entDiscontinued/prepare", 200, PrepareTestFixtures.encodePrepare(discontinuedExhausted))
        server.respondJson("GET", "/api/v1/me/entitlements/$entPurchase/prepare", 200, PrepareTestFixtures.encodePrepare(purchaseExhausted))

        val navigator = mountPrepareRoute(entId = entClass)
        waitForTag(PrepareTestTags.EXHAUSTED_BANNER)
        composeRule.onNodeWithTag(PrepareTestTags.READY_GRANT_SOURCE).assertDoesNotExist()
        composeRule.onNodeWithTag(PrepareTestTags.EXHAUSTED_BANNER)
            .assertTextContains("Bạn đã dùng hết lượt của bài này.", substring = true)
            .assertTextContains("Bạn đã dùng hết số lượt được giao cho bài thi này.", substring = true)
        composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(PrepareTestTags.BUY_MORE_BUTTON).assertDoesNotExist()

        composeRule.runOnIdle { navigator.openPrepare(entDiscontinued.toString()) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.EXHAUSTED_BANNER)
                    .assertTextContains("Bài thi này đã ngừng mở bán nên không thể mua thêm lượt.", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(PrepareTestTags.BUY_MORE_BUTTON).assertDoesNotExist()

        composeRule.runOnIdle { navigator.openPrepare(entPurchase.toString()) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(PrepareTestTags.BUY_MORE_BUTTON).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(PrepareTestTags.EXHAUSTED_BANNER)
            .assertTextContains("Bạn có thể mua thêm lượt để tiếp tục luyện tập bài thi này.", substring = true)
        composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(PrepareTestTags.BUY_MORE_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navigator.navController.currentDestination?.route?.contains("checkout/") == true
        }
        assertEquals(
            purchaseExhausted.examCode,
            navigator.navController.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_CODE),
        )
    }

    @Test
    fun startRefused() {
        val response = PrepareTestFixtures.loadPrepare("pre-choice")
        server.respondJson("GET", preparePath, 200, PrepareTestFixtures.encodePrepare(response))
        server.respondSequence(
            "POST",
            "/api/v1/me/attempts",
            { ContractFakeServer.jsonResponse(409, PrepareTestFixtures.readStartRaw(409, "no-attempts-left")) },
            { ContractFakeServer.jsonResponse(422, PrepareTestFixtures.readStartRaw(422, "choice-not-allowed")) },
            { ContractFakeServer.jsonResponse(409, PrepareTestFixtures.readStartRaw(409, "no-serving-option")) },
            { ContractFakeServer.jsonResponse(422, PrepareTestFixtures.readStartRaw(422, "subjects-invalid")) },
            { ContractFakeServer.jsonResponse(429, PrepareTestFixtures.readRateLimitedRaw()) },
            { ContractFakeServer.jsonResponse(500, """{"code":"INTERNAL_ERROR","message":"fail"}""") },
        )

        mountPrepareRoute()
        waitForTag(PrepareTestTags.preChoiceOption("CS"))

        val expectedErrors = listOf(
            "Bạn đã dùng hết lượt của bài thi này.",
            "Lựa chọn hiện tại không hợp lệ hoặc không thuộc phạm vi bạn đã mua.",
            "1900 1234",
            "Danh sách môn đã chọn chưa đúng số lượng hoặc không hợp lệ.",
            "Bạn thao tác quá nhanh. Vui lòng chờ một lát rồi thử lại.",
            "Không thể bắt đầu bài thi lúc này. Vui lòng thử lại.",
        )

        expectedErrors.forEach { expectedSnippet ->
            scrollToTag(PrepareTestTags.START_BUTTON)
            composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).assertIsEnabled().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(PrepareTestTags.START_ERROR_BANNER)
                        .assertTextContains(expectedSnippet, substring = true)
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag(PrepareTestTags.START_BUTTON).assertIsEnabled()
            scrollToTag(PrepareTestTags.preChoiceOption("CS"))
            composeRule.onNodeWithTag(PrepareTestTags.preChoiceOption("CS")).assertIsSelected()
        }
    }
}
