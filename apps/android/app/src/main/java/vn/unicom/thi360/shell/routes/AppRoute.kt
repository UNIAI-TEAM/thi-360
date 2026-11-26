package vn.unicom.thi360.shell.routes

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import vn.unicom.thi360.core.legal.LegalId
import vn.unicom.thi360.shell.Destination

class PaymentReturnLinkStore {
    private val links = ConcurrentHashMap<String, String>()

    fun put(link: String): String {
        require(link.isNotBlank()) { "Return link must not be blank" }
        val token = UUID.randomUUID().toString()
        links[token] = link
        return token
    }

    fun get(token: String?): String? = token?.let { links[it] }

    fun remove(token: String) {
        links.remove(token)
    }

    fun clear() {
        links.clear()
    }

    companion object {
        val Default = PaymentReturnLinkStore()
    }
}

sealed interface AppRoute {
    data object Catalog : AppRoute

    data class Product(val code: String) : AppRoute {
        init {
            require(code.isNotBlank()) { "Product code must not be blank" }
        }
    }

    data class Checkout(
        val code: String,
        val seenVersionNo: Int? = null,
        val seenPrice: Long? = null,
    ) : AppRoute {
        init {
            require(code.isNotBlank()) { "Checkout code must not be blank" }
        }
    }

    sealed interface PaymentResult : AppRoute {
        data class ByOrder(val orderCode: String) : PaymentResult {
            init {
                require(orderCode.isNotBlank()) { "Order code must not be blank" }
            }
        }

        data class ByReturnLink(val link: String) : PaymentResult {
            init {
                require(link.isNotBlank()) { "Return link must not be blank" }
            }

            override fun toString(): String = "ByReturnLink(link=[REDACTED])"
        }
    }

    data object Orders : AppRoute

    data class Order(val orderCode: String) : AppRoute {
        init {
            require(orderCode.isNotBlank()) { "Order code must not be blank" }
        }
    }

    data object MyExams : AppRoute

    data class History(val resetFilters: Boolean = false) : AppRoute

    data class Prepare(val entitlementId: String) : AppRoute {
        init {
            require(entitlementId.isNotBlank()) { "Entitlement ID must not be blank" }
        }
    }

    data class Attempt(val attemptId: String) : AppRoute {
        init {
            require(attemptId.isNotBlank()) { "Attempt ID must not be blank" }
        }
    }

    data class Result(val attemptId: String) : AppRoute {
        init {
            require(attemptId.isNotBlank()) { "Attempt ID must not be blank" }
        }
    }

    data class Review(val attemptId: String) : AppRoute {
        init {
            require(attemptId.isNotBlank()) { "Attempt ID must not be blank" }
        }
    }

    data object Profile : AppRoute

    data object ProfileVerifyEmail : AppRoute

    data object DeleteAccount : AppRoute

    data class Legal(val id: LegalId) : AppRoute

    companion object {
        const val ARG_CODE = "code"
        const val ARG_SEEN_VERSION_NO = "seenVersionNo"
        const val ARG_SEEN_PRICE = "seenPrice"
        const val ARG_ORDER_CODE = "orderCode"
        const val ARG_LINK_TOKEN = "linkToken"
        const val ARG_RESET_FILTERS = "resetFilters"
        const val ARG_ENTITLEMENT_ID = "entitlementId"
        const val ARG_ATTEMPT_ID = "attemptId"
        const val ARG_LEGAL_ID = "id"

        const val CATALOG_PATH = "catalog"
        const val PRODUCT_PATTERN = "product/{$ARG_CODE}"
        const val CHECKOUT_PATTERN =
            "checkout/{$ARG_CODE}?$ARG_SEEN_VERSION_NO={$ARG_SEEN_VERSION_NO}&$ARG_SEEN_PRICE={$ARG_SEEN_PRICE}"
        const val PAYMENT_RESULT_PATTERN =
            "payment-result?$ARG_ORDER_CODE={$ARG_ORDER_CODE}&$ARG_LINK_TOKEN={$ARG_LINK_TOKEN}"
        const val ORDERS_PATH = "orders"
        const val ORDER_PATTERN = "order/{$ARG_ORDER_CODE}"
        const val MY_EXAMS_PATH = "my-exams"
        const val HISTORY_PATTERN = "history?$ARG_RESET_FILTERS={$ARG_RESET_FILTERS}"
        const val PREPARE_PATTERN = "prepare/{$ARG_ENTITLEMENT_ID}"
        const val ATTEMPT_PATTERN = "attempt/{$ARG_ATTEMPT_ID}"
        const val RESULT_PATTERN = "result/{$ARG_ATTEMPT_ID}"
        const val REVIEW_PATTERN = "review/{$ARG_ATTEMPT_ID}"
        const val ACCOUNT_ROOT_PATH = "root"
        const val PROFILE_PATH = "profile"
        const val PROFILE_VERIFY_EMAIL_PATH = "profile/verify-email"
        const val DELETE_ACCOUNT_PATH = "account/delete"
        const val LEGAL_PATTERN = "legal/{$ARG_LEGAL_ID}"

        fun tabRoute(tab: Destination, subPath: String): String = "${tab.route}/$subPath"

        fun rootRouteFor(tab: Destination): String = when (tab) {
            Destination.Home -> tabRoute(tab, CATALOG_PATH)
            Destination.MyExams -> tabRoute(tab, MY_EXAMS_PATH)
            Destination.History -> tabRoute(tab, HISTORY_PATTERN)
            Destination.Account -> tabRoute(tab, ACCOUNT_ROOT_PATH)
        }

        fun encodeSegment(raw: String): String =
            URLEncoder.encode(raw, StandardCharsets.UTF_8.name()).replace("+", "%20")

        fun decodeSegment(encoded: String): String? = runCatching {
            URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
        }.getOrNull()

        fun buildSubPath(
            route: AppRoute,
            linkStore: PaymentReturnLinkStore = PaymentReturnLinkStore.Default,
        ): String = when (route) {
            Catalog -> CATALOG_PATH
            is Product -> "product/${encodeSegment(route.code)}"
            is Checkout -> buildString {
                append("checkout/").append(encodeSegment(route.code))
                val params = buildList {
                    route.seenVersionNo?.let { add("$ARG_SEEN_VERSION_NO=$it") }
                    route.seenPrice?.let { add("$ARG_SEEN_PRICE=$it") }
                }
                if (params.isNotEmpty()) {
                    append('?').append(params.joinToString("&"))
                }
            }
            is PaymentResult.ByOrder -> "payment-result?$ARG_ORDER_CODE=${encodeSegment(route.orderCode)}"
            is PaymentResult.ByReturnLink -> {
                val token = linkStore.put(route.link)
                "payment-result?$ARG_LINK_TOKEN=${encodeSegment(token)}"
            }
            Orders -> ORDERS_PATH
            is Order -> "order/${encodeSegment(route.orderCode)}"
            MyExams -> MY_EXAMS_PATH
            is History -> "history?$ARG_RESET_FILTERS=${route.resetFilters}"
            is Prepare -> "prepare/${encodeSegment(route.entitlementId)}"
            is Attempt -> "attempt/${encodeSegment(route.attemptId)}"
            is Result -> "result/${encodeSegment(route.attemptId)}"
            is Review -> "review/${encodeSegment(route.attemptId)}"
            Profile -> PROFILE_PATH
            ProfileVerifyEmail -> PROFILE_VERIFY_EMAIL_PATH
            DeleteAccount -> DELETE_ACCOUNT_PATH
            is Legal -> "legal/${route.id.routeValue}"
        }

        fun parseOrNull(
            rawRoute: String,
            linkStore: PaymentReturnLinkStore = PaymentReturnLinkStore.Default,
        ): AppRoute? = AppRouteParser.parse(rawRoute, linkStore)
    }
}

object AppRouteParser {
    private val tabPrefixes = Destination.entries.map { "${it.route}/" }

    fun parse(
        rawRoute: String,
        linkStore: PaymentReturnLinkStore = PaymentReturnLinkStore.Default,
    ): AppRoute? {
        val trimmed = rawRoute.trim()
        if (trimmed.isEmpty()) return null

        val queryIdx = trimmed.indexOf('?')
        val rawPath = if (queryIdx >= 0) trimmed.substring(0, queryIdx) else trimmed
        val rawQuery = if (queryIdx >= 0) trimmed.substring(queryIdx + 1) else ""
        val query = parseQuery(rawQuery) ?: return null

        val strippedPath = stripTabPrefix(rawPath.trimStart('/'))
        val segments = strippedPath.split('/')

        return when (segments.firstOrNull()) {
            "catalog" -> if (segments.size == 1 && query.isEmpty()) AppRoute.Catalog else null

            "product" -> {
                if (segments.size != 2 || query.isNotEmpty()) return null
                val code = AppRoute.decodeSegment(segments[1])?.takeIf { it.isNotBlank() } ?: return null
                AppRoute.Product(code)
            }

            "checkout" -> {
                if (segments.size != 2) return null
                val code = AppRoute.decodeSegment(segments[1])?.takeIf { it.isNotBlank() } ?: return null
                val allowedKeys = setOf(AppRoute.ARG_SEEN_VERSION_NO, AppRoute.ARG_SEEN_PRICE)
                if (query.keys.any { it !in allowedKeys }) return null
                val versionRaw = query[AppRoute.ARG_SEEN_VERSION_NO]
                val seenVersionNo = if (versionRaw != null) {
                    versionRaw.toIntOrNull() ?: return null
                } else {
                    null
                }
                val priceRaw = query[AppRoute.ARG_SEEN_PRICE]
                val seenPrice = if (priceRaw != null) {
                    priceRaw.toLongOrNull() ?: return null
                } else {
                    null
                }
                AppRoute.Checkout(code = code, seenVersionNo = seenVersionNo, seenPrice = seenPrice)
            }

            "payment-result" -> {
                if (segments.size != 1) return null
                val allowedKeys = setOf(AppRoute.ARG_ORDER_CODE, AppRoute.ARG_LINK_TOKEN)
                if (query.keys.any { it !in allowedKeys }) return null
                val orderCode = query[AppRoute.ARG_ORDER_CODE]?.takeIf { it.isNotBlank() }
                val linkToken = query[AppRoute.ARG_LINK_TOKEN]?.takeIf { it.isNotBlank() }
                when {
                    orderCode != null && linkToken == null -> AppRoute.PaymentResult.ByOrder(orderCode)
                    linkToken != null && orderCode == null -> {
                        val link = linkStore.get(linkToken)?.takeIf { it.isNotBlank() } ?: return null
                        AppRoute.PaymentResult.ByReturnLink(link)
                    }
                    else -> null
                }
            }

            "orders" -> if (segments.size == 1 && query.isEmpty()) AppRoute.Orders else null

            "order" -> {
                if (segments.size != 2 || query.isNotEmpty()) return null
                val orderCode = AppRoute.decodeSegment(segments[1])?.takeIf { it.isNotBlank() } ?: return null
                AppRoute.Order(orderCode)
            }

            "my-exams" -> if (segments.size == 1 && query.isEmpty()) AppRoute.MyExams else null

            "history" -> {
                if (segments.size != 1) return null
                if (query.keys.any { it != AppRoute.ARG_RESET_FILTERS }) return null
                val resetRaw = query[AppRoute.ARG_RESET_FILTERS]
                val resetFilters = when (resetRaw) {
                    null, "false" -> false
                    "true" -> true
                    else -> return null
                }
                AppRoute.History(resetFilters = resetFilters)
            }

            "prepare" -> {
                if (segments.size != 2 || query.isNotEmpty()) return null
                val entitlementId = AppRoute.decodeSegment(segments[1])?.takeIf { it.isNotBlank() } ?: return null
                AppRoute.Prepare(entitlementId)
            }

            "attempt" -> {
                if (segments.size != 2 || query.isNotEmpty()) return null
                val attemptId = AppRoute.decodeSegment(segments[1])?.takeIf { it.isNotBlank() } ?: return null
                AppRoute.Attempt(attemptId)
            }

            "result" -> {
                if (segments.size != 2 || query.isNotEmpty()) return null
                val attemptId = AppRoute.decodeSegment(segments[1])?.takeIf { it.isNotBlank() } ?: return null
                AppRoute.Result(attemptId)
            }

            "review" -> {
                if (segments.size != 2 || query.isNotEmpty()) return null
                val attemptId = AppRoute.decodeSegment(segments[1])?.takeIf { it.isNotBlank() } ?: return null
                AppRoute.Review(attemptId)
            }

            "profile" -> when {
                segments.size == 1 && query.isEmpty() -> AppRoute.Profile
                segments.size == 2 && segments[1] == "verify-email" && query.isEmpty() -> AppRoute.ProfileVerifyEmail
                else -> null
            }

            "account" -> when {
                segments.size == 2 && segments[1] == "delete" && query.isEmpty() -> AppRoute.DeleteAccount
                else -> null
            }

            "legal" -> {
                if (segments.size != 2 || query.isNotEmpty()) return null
                val legalId = LegalId.fromRouteValue(segments[1]) ?: return null
                AppRoute.Legal(legalId)
            }

            else -> null
        }
    }

    private fun stripTabPrefix(path: String): String {
        for (prefix in tabPrefixes) {
            if (path.startsWith(prefix)) {
                val remainder = path.removePrefix(prefix)
                // Keep "account/delete" intact when it is the unprefixed logical route itself.
                if (prefix == "account/" && remainder == "delete") {
                    return path
                }
                return remainder
            }
        }
        return path
    }

    private fun parseQuery(rawQuery: String): Map<String, String>? {
        if (rawQuery.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val eqIdx = pair.indexOf('=')
            if (eqIdx <= 0) return null
            val key = AppRoute.decodeSegment(pair.substring(0, eqIdx)) ?: return null
            val value = AppRoute.decodeSegment(pair.substring(eqIdx + 1)) ?: return null
            if (value.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }
}
