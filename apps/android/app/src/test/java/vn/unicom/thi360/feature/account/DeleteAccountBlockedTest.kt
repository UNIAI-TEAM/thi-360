package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class DeleteAccountBlockedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun mountBlocked(
        fixture: String,
        onOpenMyExams: () -> Unit = {},
        onOpenOrders: () -> Unit = {},
    ) {
        server.respondApiFixture("GET", "/api/v1/me/account/deletion-check", 200, fixture)
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val profileGateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val deps = ProfileDependencies(
            profileStore = AccountProfileStore(profileGateway),
            profileGateway = profileGateway,
            accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
            sessionManager = stack.sessionManager,
            imageLoader = SingletonImageLoader.get(ApplicationProvider.getApplicationContext()),
            apiBaseUrl = server.url("/"),
        )
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                DeleteAccountScreen(
                    onOpenMyExams = onOpenMyExams,
                    onOpenOrders = onOpenOrders,
                    dependencies = deps,
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_CARD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(DeleteAccountTestTags.PASSWORD_FIELD).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.SUBMIT_BUTTON).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun blockedAttemptShowsAttemptMessageAndNavigatesToMyExams() {
        var myExamsClicks = 0
        mountBlocked("me-account-deletion-check-200-blocked-attempt.json", onOpenMyExams = { myExamsClicks++ })
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ATTEMPT_MESSAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ORDER_MESSAGE).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_PENDING_PAYMENT_MESSAGE).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_EXAMS_BUTTON).performScrollTo().performClick()
        assertEquals(1, myExamsClicks)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun blockedOrderShowsOrderMessageAndNavigatesToOrders() {
        var ordersClicks = 0
        mountBlocked("me-account-deletion-check-200-blocked-order.json", onOpenOrders = { ordersClicks++ })
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ORDER_MESSAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_ORDERS_BUTTON).performScrollTo().performClick()
        assertEquals(1, ordersClicks)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun blockedPendingPaymentShowsPendingPaymentMessageAndNavigatesToOrders() {
        var ordersClicks = 0
        mountBlocked("me-account-deletion-check-200-blocked-pending-payment.json", onOpenOrders = { ordersClicks++ })
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_PENDING_PAYMENT_MESSAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_ORDERS_BUTTON).performScrollTo().performClick()
        assertEquals(1, ordersClicks)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun blockedAllThreeRendersEveryReasonAndBothActions() {
        var myExamsClicks = 0
        var ordersClicks = 0
        mountBlocked(
            "me-account-deletion-check-200-blocked.json",
            onOpenMyExams = { myExamsClicks++ },
            onOpenOrders = { ordersClicks++ },
        )
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ATTEMPT_MESSAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ORDER_MESSAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_PENDING_PAYMENT_MESSAGE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_EXAMS_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(DeleteAccountTestTags.OPEN_MY_ORDERS_BUTTON).performScrollTo().performClick()
        assertEquals(1, myExamsClicks)
        assertEquals(1, ordersClicks)
    }
}
