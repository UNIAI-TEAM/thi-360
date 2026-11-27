package vn.unicom.thi360.feature.payment

import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.generated.model.CreateOrderRequest
import vn.unicom.thi360.core.network.generated.model.PriceQuoteRequest
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.payment.data.OpenApiPaymentGateway
import vn.unicom.thi360.feature.payment.domain.PaymentResultEntry
import vn.unicom.thi360.feature.payment.domain.PaymentReturnLink

class PaymentLogRedactionTest {

    @Test
    fun demoLogsAndToStringNeverExposeVoucherVnpParamsOrTokens() = runBlocking {
        val secretVoucher = "SECRET-VOUCHER-999"
        val secretHash = "deadbeefsupersecretvnpayhash123456"
        val secretTxnRef = "TXN-SECRET-98765"
        val secretAccessToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.supersecretjwtpayload"
        val secretRefreshToken = "refresh-secret-token-xyz-999"

        val harness = PaymentTestFixtures.createHarness(
            initialTokens = Tokens(
                accessToken = secretAccessToken,
                refreshToken = secretRefreshToken,
                accessExpiresIn = 900,
                refreshExpiresIn = 3600,
                mustChangePassword = false,
                accountId = PaymentTestFixtures.ACCOUNT_ID,
            ),
            environment = Environment.DEMO,
            serverSetup = {
                respondApiFixture("POST", "/api/v1/me/price-quote", 422, "price-quote-422-voucher-invalid.json")
                respondApiFixture("POST", "/api/v1/me/orders", 422, "orders-create-422-voucher-invalid.json")
                respondApiFixture("GET", "/api/v1/public/payments/vnpay/return", 422, "vnpay-return-422-vnpay-signature-invalid.json")
                respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 404, "orders-detail-404-order-not-found.json")
            },
        )
        try {
            val gateway = OpenApiPaymentGateway.fromSessionStack(harness.stack)
            gateway.requestQuote(PriceQuoteRequest(code = PaymentTestFixtures.EXAM_CODE, voucherCode = secretVoucher))
            gateway.createOrder(
                CreateOrderRequest(
                    code = PaymentTestFixtures.EXAM_CODE,
                    expectedAccountId = UUID.fromString(PaymentTestFixtures.ACCOUNT_ID),
                    expectedAmountDue = 200_000L,
                    expectedVersionNo = 1,
                    voucherCode = secretVoucher,
                ),
                idempotencyKey = UUID.randomUUID().toString(),
            )
            val vnpParams = linkedMapOf(
                "vnp_TxnRef" to secretTxnRef,
                "vnp_ResponseCode" to "00",
                "vnp_SecureHash" to secretHash,
            )
            gateway.verifyVnpayReturn(vnpParams)
            gateway.getOrder(PaymentTestFixtures.ORDER_CODE)

            val link = PaymentReturnLink(vnpParams)
            val entry = PaymentResultEntry.ByReturnLink(link)
            val combined = (harness.logs + link.toString() + entry.toString()).joinToString("\n")
            assertTrue("Expected demo error logs to be recorded", harness.logs.isNotEmpty())
            assertFalse("Voucher code leaked: $combined", combined.contains(secretVoucher))
            assertFalse("vnp_SecureHash leaked: $combined", combined.contains(secretHash))
            assertFalse("vnp_TxnRef leaked: $combined", combined.contains(secretTxnRef))
            assertFalse("vnp_ key leaked: $combined", combined.contains("vnp_SecureHash"))
            assertFalse("Access token leaked: $combined", combined.contains(secretAccessToken))
            assertFalse("Refresh token leaked: $combined", combined.contains(secretRefreshToken))
        } finally {
            harness.close()
        }
    }
}
