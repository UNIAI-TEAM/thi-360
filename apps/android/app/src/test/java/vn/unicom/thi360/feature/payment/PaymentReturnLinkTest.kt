package vn.unicom.thi360.feature.payment

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.feature.payment.domain.PaymentReturnLink

@RunWith(RobolectricTestRunner::class)
class PaymentReturnLinkTest {

    @Test
    fun parsesValidLinkAndRedactsToString() {
        val uri = Uri.parse(
            "https://thi360.zone17.click/checkout/result/app?vnp_TxnRef=DH001&vnp_ResponseCode=00&vnp_Amount=20000000&vnp_SecureHash=abc123&extra=ignored",
        )
        val parsed = PaymentReturnLink.parse(uri, "thi360.zone17.click")
        assertNotNull(parsed)
        assertEquals(
            mapOf(
                "vnp_TxnRef" to "DH001",
                "vnp_ResponseCode" to "00",
                "vnp_Amount" to "20000000",
                "vnp_SecureHash" to "abc123",
            ),
            parsed!!.vnpParams,
        )
        assertEquals("DH001", parsed.txnRef)
        assertEquals("00", parsed.responseCode)

        val str = parsed.toString()
        assertFalse(str.contains("DH001"))
        assertFalse(str.contains("abc123"))
        assertFalse(str.contains("20000000"))
    }

    @Test
    fun rejectsInvalidSchemeHostPathOrMissingVnpParams() {
        val allowedHost = "thi360.zone17.click"
        val rejected = listOf(
            "http://thi360.zone17.click/checkout/result/app?vnp_ResponseCode=00",
            "https://evil.example/checkout/result/app?vnp_ResponseCode=00",
            "https://thi360.zone17.click/checkout/result?vnp_ResponseCode=00",
            "https://thi360.zone17.click/checkout/result/app/x?vnp_ResponseCode=00",
            "https://thi360.zone17.click/checkout/result/app/?vnp_ResponseCode=00",
            "https://thi360.zone17.click/checkout/result/app",
            "https://thi360.zone17.click/checkout/result/app?foo=bar",
            "https://user:pass@thi360.zone17.click/checkout/result/app?vnp_ResponseCode=00",
        )
        for (raw in rejected) {
            assertNull("Must reject $raw", PaymentReturnLink.parse(Uri.parse(raw), allowedHost))
        }
    }
}
