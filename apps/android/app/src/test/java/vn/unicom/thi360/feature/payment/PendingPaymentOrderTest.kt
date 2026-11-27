package vn.unicom.thi360.feature.payment

import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.core.storage.AccountScopedStoreProvider
import vn.unicom.thi360.feature.payment.domain.PaymentResultEntry
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrder
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.viewmodel.PaymentResultViewModel
import vn.unicom.thi360.shell.routes.InboundLinkRouter
import vn.unicom.thi360.shell.routes.registerPaymentHooks

@OptIn(ExperimentalCoroutinesApi::class)
class PendingPaymentOrderTest {

    @Test
    fun expiresAfter15Minutes() {
        val (dir, store) = PaymentTestFixtures.createTempPendingStore()
        try {
            val openedAt = 1_000_000L
            store.save(PaymentTestFixtures.ORDER_CODE, openedAt)

            // 14m 59s -> still valid
            val at14m59s = openedAt + (14 * 60 + 59) * 1000L
            assertEquals(PaymentTestFixtures.ORDER_CODE, store.readValid(at14m59s)?.orderCode)

            // 15m 00s -> still valid
            val at15m00s = openedAt + PendingPaymentOrder.PENDING_PAYMENT_TTL_MILLIS
            assertEquals(PaymentTestFixtures.ORDER_CODE, store.readValid(at15m00s)?.orderCode)

            // 15m 01s -> expired & cleaned up
            val at15m01s = openedAt + (15 * 60 + 1) * 1000L
            assertNull(store.readValid(at15m01s))
            assertNull("Expired pending order must be removed from store", store.readValid(at14m59s))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun clearedOnTerminalLeaveLogout() = runBlocking {
        // 1. Cleared on terminal state (FULFILLED / FAILED / CLOSED)
        for (fixture in listOf(
            "orders-detail-200-fulfilled.json",
            "orders-detail-200-payment-failed.json",
            "orders-detail-200-cancelled.json",
        )) {
            val harness = PaymentTestFixtures.createHarness {
                respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, fixture)
            }
            val store = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
            store.save(PaymentTestFixtures.ORDER_CODE, 1_000L)
            val testScope = TestScope(StandardTestDispatcher())
            try {
                val vm = PaymentResultViewModel(
                    initialEntry = PaymentResultEntry.ByOrder(PaymentTestFixtures.ORDER_CODE),
                    gateway = PaymentTestFixtures.createGateway(harness),
                    pendingOrderStore = store,
                    apiBaseUrl = harness.server.url("/"),
                    scope = testScope,
                )
                vm.start()
                val deadline = System.currentTimeMillis() + 10_000L
                while (store.readValid(1_000L) != null && System.currentTimeMillis() < deadline) {
                    testScope.advanceUntilIdle()
                    Thread.sleep(10)
                }
                assertNull("Pending order must be cleared on terminal fixture $fixture", store.readValid(1_000L))
            } finally {
                harness.close()
            }
        }

        // 2. Cleared when leaving screen
        run {
            val harness = PaymentTestFixtures.createHarness {
                respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-pending-payment.json")
            }
            val store = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
            store.save(PaymentTestFixtures.ORDER_CODE, 1_000L)
            val testScope = TestScope(StandardTestDispatcher())
            try {
                val vm = PaymentResultViewModel(
                    initialEntry = PaymentResultEntry.ByOrder(PaymentTestFixtures.ORDER_CODE),
                    gateway = PaymentTestFixtures.createGateway(harness),
                    pendingOrderStore = store,
                    apiBaseUrl = harness.server.url("/"),
                    scope = testScope,
                )
                vm.onLeaveScreen()
                assertNull(store.readValid(1_000L))
            } finally {
                harness.close()
            }
        }

        // 3. Cleared on logout via SessionCleanup
        val rootDir = Files.createTempDirectory("thi360-pending-logout-").toFile()
        try {
            var currentAccountId: String? = PaymentTestFixtures.ACCOUNT_ID
            val provider = AccountScopedStoreProvider(rootDir = rootDir, accountIdProvider = { currentAccountId })
            val store = PendingPaymentOrderStore { provider.current() }
            val cleanup = SessionCleanup(rootDir)
            val router = InboundLinkRouter { "10.0.2.2" }
            registerPaymentHooks(
                inboundLinkRouter = router,
                sessionCleanup = cleanup,
                pendingOrderStoreProvider = { store },
                allowedHostProvider = { "10.0.2.2" },
            )
            store.save(PaymentTestFixtures.ORDER_CODE, 1_000L)
            assertEquals(PaymentTestFixtures.ORDER_CODE, store.readValid(1_000L)?.orderCode)

            cleanup.onLogout(PaymentTestFixtures.ACCOUNT_ID)
            assertNull(store.readValid(1_000L))
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun otherAccountDoesNotSee() {
        val rootDir = Files.createTempDirectory("thi360-pending-accounts-").toFile()
        try {
            val accountA = PendingPaymentOrderStore { AccountScopedStore(rootDir, PaymentTestFixtures.ACCOUNT_ID) }
            val accountB = PendingPaymentOrderStore { AccountScopedStore(rootDir, PaymentTestFixtures.OTHER_ACCOUNT_ID) }

            accountA.save(PaymentTestFixtures.ORDER_CODE, 1_000L)
            assertEquals(PaymentTestFixtures.ORDER_CODE, accountA.readValid(1_000L)?.orderCode)
            assertNull("Account B must not see Account A's pending order", accountB.readValid(1_000L))
        } finally {
            rootDir.deleteRecursively()
        }
    }
}
