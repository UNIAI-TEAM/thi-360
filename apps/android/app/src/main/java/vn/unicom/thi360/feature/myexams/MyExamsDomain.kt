package vn.unicom.thi360.feature.myexams

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.generated.model.ExamKindRef
import vn.unicom.thi360.core.network.generated.model.IntRange as ApiIntRange
import vn.unicom.thi360.core.network.generated.model.LocalizedName
import vn.unicom.thi360.core.network.generated.model.MyExamBundleRef
import vn.unicom.thi360.core.network.generated.model.MyExamCard
import vn.unicom.thi360.core.preferences.AppLocale

val DISPLAY_TIME_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
private val MY_EXAMS_STALE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

const val ENTITLEMENT_SOURCE_CLASS = "CLASS"
const val BUILD_MODE_REVIEW = "REVIEW"

data class BundleCrossRef(
    val examCode: String,
    val examName: String,
    val primaryBundle: MyExamBundleRef,
)

data class BundleGroup(
    val ref: MyExamBundleRef,
    val cards: List<MyExamCard>,
    val crossRefs: List<BundleCrossRef>,
) {
    val totalItemCount: Int
        get() = cards.size + crossRefs.size
}

data class GroupedMyExams(
    val checking: List<MyExamCard>,
    val bundleGroups: List<BundleGroup>,
    val standalone: List<MyExamCard>,
    val count: Int,
)

object MyExamsLayout {
    fun group(cards: List<MyExamCard>): GroupedMyExams {
        val checking = mutableListOf<MyExamCard>()
        val standalone = mutableListOf<MyExamCard>()

        data class MutableBundleGroup(
            val ref: MyExamBundleRef,
            val cards: MutableList<MyExamCard> = mutableListOf(),
            val crossRefs: MutableList<BundleCrossRef> = mutableListOf(),
        )

        val groupMap = LinkedHashMap<UUID, MutableBundleGroup>()

        fun ensureGroup(ref: MyExamBundleRef): MutableBundleGroup =
            groupMap.getOrPut(ref.bundleId) { MutableBundleGroup(ref) }

        for (card in cards) {
            if (card.state == MyExamCard.State.CHECKING) {
                checking.add(card)
                continue
            }
            val bundles = card.bundles.orEmpty()
            if (bundles.isEmpty()) {
                standalone.add(card)
                continue
            }
            val primary = bundles.first()
            ensureGroup(primary).cards.add(card)

            val seenBundleIds = HashSet<UUID>()
            seenBundleIds.add(primary.bundleId)
            for (secondary in bundles.drop(1)) {
                if (seenBundleIds.add(secondary.bundleId)) {
                    ensureGroup(secondary).crossRefs.add(
                        BundleCrossRef(
                            examCode = card.code,
                            examName = card.name,
                            primaryBundle = primary,
                        ),
                    )
                }
            }
        }

        return GroupedMyExams(
            checking = checking,
            bundleGroups = groupMap.values.map {
                BundleGroup(
                    ref = it.ref,
                    cards = it.cards.toList(),
                    crossRefs = it.crossRefs.toList(),
                )
            },
            standalone = standalone,
            count = cards.size,
        )
    }
}

data class CardButtons(
    val continueAttemptId: String?,
    val startEntitlementId: String?,
    val showRebuy: Boolean,
    val resultAttemptId: String?,
    val showHistory: Boolean,
    val showBuyMore: Boolean,
) {
    val hasSecondaryRow: Boolean
        get() = resultAttemptId != null || showHistory

    companion object {
        fun of(card: MyExamCard): CardButtons {
            val actions = card.actions
            val isClassAssigned = card.entitlementSource == ENTITLEMENT_SOURCE_CLASS
            val isOnSale = card.saleState == MyExamCard.SaleState.ON_SALE
            val canPurchase = !isClassAssigned && isOnSale && !card.hasPendingFulfillment

            val continueAttemptId = if (MyExamCard.Actions.CONTINUE in actions) {
                card.activeAttemptId?.toString()
            } else {
                null
            }
            val startEntitlementId = if (continueAttemptId == null && MyExamCard.Actions.START in actions) {
                card.entitlementId?.toString()
            } else {
                null
            }
            val resultAttemptId = if (MyExamCard.Actions.VIEW_RESULT in actions) {
                card.resultAttemptId?.toString()
            } else {
                null
            }
            val showHistory = MyExamCard.Actions.HISTORY in actions

            return CardButtons(
                continueAttemptId = continueAttemptId,
                startEntitlementId = startEntitlementId,
                showRebuy = canPurchase && MyExamCard.Actions.REBUY in actions,
                resultAttemptId = resultAttemptId,
                showHistory = showHistory,
                showBuyMore = canPurchase && MyExamCard.Actions.BUY_MORE in actions,
            )
        }
    }
}

enum class CardNote {
    LAST_ATTEMPT,
    CLASS_EXHAUSTED,
    CLASS_EXHAUSTED_WITH_HISTORY,
    DISCONTINUED_EXHAUSTED,
    DISCONTINUED_EXHAUSTED_WITH_HISTORY,
    DISCONTINUED_CAN_START,
}

data class CardNotes(
    val middleNotes: List<CardNote>,
    val pendingTopUpOrderCode: String?,
) {
    companion object {
        fun of(card: MyExamCard): CardNotes {
            val buttons = CardButtons.of(card)
            val isClassAssigned = card.entitlementSource == ENTITLEMENT_SOURCE_CLASS
            val isOnSale = card.saleState == MyExamCard.SaleState.ON_SALE
            val hasHistoryOrResult = buttons.hasSecondaryRow

            val middle = buildList {
                if (card.state == MyExamCard.State.IN_PROGRESS && card.attemptsLeft == 0) {
                    add(CardNote.LAST_ATTEMPT)
                }
                if (isClassAssigned && card.state == MyExamCard.State.EXHAUSTED) {
                    add(
                        if (hasHistoryOrResult) {
                            CardNote.CLASS_EXHAUSTED_WITH_HISTORY
                        } else {
                            CardNote.CLASS_EXHAUSTED
                        },
                    )
                }
                if (!isClassAssigned && !isOnSale && card.state == MyExamCard.State.EXHAUSTED) {
                    add(
                        if (hasHistoryOrResult) {
                            CardNote.DISCONTINUED_EXHAUSTED_WITH_HISTORY
                        } else {
                            CardNote.DISCONTINUED_EXHAUSTED
                        },
                    )
                }
                if (!isClassAssigned && !isOnSale &&
                    (card.state == MyExamCard.State.CAN_START || card.state == MyExamCard.State.IN_PROGRESS)
                ) {
                    add(CardNote.DISCONTINUED_CAN_START)
                }
            }

            val pendingTopUpOrderCode = if (card.hasPendingFulfillment) {
                card.pendingOrderCode?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }

            return CardNotes(
                middleNotes = middle,
                pendingTopUpOrderCode = pendingTopUpOrderCode,
            )
        }
    }
}

sealed interface CheckingMessage {
    data class WithOrderAndContact(val orderCode: String, val contact: String) : CheckingMessage
    data class WithOrderNoContact(val orderCode: String) : CheckingMessage
    data class NoOrderWithContact(val contact: String) : CheckingMessage
    data object NoOrderNoContact : CheckingMessage

    val orderCodeOrNull: String?
        get() = when (this) {
            is WithOrderAndContact -> orderCode
            is WithOrderNoContact -> orderCode
            is NoOrderWithContact, NoOrderNoContact -> null
        }

    companion object {
        fun formatSupportContact(support: SupportContact): String? {
            val channels = listOfNotNull(support.normalizedPhone, support.normalizedEmail)
                .joinToString(" · ")
            val hours = support.normalizedHours
            return when {
                channels.isEmpty() -> hours
                hours != null -> "$channels ($hours)"
                else -> channels
            }
        }

        fun of(card: MyExamCard, support: SupportContact): CheckingMessage {
            val orderCode = card.pendingOrderCode?.trim()?.takeIf { it.isNotEmpty() }
            val contact = formatSupportContact(support)
            return when {
                orderCode != null && contact != null -> WithOrderAndContact(orderCode, contact)
                orderCode != null -> WithOrderNoContact(orderCode)
                contact != null -> NoOrderWithContact(contact)
                else -> NoOrderNoContact
            }
        }
    }
}

fun resolveGreetingName(fullName: String?, username: String?): String =
    fullName?.trim()?.takeIf { it.isNotEmpty() }
        ?: username?.trim()?.takeIf { it.isNotEmpty() }
        ?: ""

fun pickExamKindName(examKind: ExamKindRef, locale: AppLocale): String =
    pickLocalizedOrCode(examKind.nameVi, examKind.nameEn, examKind.code, locale)

fun pickScopeLabel(scopeLabel: LocalizedName?, locale: AppLocale): String? {
    if (scopeLabel == null) return null
    val vi = scopeLabel.nameVi.trim()
    val en = scopeLabel.nameEn.trim()
    return when (locale) {
        AppLocale.EN -> en.ifEmpty { vi }.takeIf { it.isNotEmpty() }
        AppLocale.VI -> vi.ifEmpty { en }.takeIf { it.isNotEmpty() }
    }
}

fun pickLocalizedOrCode(nameVi: String?, nameEn: String?, code: String, locale: AppLocale): String {
    val vi = nameVi?.trim().orEmpty()
    val en = nameEn?.trim().orEmpty()
    val picked = when (locale) {
        AppLocale.EN -> en.ifEmpty { vi }
        AppLocale.VI -> vi.ifEmpty { en }
    }
    return picked.ifEmpty { code }
}

fun formatRangeText(range: ApiIntRange?): String? {
    if (range == null) return null
    return if (range.min == range.max) range.min.toString() else "${range.min}–${range.max}"
}

fun formatMyExamsStaleTime(epochMillis: Long?): String? {
    if (epochMillis == null || epochMillis <= 0L) return null
    return Instant.ofEpochMilli(epochMillis)
        .atZone(DISPLAY_TIME_ZONE)
        .format(MY_EXAMS_STALE_TIME_FORMATTER)
}

fun myExamStableKey(card: MyExamCard): String =
    card.entitlementId?.toString()
        ?: "checking:${card.pendingOrderCode?.trim().orEmpty()}:${card.code}"

fun buildUniqueCheckingKeys(cards: List<MyExamCard>): List<String> {
    val seenCounts = HashMap<String, Int>(cards.size)
    return cards.map { card ->
        val baseKey = myExamStableKey(card)
        val occurrence = seenCounts[baseKey] ?: 0
        seenCounts[baseKey] = occurrence + 1
        if (occurrence == 0) baseKey else "$baseKey#dup$occurrence"
    }
}

class NavigationClickGuard(
    private val minIntervalMillis: Long = DEFAULT_GUARD_INTERVAL_MS,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var lastDispatchedAtMillis: Long = Long.MIN_VALUE

    fun run(action: () -> Unit) {
        val now = clockMillis()
        if (lastDispatchedAtMillis != Long.MIN_VALUE && (now - lastDispatchedAtMillis) < minIntervalMillis) {
            return
        }
        lastDispatchedAtMillis = now
        action()
    }

    companion object {
        const val DEFAULT_GUARD_INTERVAL_MS: Long = 500L
    }
}
