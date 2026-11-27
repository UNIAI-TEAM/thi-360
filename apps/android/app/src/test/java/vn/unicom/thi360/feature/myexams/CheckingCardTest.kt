package vn.unicom.thi360.feature.myexams

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
import vn.unicom.thi360.core.network.generated.model.ExamKindRef
import vn.unicom.thi360.core.network.generated.model.MyExamCard
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.routes.AppRoute
import vn.unicom.thi360.shell.routes.LocalSupportContactOverride
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class CheckingCardTest {

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
    fun checkingCardWithOrderAndSupportOpensOrderAndReloadTransitionsToGrantedCard() {
        val checkingCard = MyExamsTestFixtures.makeCard(
            code = "CHK-01",
            name = "Đề HSA tổng hợp",
            entitlementId = null,
            state = MyExamCard.State.CHECKING,
            pendingOrderCode = "DH001",
            hasPendingFulfillment = true,
            actions = listOf(MyExamCard.Actions.CHECKING),
            examKind = ExamKindRef(code = "HSA", nameVi = "", nameEn = ""),
        )
        val grantedCard = MyExamsTestFixtures.makeCard(
            code = "CHK-01",
            name = "Đề HSA tổng hợp",
            state = MyExamCard.State.CAN_START,
            attemptsLeft = 2,
            actions = listOf(MyExamCard.Actions.START),
        )
        val reloadGate = CountDownLatch(1)
        val reloadArrived = CountDownLatch(1)
        val calls = AtomicInteger(0)

        server.respondDynamic("GET", "/api/v1/me/exams") {
            if (calls.incrementAndGet() == 1) {
                ContractFakeServer.jsonResponse(200, MyExamsTestFixtures.encodeResponse(listOf(checkingCard)))
            } else {
                reloadArrived.countDown()
                reloadGate.await(5, TimeUnit.SECONDS)
                ContractFakeServer.jsonResponse(200, MyExamsTestFixtures.encodeResponse(listOf(grantedCard)))
            }
        }

        val support = SupportContact(
            phone = "1900 1234",
            email = "hotro@thi360.vn",
            hours = "08:00-21:00",
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
            CompositionLocalProvider(LocalSupportContactOverride provides support) {
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
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.checkingCard("CHK-01")).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithText("HSA").assertIsDisplayed()
        composeRule.onNodeWithText("Đang kiểm tra").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Đã nhận thanh toán cho đơn DH001. Bài thi đang được cấp cho tài khoản của bạn, bạn không cần thanh toán lại. Tải lại màn hình sau ít phút; nếu vẫn chưa thấy bài, liên hệ hỗ trợ qua 1900 1234 · hotro@thi360.vn (08:00-21:00) và gửi kèm mã đơn.",
        ).assertIsDisplayed()

        composeRule.onNodeWithTag(MyExamsTestTags.checkingViewOrderButton("CHK-01")).performClick()
        composeRule.waitForIdle()
        assertEquals(
            "DH001",
            checkNotNull(capturedNavController).currentBackStackEntry?.arguments?.getString(AppRoute.ARG_ORDER_CODE),
        )
        composeRule.runOnIdle { checkNotNull(capturedNavController).popBackStack() }
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000) { reloadArrived.count == 0L }
        composeRule.onNodeWithTag(MyExamsTestTags.checkingReloadButton("CHK-01")).assertIsNotEnabled()
        composeRule.onNodeWithTag(MyExamsTestTags.checkingReloadButton("CHK-01")).performClick()
        assertEquals(2, server.requestCount("GET", "/api/v1/me/exams"))

        reloadGate.countDown()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(MyExamsTestTags.checkingCard("CHK-01")).assertDoesNotExist()
                composeRule.onNodeWithTag(MyExamsTestTags.examCard("CHK-01")).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun checkingCardWithoutOrderAndWithoutSupportShowsPendingAndHidesViewOrder() {
        val checkingCard = MyExamsTestFixtures.makeCard(
            code = "CHK-NOORDER",
            name = "Đề chờ cấp quyền",
            entitlementId = null,
            state = MyExamCard.State.CHECKING,
            pendingOrderCode = null,
            hasPendingFulfillment = true,
            actions = listOf(MyExamCard.Actions.CHECKING),
            examKind = ExamKindRef(code = "TSA", nameVi = "Tư duy Bách khoa", nameEn = ""),
        )

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                MyExamsScreen(
                    feedState = MyExamsFeedState(loadState = LoadState.Loaded(listOf(checkingCard))),
                    greetingName = "An",
                    supportContact = SupportContact(),
                    showCompactBrandHeader = false,
                    onPullToRefresh = {},
                    onRetry = {},
                    onExploreExams = {},
                    onOpenOrder = {},
                    onOpenProduct = {},
                    onContinueAttempt = {},
                    onStartPrepare = {},
                    onOpenResult = {},
                    onOpenHistoryReset = {},
                    onOpenCheckout = {},
                )
            }
        }

        composeRule.onNodeWithText("Tư duy Bách khoa").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Đã nhận thanh toán cho đơn hàng của bạn. Bài thi đang được cấp cho tài khoản, bạn không cần thanh toán lại. Tải lại màn hình sau ít phút; nếu vẫn chưa thấy bài, hãy liên hệ hỗ trợ. Thông tin liên hệ đang được cập nhật.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(MyExamsTestTags.checkingViewOrderButton("CHK-NOORDER"))
            .assertDoesNotExist()
    }

    @Test
    fun checkingMessageCoversAllFourVariantsAndHoursOnlyContact() {
        val withOrder = MyExamsTestFixtures.makeCard(
            code = "C1",
            state = MyExamCard.State.CHECKING,
            pendingOrderCode = "DH100",
        )
        val noOrder = withOrder.copy(pendingOrderCode = "   ")
        val hoursOnly = SupportContact(phone = "", email = "", hours = "08:00-17:00")
        val emptySupport = SupportContact()

        assertEquals(
            CheckingMessage.WithOrderAndContact("DH100", "08:00-17:00"),
            CheckingMessage.of(withOrder, hoursOnly),
        )
        assertEquals(
            CheckingMessage.WithOrderNoContact("DH100"),
            CheckingMessage.of(withOrder, emptySupport),
        )
        assertEquals(
            CheckingMessage.NoOrderWithContact("08:00-17:00"),
            CheckingMessage.of(noOrder, hoursOnly),
        )
        assertEquals(
            CheckingMessage.NoOrderNoContact,
            CheckingMessage.of(noOrder, emptySupport),
        )
    }
}
