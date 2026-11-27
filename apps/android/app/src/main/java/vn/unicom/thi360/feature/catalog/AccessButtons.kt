package vn.unicom.thi360.feature.catalog

enum class ExamAccessStateValue {
    IN_PROGRESS,
    CAN_START,
    CHECKING,
    EXHAUSTED,
    NONE;

    companion object {
        fun fromApi(raw: String?): ExamAccessStateValue =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

data class BundleExamAccessRow(
    val examId: String,
    val code: String,
    val name: String,
    val state: ExamAccessStateValue,
    val attemptsLeft: Int,
    val entitlementId: String?,
    val activeAttemptId: String?,
    val hasPendingFulfillment: Boolean,
    val pendingOrderCode: String?,
)

data class ExamAccessRow(
    val code: String,
    val state: ExamAccessStateValue,
    val attemptsLeft: Int = 0,
    val entitlementId: String? = null,
    val activeAttemptId: String? = null,
    val hasPendingFulfillment: Boolean = false,
    val pendingOrderCode: String? = null,
    val items: List<BundleExamAccessRow> = emptyList(),
) {
    companion object {
        fun none(code: String): ExamAccessRow = ExamAccessRow(
            code = code,
            state = ExamAccessStateValue.NONE,
        )
    }
}

sealed interface AccessCell {
    data object Loading : AccessCell
    data class Ready(val row: ExamAccessRow) : AccessCell
    data object Failed : AccessCell
}

sealed interface AccessButton {
    data class Continue(val attemptId: String?) : AccessButton
    data class Enter(val entitlementId: String?) : AccessButton
    data class Buy(
        val code: String,
        val seenVersionNo: Int,
        val seenPrice: Long,
        val isBundle: Boolean,
    ) : AccessButton
    data class BuyMore(
        val code: String,
        val seenVersionNo: Int,
        val seenPrice: Long,
    ) : AccessButton
    data class BuyAgain(
        val code: String,
        val seenVersionNo: Int,
        val seenPrice: Long,
    ) : AccessButton
    data class Checking(val orderCode: String?) : AccessButton
}

sealed interface AccessNote {
    data object ContinueInProgress : AccessNote
    data class AttemptsLeft(val count: Int) : AccessNote
    data object Exhausted : AccessNote
    data class PendingTopUp(val orderCode: String) : AccessNote
    data class CheckingOrder(val orderCode: String) : AccessNote
}

data class AccessPanelModel(
    val primary: AccessButton,
    val secondary: AccessButton? = null,
    val leadNote: AccessNote? = null,
    val tailNote: AccessNote? = null,
    val showViewDetail: Boolean = false,
)

object AccessButtons {
    const val MAX_CODES_PER_REQUEST = 48

    fun chunkCodes(codes: List<String>, chunkSize: Int = MAX_CODES_PER_REQUEST): List<List<String>> {
        require(chunkSize > 0) { "chunkSize must be positive" }
        val normalized = codes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return if (normalized.isEmpty()) emptyList() else normalized.chunked(chunkSize)
    }

    fun forCard(
        row: ExamAccessRow,
        code: String,
        seenVersionNo: Int,
        seenPrice: Long,
        isBundle: Boolean,
    ): AccessPanelModel = buildModel(
        row = row,
        code = code,
        seenVersionNo = seenVersionNo,
        seenPrice = seenPrice,
        isBundle = isBundle,
        isPricePanel = false,
    )

    fun forPricePanel(
        row: ExamAccessRow,
        code: String,
        seenVersionNo: Int,
        seenPrice: Long,
    ): AccessPanelModel = buildModel(
        row = row,
        code = code,
        seenVersionNo = seenVersionNo,
        seenPrice = seenPrice,
        isBundle = false,
        isPricePanel = true,
    )

    private fun buildModel(
        row: ExamAccessRow,
        code: String,
        seenVersionNo: Int,
        seenPrice: Long,
        isBundle: Boolean,
        isPricePanel: Boolean,
    ): AccessPanelModel {
        val showViewDetail = !isPricePanel
        val buyMore = if (!row.hasPendingFulfillment) {
            AccessButton.BuyMore(code = code, seenVersionNo = seenVersionNo, seenPrice = seenPrice)
        } else {
            null
        }
        val pendingTopUpNote = if (row.hasPendingFulfillment) {
            AccessNote.PendingTopUp(orderCode = row.pendingOrderCode.orEmpty())
        } else {
            null
        }

        return when (row.state) {
            ExamAccessStateValue.IN_PROGRESS -> AccessPanelModel(
                primary = AccessButton.Continue(row.activeAttemptId),
                secondary = buyMore,
                leadNote = AccessNote.ContinueInProgress,
                tailNote = pendingTopUpNote,
                showViewDetail = showViewDetail,
            )

            ExamAccessStateValue.CAN_START -> AccessPanelModel(
                primary = AccessButton.Enter(row.entitlementId),
                secondary = buyMore,
                leadNote = AccessNote.AttemptsLeft(row.attemptsLeft),
                tailNote = pendingTopUpNote,
                showViewDetail = showViewDetail,
            )

            ExamAccessStateValue.EXHAUSTED -> if (isPricePanel) {
                AccessPanelModel(
                    primary = AccessButton.BuyAgain(
                        code = code,
                        seenVersionNo = seenVersionNo,
                        seenPrice = seenPrice,
                    ),
                    leadNote = AccessNote.Exhausted,
                    showViewDetail = false,
                )
            } else {
                AccessPanelModel(
                    primary = AccessButton.Buy(
                        code = code,
                        seenVersionNo = seenVersionNo,
                        seenPrice = seenPrice,
                        isBundle = isBundle,
                    ),
                    showViewDetail = true,
                )
            }

            ExamAccessStateValue.CHECKING -> {
                val orderCode = row.pendingOrderCode?.trim()?.takeIf { it.isNotEmpty() }
                AccessPanelModel(
                    primary = AccessButton.Checking(orderCode = orderCode),
                    tailNote = orderCode?.let { AccessNote.CheckingOrder(it) },
                    showViewDetail = showViewDetail,
                )
            }

            ExamAccessStateValue.NONE -> AccessPanelModel(
                primary = AccessButton.Buy(
                    code = code,
                    seenVersionNo = seenVersionNo,
                    seenPrice = seenPrice,
                    isBundle = isBundle,
                ),
                showViewDetail = showViewDetail,
            )
        }
    }
}
