package vn.unicom.thi360.feature.payment

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.BuildConfig
import vn.unicom.thi360.MainActivity
import vn.unicom.thi360.Thi360Application

@RunWith(RobolectricTestRunner::class)
class ManifestPaymentReturnLinkTest {

    private fun expectedHost(): String = when (BuildConfig.FLAVOR) {
        "dev" -> "10.0.2.2"
        "demo" -> "thi360.zone17.click"
        else -> error("Unexpected flavor: ${BuildConfig.FLAVOR}")
    }

    @Test
    fun manifestClaimsCheckoutResultAppAndResetPasswordAndRejectsCheckoutResult() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val pm = context.packageManager
        val host = expectedHost()

        val validPayIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://$host/checkout/result/app?vnp_ResponseCode=00"),
        ).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(context.packageName)
        }
        val resolvedPay = pm.queryIntentActivities(validPayIntent, 0)
        assertEquals(1, resolvedPay.size)
        assertEquals(MainActivity::class.java.name, resolvedPay.single().activityInfo.name)

        val validResetIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://$host/reset-password?token=abc"),
        ).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(context.packageName)
        }
        assertEquals(1, pm.queryIntentActivities(validResetIntent, 0).size)

        val rejected = listOf(
            "https://$host/checkout/result?vnp_ResponseCode=00",
            "https://$host/checkout/result/app/x?vnp_ResponseCode=00",
            "http://$host/checkout/result/app?vnp_ResponseCode=00",
            "https://evil.example/checkout/result/app?vnp_ResponseCode=00",
        )
        for (raw in rejected) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(raw)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(context.packageName)
            }
            assertTrue("Must NOT claim $raw", pm.queryIntentActivities(intent, 0).isEmpty())
        }
    }
}
