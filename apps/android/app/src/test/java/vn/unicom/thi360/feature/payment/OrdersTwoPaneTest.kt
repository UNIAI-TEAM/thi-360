package vn.unicom.thi360.feature.payment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.ListDetailTestTags
import vn.unicom.thi360.core.ui.LocalShellWidth
import vn.unicom.thi360.core.ui.ShellWidth
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.OrdersScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.OrdersViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class OrdersTwoPaneTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun twoOrderFixtures(): Triple<String, String, String> {
        val listBody = PaymentTestFixtures.mutateJson("orders-list-200.json") { obj ->
            val first = obj["items"]!!.jsonArray.first().jsonObject
            val second = JsonObject(
                first + mapOf(
                    "id" to JsonPrimitive("00000000-0000-7000-8000-000000000002"),
                    "code" to JsonPrimitive("DH261126000002"),
                    "productName" to JsonPrimitive("Đề thi thứ hai"),
                    "state" to JsonPrimitive("FULFILLED"),
                ),
            )
            JsonObject(
                obj + mapOf(
                    "totalItems" to JsonPrimitive(2),
                    "items" to JsonArray(listOf(first, second)),
                ),
            )
        }
        val detail1 = PaymentTestFixtures.readFixture("orders-detail-200-pending-payment.json")
        val detail2 = PaymentTestFixtures.mutateJson("orders-detail-200-fulfilled.json") { obj ->
            JsonObject(
                obj + mapOf(
                    "code" to JsonPrimitive("DH261126000002"),
                    "productName" to JsonPrimitive("Đề thi thứ hai"),
                ),
            )
        }
        return Triple(listBody, detail1, detail2)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun phoneUsesSingleColumnAndOpensSeparateDetailRoute() {
        val (listBody, detail1, _) = twoOrderFixtures()
        var openedSeparateOrder: String? = null
        val harness = PaymentTestFixtures.createHarness {
            respondJson("GET", "/api/v1/me/orders", 200, listBody)
            respondJson("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, detail1)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = OrdersViewModel(
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            usernameProvider = { "student.demo" },
        )
        try {
            vm.loadInitial()
            composeRule.setContent {
                CompositionLocalProvider(LocalShellWidth provides ShellWidth.Narrow) {
                    Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                        OrdersScreen(
                            viewModel = vm,
                            paymentBrowser = RecordingPaymentBrowser(),
                            supportContact = SupportContact(),
                            onBack = {},
                            onOpenOrder = { openedSeparateOrder = it },
                            onOpenMyExams = {},
                            onOpenCheckout = {},
                            onOpenCatalog = {},
                            onSwitchAccount = {},
                        )
                    }
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.orderRow(PaymentTestFixtures.ORDER_CODE)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(ListDetailTestTags.SINGLE_COLUMN).assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.orderRow(PaymentTestFixtures.ORDER_CODE)).performClick()
            assertEquals(PaymentTestFixtures.ORDER_CODE, openedSeparateOrder)
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun tablet840FullShowsTwoPaneSwitchesDetailAndSyncsCancelToList() {
        val (listBody, detail1, detail2) = twoOrderFixtures()
        val cancelledDetail1 = PaymentTestFixtures.readFixture("orders-cancel-200.json")
        val harness = PaymentTestFixtures.createHarness {
            respondJson("GET", "/api/v1/me/orders", 200, listBody)
            respondJson("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, detail1)
            respondJson("GET", "/api/v1/me/orders/DH261126000002", 200, detail2)
            respondJson("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/cancel", 200, cancelledDetail1)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = OrdersViewModel(
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            usernameProvider = { "student.demo" },
        )
        try {
            vm.loadInitial()
            composeRule.setContent {
                CompositionLocalProvider(LocalShellWidth provides ShellWidth.TwoColumn) {
                    Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                        OrdersScreen(
                            viewModel = vm,
                            paymentBrowser = RecordingPaymentBrowser(),
                            supportContact = SupportContact(),
                            onBack = null,
                            onOpenOrder = {},
                            onOpenMyExams = {},
                            onOpenCheckout = {},
                            onOpenCatalog = {},
                            onSwitchAccount = {},
                        )
                    }
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_HEADING).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(ListDetailTestTags.TWO_COLUMN).assertIsDisplayed()
            composeRule.onNodeWithText("Đơn ${PaymentTestFixtures.ORDER_CODE}").assertIsDisplayed()

            // Switch selection to 2nd order -> right pane changes while list remains visible
            composeRule.onNodeWithTag(PaymentTestTags.orderRow("DH261126000002")).performClick()
            composeRule.waitUntilMainIdle {
                vm.state.value.selectedOrderCode == "DH261126000002" &&
                    (vm.state.value.selectedDetail as? vn.unicom.thi360.core.ui.LoadState.Loaded)?.value?.code == "DH261126000002"
            }
            composeRule.onNodeWithText("Đơn DH261126000002").assertIsDisplayed()

            // Switch back to 1st order and cancel from right pane -> updates left list row too!
            composeRule.onNodeWithTag(PaymentTestTags.orderRow(PaymentTestFixtures.ORDER_CODE)).performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_CANCEL_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            // Replace list endpoint so subsequent reload returns CANCELLED too
            harness.server.respondJson(
                "GET",
                "/api/v1/me/orders",
                200,
                PaymentTestFixtures.mutateJson("orders-list-200.json") { obj ->
                    val first = obj["items"]!!.jsonArray.first().jsonObject
                    JsonObject(obj + ("items" to JsonArray(listOf(JsonObject(first + ("state" to JsonPrimitive("CANCELLED")))))))
                },
            )
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_CANCEL_BUTTON).performClick()
            composeRule.onNodeWithTag(PaymentTestTags.CANCEL_DIALOG_CONFIRM).performClick()
            composeRule.waitUntilMainIdle {
                val items = (vm.state.value.orders as? vn.unicom.thi360.core.ui.LoadState.Loaded)?.value.orEmpty()
                items.firstOrNull { it.code == PaymentTestFixtures.ORDER_CODE }?.state?.value == "CANCELLED"
            }
            val syncedRow = (vm.state.value.orders as vn.unicom.thi360.core.ui.LoadState.Loaded).value
                .first { it.code == PaymentTestFixtures.ORDER_CODE }
            assertEquals("CANCELLED", syncedRow.state.value)
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun ordersInsideAccountDetailPaneAt840dpCollapsesCleanlyWithoutOverflow() {
        val (listBody, detail1, _) = twoOrderFixtures()
        var openedDetail: String? = null
        val harness = PaymentTestFixtures.createHarness {
            respondJson("GET", "/api/v1/me/orders", 200, listBody)
            respondJson("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, detail1)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = OrdersViewModel(
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            usernameProvider = { "student.demo" },
        )
        try {
            vm.loadInitial()
            // Simulate Account detail pane width on 840x720 (~367dp) even though LocalShellWidth is TwoColumn
            composeRule.setContent {
                CompositionLocalProvider(LocalShellWidth provides ShellWidth.TwoColumn) {
                    Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                        Box(modifier = Modifier.width(367.dp).fillMaxHeight()) {
                            OrdersScreen(
                                viewModel = vm,
                                paymentBrowser = RecordingPaymentBrowser(),
                                supportContact = SupportContact(),
                                onBack = null,
                                onOpenOrder = { openedDetail = it },
                                onOpenMyExams = {},
                                onOpenCheckout = {},
                                onOpenCatalog = {},
                                onSwitchAccount = {},
                            )
                        }
                    }
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.orderRow(PaymentTestFixtures.ORDER_CODE)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(ListDetailTestTags.SINGLE_COLUMN).assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.orderRow(PaymentTestFixtures.ORDER_CODE)).performClick()
            assertEquals(PaymentTestFixtures.ORDER_CODE, openedDetail)
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun ordersInsideAccountDetailPaneAt1280dpShowsTwoColumnCleanly() {
        val (listBody, detail1, _) = twoOrderFixtures()
        val harness = PaymentTestFixtures.createHarness {
            respondJson("GET", "/api/v1/me/orders", 200, listBody)
            respondJson("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, detail1)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = OrdersViewModel(
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            usernameProvider = { "student.demo" },
        )
        try {
            vm.loadInitial()
            // Simulate Account detail pane width on 1280x800 (~807dp)
            composeRule.setContent {
                CompositionLocalProvider(LocalShellWidth provides ShellWidth.TwoColumn) {
                    Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                        Box(modifier = Modifier.width(807.dp).fillMaxHeight()) {
                            OrdersScreen(
                                viewModel = vm,
                                paymentBrowser = RecordingPaymentBrowser(),
                                supportContact = SupportContact(),
                                onBack = null,
                                onOpenOrder = {},
                                onOpenMyExams = {},
                                onOpenCheckout = {},
                                onOpenCatalog = {},
                                onSwitchAccount = {},
                            )
                        }
                    }
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_HEADING).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(ListDetailTestTags.TWO_COLUMN).assertIsDisplayed()
            composeRule.onNodeWithText("Đơn ${PaymentTestFixtures.ORDER_CODE}").assertIsDisplayed()
        } finally {
            scope.cancel()
            harness.close()
        }
    }
}
