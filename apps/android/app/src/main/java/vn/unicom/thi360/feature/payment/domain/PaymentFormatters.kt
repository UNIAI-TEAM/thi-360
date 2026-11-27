package vn.unicom.thi360.feature.payment.domain

import android.content.res.Resources
import androidx.annotation.StringRes
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import vn.unicom.thi360.R
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.generated.model.DecimalRange
import vn.unicom.thi360.core.network.generated.model.IntRange
import vn.unicom.thi360.core.network.generated.model.OrderDetail
import vn.unicom.thi360.core.network.generated.model.OrderPaymentView
import vn.unicom.thi360.core.network.generated.model.OrderSummary
import vn.unicom.thi360.core.preferences.AppLocale

enum class PaymentStatusTone {
    Success,
    Warning,
    Danger,
    Info,
    Neutral,
}

enum class VoucherErrorKey {
    RATE_LIMITED,
    VOUCHER_EXHAUSTED,
    VOUCHER_EXPIRED,
    VOUCHER_INVALID,
    VOUCHER_NOT_APPLICABLE,
    VOUCHER_NOT_ELIGIBLE,
    VOUCHER_USER_LIMIT,
    GENERIC,
}

object VoucherErrorMapper {
    fun map(statusCode: Int?, errorCode: String?): VoucherErrorKey {
        if (statusCode == 429 || errorCode == "RATE_LIMITED") {
            return VoucherErrorKey.RATE_LIMITED
        }
        return when (errorCode) {
            "VOUCHER_EXHAUSTED" -> VoucherErrorKey.VOUCHER_EXHAUSTED
            "VOUCHER_EXPIRED" -> VoucherErrorKey.VOUCHER_EXPIRED
            "VOUCHER_INVALID" -> VoucherErrorKey.VOUCHER_INVALID
            "VOUCHER_NOT_APPLICABLE" -> VoucherErrorKey.VOUCHER_NOT_APPLICABLE
            "VOUCHER_NOT_ELIGIBLE" -> VoucherErrorKey.VOUCHER_NOT_ELIGIBLE
            "VOUCHER_USER_LIMIT" -> VoucherErrorKey.VOUCHER_USER_LIMIT
            else -> VoucherErrorKey.GENERIC
        }
    }

    @StringRes
    fun stringRes(key: VoucherErrorKey, isBundle: Boolean): Int = when (key) {
        VoucherErrorKey.RATE_LIMITED -> R.string.checkout_voucher_errors_RATE_LIMITED
        VoucherErrorKey.VOUCHER_EXHAUSTED -> R.string.checkout_voucher_errors_VOUCHER_EXHAUSTED
        VoucherErrorKey.VOUCHER_EXPIRED -> R.string.checkout_voucher_errors_VOUCHER_EXPIRED
        VoucherErrorKey.VOUCHER_INVALID -> R.string.checkout_voucher_errors_VOUCHER_INVALID
        VoucherErrorKey.VOUCHER_NOT_APPLICABLE ->
            if (isBundle) {
                R.string.checkout_voucher_errors_VOUCHER_NOT_APPLICABLE_BUNDLE
            } else {
                R.string.checkout_voucher_errors_VOUCHER_NOT_APPLICABLE
            }
        VoucherErrorKey.VOUCHER_NOT_ELIGIBLE -> R.string.checkout_voucher_errors_VOUCHER_NOT_ELIGIBLE
        VoucherErrorKey.VOUCHER_USER_LIMIT -> R.string.checkout_voucher_errors_VOUCHER_USER_LIMIT
        VoucherErrorKey.GENERIC -> R.string.checkout_voucher_errors_generic
    }
}

enum class OrderActionErrorKey {
    ORDER_EXPIRED,
    ORDER_NOT_FOUND,
    ORDER_NOT_PENDING,
    PAYMENT_ATTEMPTS_EXCEEDED,
    PRODUCT_DISCONTINUED,
    PRODUCT_NOT_FOUND,
    RATE_LIMITED,
    GENERIC,
}

object OrderActionErrorMapper {
    fun map(statusCode: Int?, errorCode: String?): OrderActionErrorKey {
        if (statusCode == 429 || errorCode == "RATE_LIMITED") {
            return OrderActionErrorKey.RATE_LIMITED
        }
        if (statusCode == 410 || errorCode == "PRODUCT_DISCONTINUED") {
            return OrderActionErrorKey.PRODUCT_DISCONTINUED
        }
        if (statusCode == 404 && errorCode == "PRODUCT_NOT_FOUND") {
            return OrderActionErrorKey.PRODUCT_NOT_FOUND
        }
        if (statusCode == 404 || errorCode == "ORDER_NOT_FOUND") {
            return OrderActionErrorKey.ORDER_NOT_FOUND
        }
        return when (errorCode) {
            "ORDER_EXPIRED" -> OrderActionErrorKey.ORDER_EXPIRED
            "ORDER_NOT_PENDING" -> OrderActionErrorKey.ORDER_NOT_PENDING
            "PAYMENT_ATTEMPTS_EXCEEDED" -> OrderActionErrorKey.PAYMENT_ATTEMPTS_EXCEEDED
            else -> OrderActionErrorKey.GENERIC
        }
    }

    fun mapFailure(failure: ApiResult<*>): OrderActionErrorKey = when (failure) {
        is ApiResult.HttpFailure -> map(failure.statusCode, failure.error?.code)
        is ApiResult.NetworkFailure -> OrderActionErrorKey.GENERIC
        is ApiResult.Success -> OrderActionErrorKey.GENERIC
    }

    @StringRes
    fun stringRes(key: OrderActionErrorKey): Int = when (key) {
        OrderActionErrorKey.ORDER_EXPIRED -> R.string.orders_errors_ORDER_EXPIRED
        OrderActionErrorKey.ORDER_NOT_FOUND -> R.string.orders_errors_ORDER_NOT_FOUND
        OrderActionErrorKey.ORDER_NOT_PENDING -> R.string.orders_errors_ORDER_NOT_PENDING
        OrderActionErrorKey.PAYMENT_ATTEMPTS_EXCEEDED -> R.string.orders_errors_PAYMENT_ATTEMPTS_EXCEEDED
        OrderActionErrorKey.PRODUCT_DISCONTINUED -> R.string.orders_errors_PRODUCT_DISCONTINUED
        OrderActionErrorKey.PRODUCT_NOT_FOUND -> R.string.orders_errors_PRODUCT_NOT_FOUND
        OrderActionErrorKey.RATE_LIMITED -> R.string.orders_errors_RATE_LIMITED
        OrderActionErrorKey.GENERIC -> R.string.orders_errors_generic
    }
}

enum class VnpayReasonKey {
    CODE_07,
    CODE_09,
    CODE_10,
    CODE_11,
    CODE_12,
    CODE_13,
    CODE_24,
    CODE_51,
    CODE_65,
    CODE_75,
    CODE_79,
    CODE_99,
    GENERIC,
}

object VnpayReasonMapper {
    fun map(responseCode: String?): VnpayReasonKey = when (responseCode?.trim()) {
        "07" -> VnpayReasonKey.CODE_07
        "09" -> VnpayReasonKey.CODE_09
        "10" -> VnpayReasonKey.CODE_10
        "11" -> VnpayReasonKey.CODE_11
        "12" -> VnpayReasonKey.CODE_12
        "13" -> VnpayReasonKey.CODE_13
        "24" -> VnpayReasonKey.CODE_24
        "51" -> VnpayReasonKey.CODE_51
        "65" -> VnpayReasonKey.CODE_65
        "75" -> VnpayReasonKey.CODE_75
        "79" -> VnpayReasonKey.CODE_79
        "99" -> VnpayReasonKey.CODE_99
        else -> VnpayReasonKey.GENERIC
    }

    @StringRes
    fun stringRes(key: VnpayReasonKey): Int = when (key) {
        VnpayReasonKey.CODE_07 -> R.string.orders_vnpayReasons_07
        VnpayReasonKey.CODE_09 -> R.string.orders_vnpayReasons_09
        VnpayReasonKey.CODE_10 -> R.string.orders_vnpayReasons_10
        VnpayReasonKey.CODE_11 -> R.string.orders_vnpayReasons_11
        VnpayReasonKey.CODE_12 -> R.string.orders_vnpayReasons_12
        VnpayReasonKey.CODE_13 -> R.string.orders_vnpayReasons_13
        VnpayReasonKey.CODE_24 -> R.string.orders_vnpayReasons_24
        VnpayReasonKey.CODE_51 -> R.string.orders_vnpayReasons_51
        VnpayReasonKey.CODE_65 -> R.string.orders_vnpayReasons_65
        VnpayReasonKey.CODE_75 -> R.string.orders_vnpayReasons_75
        VnpayReasonKey.CODE_79 -> R.string.orders_vnpayReasons_79
        VnpayReasonKey.CODE_99 -> R.string.orders_vnpayReasons_99
        VnpayReasonKey.GENERIC -> R.string.orders_vnpayReasons_generic
    }
}

enum class HistoryActorKey {
    LEARNER,
    SYSTEM,
    VNPAY_IPN,
    ADMIN,
    UNKNOWN,
}

object HistoryActorMapper {
    fun map(actor: String?): HistoryActorKey = when (actor?.trim()?.uppercase()) {
        "LEARNER" -> HistoryActorKey.LEARNER
        "SYSTEM" -> HistoryActorKey.SYSTEM
        "VNPAY_IPN" -> HistoryActorKey.VNPAY_IPN
        "ADMIN" -> HistoryActorKey.ADMIN
        else -> HistoryActorKey.UNKNOWN
    }

    @StringRes
    fun stringRes(key: HistoryActorKey): Int = when (key) {
        HistoryActorKey.LEARNER -> R.string.orders_detail_history_actors_LEARNER
        HistoryActorKey.SYSTEM -> R.string.orders_detail_history_actors_SYSTEM
        HistoryActorKey.VNPAY_IPN -> R.string.orders_detail_history_actors_VNPAY_IPN
        HistoryActorKey.ADMIN -> R.string.orders_detail_history_actors_ADMIN
        HistoryActorKey.UNKNOWN -> R.string.orders_detail_history_actors_unknown
    }
}

enum class HistoryReasonKey {
    ORDER_CREATED,
    FREE_ORDER_PAID,
    PAYMENT_ATTEMPT_CREATED,
    REUSE_PENDING_PAYMENT,
    REPLACED_BY,
    CANCELLED_BY_USER,
    PAYMENT_SUCCEEDED,
    FULFILLED,
    EXCESS_AFTER_COUNTED,
    AUTO_EXCESS_AFTER_COUNTED,
    LATE_SUCCESS_PENDING_REVIEW,
    DOUBLE_CHARGE_REVIEW,
    DOUBLE_CHARGE_REPLACED_ORDER,
    LATE_PAYMENT_RECLAIMED,
    LATE_PAYMENT_REPLACED_RECLAIMED,
    LATE_PAYMENT_REPLACED_DOUBLE_CHARGE,
    EXPIRED,
    REFUNDED,
    UNKNOWN,
}

data class ParsedHistoryReason(
    val key: HistoryReasonKey,
    val ref: String,
)

object HistoryReasonParser {
    private val keyByName = HistoryReasonKey.entries
        .filter { it != HistoryReasonKey.UNKNOWN }
        .associateBy { it.name }

    fun parse(rawReason: String): ParsedHistoryReason {
        val trimmed = rawReason.trim()
        val colonIdx = trimmed.indexOf(':')
        val prefix = (if (colonIdx >= 0) trimmed.substring(0, colonIdx) else trimmed).trim().uppercase()
        val ref = if (colonIdx >= 0) trimmed.substring(colonIdx + 1).trim() else ""
        val key = keyByName[prefix] ?: HistoryReasonKey.UNKNOWN
        return ParsedHistoryReason(key = key, ref = ref)
    }

    fun format(rawReason: String, resources: Resources): String {
        val parsed = parse(rawReason)
        val ref = parsed.ref.ifEmpty { "—" }
        return when (parsed.key) {
            HistoryReasonKey.ORDER_CREATED -> resources.getString(R.string.orders_detail_history_reasons_ORDER_CREATED)
            HistoryReasonKey.FREE_ORDER_PAID -> resources.getString(R.string.orders_detail_history_reasons_FREE_ORDER_PAID)
            HistoryReasonKey.PAYMENT_ATTEMPT_CREATED -> resources.getString(R.string.orders_detail_history_reasons_PAYMENT_ATTEMPT_CREATED, ref)
            HistoryReasonKey.REUSE_PENDING_PAYMENT -> resources.getString(R.string.orders_detail_history_reasons_REUSE_PENDING_PAYMENT, ref)
            HistoryReasonKey.REPLACED_BY -> resources.getString(R.string.orders_detail_history_reasons_REPLACED_BY, ref)
            HistoryReasonKey.CANCELLED_BY_USER -> resources.getString(R.string.orders_detail_history_reasons_CANCELLED_BY_USER)
            HistoryReasonKey.PAYMENT_SUCCEEDED -> resources.getString(R.string.orders_detail_history_reasons_PAYMENT_SUCCEEDED, ref)
            HistoryReasonKey.FULFILLED -> resources.getString(R.string.orders_detail_history_reasons_FULFILLED)
            HistoryReasonKey.EXCESS_AFTER_COUNTED -> resources.getString(R.string.orders_detail_history_reasons_EXCESS_AFTER_COUNTED, ref)
            HistoryReasonKey.AUTO_EXCESS_AFTER_COUNTED -> resources.getString(R.string.orders_detail_history_reasons_AUTO_EXCESS_AFTER_COUNTED, ref)
            HistoryReasonKey.LATE_SUCCESS_PENDING_REVIEW -> resources.getString(R.string.orders_detail_history_reasons_LATE_SUCCESS_PENDING_REVIEW, ref)
            HistoryReasonKey.DOUBLE_CHARGE_REVIEW -> resources.getString(R.string.orders_detail_history_reasons_DOUBLE_CHARGE_REVIEW, ref)
            HistoryReasonKey.DOUBLE_CHARGE_REPLACED_ORDER -> resources.getString(R.string.orders_detail_history_reasons_DOUBLE_CHARGE_REPLACED_ORDER, ref)
            HistoryReasonKey.LATE_PAYMENT_RECLAIMED -> resources.getString(R.string.orders_detail_history_reasons_LATE_PAYMENT_RECLAIMED, ref)
            HistoryReasonKey.LATE_PAYMENT_REPLACED_RECLAIMED -> resources.getString(R.string.orders_detail_history_reasons_LATE_PAYMENT_REPLACED_RECLAIMED, ref)
            HistoryReasonKey.LATE_PAYMENT_REPLACED_DOUBLE_CHARGE -> resources.getString(R.string.orders_detail_history_reasons_LATE_PAYMENT_REPLACED_DOUBLE_CHARGE, ref)
            HistoryReasonKey.EXPIRED -> resources.getString(R.string.orders_detail_history_reasons_EXPIRED)
            HistoryReasonKey.REFUNDED -> resources.getString(R.string.orders_detail_history_reasons_REFUNDED)
            HistoryReasonKey.UNKNOWN -> resources.getString(R.string.orders_detail_history_reasons_unknown)
        }
    }
}

data class BenefitsSnapshot(
    val aiExplanation: Boolean,
    val strengthWeakness: Boolean,
    val practice: Boolean,
    val note: String? = null,
)

object PaymentFormatters {
    val VIETNAM_ZONE_ID: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val DATE_TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(VIETNAM_ZONE_ID)
    private val TIME_ONLY_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm").withZone(VIETNAM_ZONE_ID)

    fun formatDateTimeVn(dateTime: OffsetDateTime): String =
        DATE_TIME_FORMATTER.format(dateTime.toInstant())

    fun formatTimeOnlyVn(dateTime: OffsetDateTime): String =
        TIME_ONLY_FORMATTER.format(dateTime.toInstant())

    fun formatEpochTimeOnlyVn(epochMillis: Long): String =
        TIME_ONLY_FORMATTER.format(Instant.ofEpochMilli(epochMillis))

    fun formatNumber(value: Long, locale: AppLocale): String {
        val symbols = DecimalFormatSymbols(locale.toJavaLocale())
        val formatter = DecimalFormat("#,###", symbols)
        return formatter.format(value)
    }

    fun formatPrice(amount: Long, locale: AppLocale, resources: Resources): String =
        resources.getString(R.string.payment_price, formatNumber(amount, locale))

    fun formatIntRange(range: IntRange?, locale: AppLocale): String? {
        if (range == null) return null
        return if (range.min == range.max) {
            formatNumber(range.min.toLong(), locale)
        } else {
            "${formatNumber(range.min.toLong(), locale)}–${formatNumber(range.max.toLong(), locale)}"
        }
    }

    fun formatDecimalRange(range: DecimalRange?, locale: AppLocale): String? {
        if (range == null) return null
        val minStr = formatBigDecimal(range.min, locale)
        val maxStr = formatBigDecimal(range.max, locale)
        return if (range.min.compareTo(range.max) == 0) minStr else "$minStr–$maxStr"
    }

    private fun formatBigDecimal(value: BigDecimal, locale: AppLocale): String {
        val stripped = value.stripTrailingZeros()
        val symbols = DecimalFormatSymbols(locale.toJavaLocale())
        val formatter = DecimalFormat("#,##0.##", symbols)
        return formatter.format(stripped)
    }

    fun formatSupportSummary(contact: SupportContact): String? {
        val channels = listOfNotNull(contact.normalizedPhone, contact.normalizedEmail)
            .joinToString(" · ")
        if (channels.isEmpty()) return contact.normalizedHours
        val hours = contact.normalizedHours
        return if (hours != null) "$channels ($hours)" else channels
    }

    fun pickLocalizedName(nameVi: String, nameEn: String, locale: AppLocale): String =
        when (locale) {
            AppLocale.VI -> nameVi.ifBlank { nameEn }
            AppLocale.EN -> nameEn.ifBlank { nameVi }
        }

    fun readBenefitsSnapshot(raw: Map<String, JsonElement>): BenefitsSnapshot {
        fun boolField(key: String): Boolean =
            (raw[key] as? JsonPrimitive)?.booleanOrNull == true

        val note = (raw["note"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        return BenefitsSnapshot(
            aiExplanation = boolField("aiExplanation"),
            strengthWeakness = boolField("strengthWeakness"),
            practice = boolField("practice"),
            note = note,
        )
    }

    fun formatResultView(resultView: String, resources: Resources): String = when (resultView) {
        "SCORE" -> resources.getString(R.string.payment_detail_resultViews_SCORE)
        "SCORE_AND_ANSWERS" -> resources.getString(R.string.payment_detail_resultViews_SCORE_AND_ANSWERS)
        "FULL_SOLUTION" -> resources.getString(R.string.payment_detail_resultViews_FULL_SOLUTION)
        else -> resultView
    }

    fun formatStateName(rawState: String, resources: Resources): String = when (rawState) {
        "PENDING_PAYMENT" -> resources.getString(R.string.orders_states_PENDING_PAYMENT)
        "PAID" -> resources.getString(R.string.orders_states_PAID)
        "FULFILLED" -> resources.getString(R.string.orders_states_FULFILLED)
        "EXPIRED" -> resources.getString(R.string.orders_states_EXPIRED)
        "REPLACED" -> resources.getString(R.string.orders_states_REPLACED)
        "CANCELLED" -> resources.getString(R.string.orders_states_CANCELLED)
        "REFUNDED" -> resources.getString(R.string.orders_states_REFUNDED)
        else -> rawState
    }

    fun orderStateTone(state: OrderDetail.State): PaymentStatusTone = when (state) {
        OrderDetail.State.PENDING_PAYMENT -> PaymentStatusTone.Warning
        OrderDetail.State.PAID -> PaymentStatusTone.Warning
        OrderDetail.State.FULFILLED -> PaymentStatusTone.Success
        OrderDetail.State.EXPIRED -> PaymentStatusTone.Neutral
        OrderDetail.State.REPLACED -> PaymentStatusTone.Neutral
        OrderDetail.State.CANCELLED -> PaymentStatusTone.Neutral
        OrderDetail.State.REFUNDED -> PaymentStatusTone.Info
    }

    fun orderSummaryStateTone(state: OrderSummary.State): PaymentStatusTone = when (state) {
        OrderSummary.State.PENDING_PAYMENT -> PaymentStatusTone.Warning
        OrderSummary.State.PAID -> PaymentStatusTone.Warning
        OrderSummary.State.FULFILLED -> PaymentStatusTone.Success
        OrderSummary.State.EXPIRED -> PaymentStatusTone.Neutral
        OrderSummary.State.REPLACED -> PaymentStatusTone.Neutral
        OrderSummary.State.CANCELLED -> PaymentStatusTone.Neutral
        OrderSummary.State.REFUNDED -> PaymentStatusTone.Info
    }

    fun paymentStateTone(state: OrderPaymentView.State): PaymentStatusTone = when (state) {
        OrderPaymentView.State.CREATED -> PaymentStatusTone.Neutral
        OrderPaymentView.State.SUCCEEDED -> PaymentStatusTone.Success
        OrderPaymentView.State.FAILED -> PaymentStatusTone.Danger
        OrderPaymentView.State.UNKNOWN -> PaymentStatusTone.Warning
    }

    @StringRes
    fun paymentStateStringRes(state: OrderPaymentView.State): Int = when (state) {
        OrderPaymentView.State.CREATED -> R.string.orders_detail_payments_states_CREATED
        OrderPaymentView.State.SUCCEEDED -> R.string.orders_detail_payments_states_SUCCEEDED
        OrderPaymentView.State.FAILED -> R.string.orders_detail_payments_states_FAILED
        OrderPaymentView.State.UNKNOWN -> R.string.orders_detail_payments_states_UNKNOWN
    }

    fun latestPayment(order: OrderDetail): OrderPaymentView? =
        order.payments.maxByOrNull { it.attemptSeq }
}
