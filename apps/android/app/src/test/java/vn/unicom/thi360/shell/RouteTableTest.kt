package vn.unicom.thi360.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.legal.LegalId
import vn.unicom.thi360.shell.routes.AppRoute
import vn.unicom.thi360.shell.routes.AppRouteParser
import vn.unicom.thi360.shell.routes.PaymentReturnLinkStore

class RouteTableTest {

    @Test
    fun allB13RoutesRoundTripWithTypedParameters() {
        val linkStore = PaymentReturnLinkStore()
        val sampleLink = "https://thi360.zone17.click/payment/return?vnp_ResponseCode=00&vnp_TxnRef=ORD-1"

        val routes = listOf(
            AppRoute.Catalog,
            AppRoute.Product(code = "TOEIC-2026"),
            AppRoute.Checkout(code = "TOEIC-2026"),
            AppRoute.Checkout(code = "TOEIC-2026", seenVersionNo = 3, seenPrice = 199_000L),
            AppRoute.PaymentResult.ByOrder(orderCode = "ORD-9988"),
            AppRoute.PaymentResult.ByReturnLink(link = sampleLink),
            AppRoute.Orders,
            AppRoute.Order(orderCode = "ORD-9988"),
            AppRoute.MyExams,
            AppRoute.History(resetFilters = false),
            AppRoute.History(resetFilters = true),
            AppRoute.Prepare(entitlementId = "ent-42"),
            AppRoute.Attempt(attemptId = "att-77"),
            AppRoute.Result(attemptId = "att-77"),
            AppRoute.Review(attemptId = "att-77"),
            AppRoute.Profile,
            AppRoute.ProfileVerifyEmail,
            AppRoute.DeleteAccount,
            AppRoute.Legal(LegalId.TERMS),
            AppRoute.Legal(LegalId.PRIVACY),
        )

        for (route in routes) {
            val subPath = AppRoute.buildSubPath(route, linkStore)
            val parsed = AppRoute.parseOrNull(subPath, linkStore)
            assertEquals("Round-trip failed for $route via '$subPath'", route, parsed)

            if (route is AppRoute.PaymentResult.ByReturnLink) {
                assertFalse(
                    "Return link query params must stay in memory, never serialized into route string",
                    subPath.contains("vnp_"),
                )
                assertTrue(route.toString().contains("[REDACTED]"))
            }
        }
    }

    @Test
    fun tabPrefixedRoutesAndDeleteAccountParseWithoutCollision() {
        assertEquals(Destination.Home.route, "home")
        assertEquals(Destination.MyExams.route, "my_exams")
        assertEquals(Destination.History.route, "history")
        assertEquals(Destination.Account.route, "account")

        assertEquals(AppRoute.Product("P1"), AppRouteParser.parse("home/product/P1"))
        assertEquals(AppRoute.Result("ATT-1"), AppRouteParser.parse("my_exams/result/ATT-1"))
        assertEquals(AppRoute.Result("ATT-1"), AppRouteParser.parse("history/result/ATT-1"))
        assertEquals(AppRoute.DeleteAccount, AppRouteParser.parse("account/account/delete"))
        assertEquals(AppRoute.DeleteAccount, AppRouteParser.parse("account/delete"))
        assertNotEquals(
            AppRoute.tabRoute(Destination.MyExams, AppRoute.RESULT_PATTERN),
            AppRoute.tabRoute(Destination.History, AppRoute.RESULT_PATTERN),
        )
    }

    @Test
    fun profileEmailRouteDoesNotExistAndMalformedOrMissingParamsAreRejectedWithoutThrowing() {
        val invalidRoutes = listOf(
            "",
            "   ",
            "profile/email",
            "account/profile/email",
            "product",
            "product/",
            "product/%20",
            "checkout",
            "checkout/P1?seenVersionNo=abc",
            "checkout/P1?seenPrice=not-a-number",
            "checkout/P1?unknownParam=1",
            "payment-result",
            "payment-result?orderCode=ORD-1&linkToken=tok-1",
            "payment-result?linkToken=unknown-token",
            "order",
            "order/",
            "history?resetFilters=maybe",
            "prepare",
            "attempt",
            "result",
            "review",
            "legal/unknown",
            "legal/",
            "unknown/path",
        )

        for (raw in invalidRoutes) {
            assertNull("Route '$raw' must be rejected as null without throwing", AppRouteParser.parse(raw))
        }
    }
}
