package vn.unicom.thi360.feature.payment.domain

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import vn.unicom.thi360.core.storage.AccountScopedStore

data class SeenPrice(
    val versionNo: Int? = null,
    val price: Long? = null,
)

class PaymentReturnLink(
    val vnpParams: Map<String, String>,
) {
    val txnRef: String?
        get() = vnpParams["vnp_TxnRef"]?.takeIf { it.isNotBlank() }

    val responseCode: String?
        get() = vnpParams["vnp_ResponseCode"]?.takeIf { it.isNotBlank() }

    override fun equals(other: Any?): Boolean =
        this === other || (other is PaymentReturnLink && vnpParams == other.vnpParams)

    override fun hashCode(): Int = vnpParams.hashCode()

    override fun toString(): String = "PaymentReturnLink(paramCount=${vnpParams.size}, params=[REDACTED])"

    companion object {
        const val RETURN_PATH = "/checkout/result/app"

        fun parse(uri: Uri?, allowedHost: String): PaymentReturnLink? {
            if (uri == null || allowedHost.isBlank()) return null
            val scheme = uri.scheme ?: return null
            if (!scheme.equals("https", ignoreCase = true)) return null
            if (!uri.userInfo.isNullOrEmpty()) return null
            val host = uri.host ?: return null
            if (!host.equals(allowedHost.trim(), ignoreCase = true)) return null
            if (uri.path != RETURN_PATH) return null
            val vnpParams = extractVnpParams(uri.encodedQuery)
            if (vnpParams.isEmpty()) return null
            return PaymentReturnLink(vnpParams)
        }

        fun fromStoredLink(rawLink: String): PaymentReturnLink? {
            if (rawLink.isBlank()) return null
            val uri = runCatching { Uri.parse(rawLink) }.getOrNull() ?: return null
            val vnpParams = extractVnpParams(uri.encodedQuery)
            if (vnpParams.isEmpty()) return null
            return PaymentReturnLink(vnpParams)
        }

        internal fun extractVnpParams(encodedQuery: String?): Map<String, String> {
            if (encodedQuery.isNullOrBlank()) return emptyMap()
            val result = linkedMapOf<String, String>()
            for (pair in encodedQuery.split('&')) {
                if (pair.isEmpty()) continue
                val eqIdx = pair.indexOf('=')
                val rawKey = if (eqIdx >= 0) pair.substring(0, eqIdx) else pair
                val rawValue = if (eqIdx >= 0) pair.substring(eqIdx + 1) else ""
                val key = decodeQueryComponent(rawKey) ?: continue
                if (key.startsWith("vnp_")) {
                    val value = decodeQueryComponent(rawValue) ?: ""
                    result[key] = value
                }
            }
            return result
        }

        private fun decodeQueryComponent(raw: String): String? = runCatching {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
        }.getOrNull()
    }
}

sealed interface PaymentResultEntry {
    data class ByOrder(val orderCode: String) : PaymentResultEntry

    data class ByReturnLink(val link: PaymentReturnLink) : PaymentResultEntry {
        override fun toString(): String = "ByReturnLink(link=[REDACTED])"
    }
}

@Serializable
data class PendingPaymentOrder(
    val orderCode: String,
    val openedAtMillis: Long,
) {
    fun isExpired(nowMillis: Long): Boolean =
        nowMillis - openedAtMillis > PENDING_PAYMENT_TTL_MILLIS

    companion object {
        const val PENDING_PAYMENT_TTL_MILLIS = 15L * 60L * 1000L
    }
}

class PendingPaymentOrderStore(
    private val storeProvider: () -> AccountScopedStore?,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(orderCode: String, openedAtMillis: Long) {
        if (orderCode.isBlank()) return
        val store = storeProvider() ?: return
        val encoded = json.encodeToString(
            PendingPaymentOrder.serializer(),
            PendingPaymentOrder(orderCode = orderCode, openedAtMillis = openedAtMillis),
        )
        store.writeString(NAMESPACE, KEY, encoded)
    }

    fun readValid(nowMillis: Long): PendingPaymentOrder? {
        val store = storeProvider() ?: return null
        val raw = store.readString(NAMESPACE, KEY) ?: return null
        val parsed = runCatching {
            json.decodeFromString(PendingPaymentOrder.serializer(), raw)
        }.getOrNull()
        if (parsed == null || parsed.orderCode.isBlank() || parsed.isExpired(nowMillis)) {
            store.delete(NAMESPACE, KEY)
            return null
        }
        return parsed
    }

    fun clear() {
        storeProvider()?.delete(NAMESPACE, KEY)
    }

    fun clearIfMatches(orderCode: String) {
        val store = storeProvider() ?: return
        val raw = store.readString(NAMESPACE, KEY) ?: return
        val parsed = runCatching {
            json.decodeFromString(PendingPaymentOrder.serializer(), raw)
        }.getOrNull()
        if (parsed == null || parsed.orderCode == orderCode) {
            store.delete(NAMESPACE, KEY)
        }
    }

    companion object {
        const val NAMESPACE = "payment"
        const val KEY = "pending_order"
    }
}

object PaymentUrlResolver {
    fun resolve(paymentUrl: String, apiBaseUrl: String): String {
        val trimmed = paymentUrl.trim()
        if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        ) {
            return trimmed
        }
        val cleanBase = apiBaseUrl.trim().trimEnd('/')
        val cleanRelative = trimmed.trimStart('/')
        return "$cleanBase/$cleanRelative"
    }
}

fun interface PaymentBrowser {
    fun open(context: Context, url: String): Boolean
}

class CustomTabsPaymentBrowser : PaymentBrowser {
    override fun open(context: Context, url: String): Boolean {
        return try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            if (context !is Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            customTabsIntent.launchUrl(context, Uri.parse(url))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
