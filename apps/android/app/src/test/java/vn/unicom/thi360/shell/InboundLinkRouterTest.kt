package vn.unicom.thi360.shell

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.shell.routes.InboundLinkParser
import vn.unicom.thi360.shell.routes.InboundLinkRouter

@RunWith(RobolectricTestRunner::class)
class InboundLinkRouterTest {

    @Test
    fun registeredParserDefaultsToRequiresUnlockedSessionAndQueuesUntilActive() {
        val router = InboundLinkRouter { "thi360.zone17.click" }
        val delivered = mutableListOf<String>()
        router.register(
            InboundLinkParser { uri ->
                if (uri.path == "/payment/return") {
                    { delivered += uri.getQueryParameter("order").orEmpty() }
                } else {
                    null
                }
            },
        )

        val paymentUri = Uri.parse("https://thi360.zone17.click/payment/return?order=ORD-1")

        // 1. Arrives while SignedOut -> queued in memory, not delivered yet.
        router.onSessionStateChanged(SessionState.SignedOut(SignOutReason.None))
        assertTrue(router.handleUri(paymentUri))
        assertTrue("Link must wait while SignedOut", delivered.isEmpty())

        // 2. Arrives while Locked -> still queued, not delivered yet.
        router.onSessionStateChanged(SessionState.Locked("student.demo"))
        assertTrue(router.handleUri(Uri.parse("https://thi360.zone17.click/payment/return?order=ORD-2")))
        assertTrue("Link must wait while Locked", delivered.isEmpty())

        // 3. Session unlocks -> Active -> queued actions delivered in order.
        router.onSessionStateChanged(SessionState.Active("student.demo", isOffline = false))
        assertEquals(listOf("ORD-1", "ORD-2"), delivered)

        // 4. Subsequent link while Active runs immediately.
        assertTrue(router.handleUri(Uri.parse("https://thi360.zone17.click/payment/return?order=ORD-3")))
        assertEquals(listOf("ORD-1", "ORD-2", "ORD-3"), delivered)
    }

    @Test
    fun builtInResetPasswordParserBypassesLockAndSignedOutAndClearsConsumedIntent() {
        val router = InboundLinkRouter { "thi360.zone17.click" }
        router.onSessionStateChanged(SessionState.Locked("student.locked"))

        val resetIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://thi360.zone17.click/reset-password?token=secret-tok"),
        )
        assertTrue(router.handleIntent(resetIntent))
        assertNotNull(router.activeResetOverlay.value)
        assertTrue(router.activeResetOverlay.value is vn.unicom.thi360.shell.ActiveResetOverlay.Valid)
        assertEquals(Intent.ACTION_MAIN, resetIntent.action)
        assertEquals(null, resetIntent.data)

        // Untrusted host is ignored.
        val evilIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://evil.example/reset-password?token=bad"),
        )
        assertFalse(router.handleIntent(evilIntent))
    }
}
