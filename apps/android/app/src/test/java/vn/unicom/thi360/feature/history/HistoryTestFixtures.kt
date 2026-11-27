package vn.unicom.thi360.feature.history

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AttemptChoiceSummary
import vn.unicom.thi360.core.network.generated.model.AttemptHistoryItem
import vn.unicom.thi360.core.network.generated.model.CatalogOptionResponse
import vn.unicom.thi360.core.network.generated.model.PageResponseAttemptHistoryItem
import vn.unicom.thi360.testing.ContractFixtureIndex

internal object HistoryTestFixtures {
    private val json = Serializer.kotlinxSerializationJson

    fun loadDefaultPage(): PageResponseAttemptHistoryItem {
        val entry = ContractFixtureIndex.find("listHistory", 200, "attempts-list-200.json")
        return json.decodeFromString(
            PageResponseAttemptHistoryItem.serializer(),
            entry.file.readText(Charsets.UTF_8),
        )
    }

    fun loadExamCatalogOptions(): List<CatalogOptionResponse> {
        val entry = ContractFixtureIndex.find("getOptions", 200, "exam")
        return json.decodeFromString(
            ListSerializer(CatalogOptionResponse.serializer()),
            entry.file.readText(Charsets.UTF_8),
        )
    }

    fun loadSubjectCatalogOptions(): List<CatalogOptionResponse> {
        val entry = ContractFixtureIndex.find("getOptions", 200, "subject")
        return json.decodeFromString(
            ListSerializer(CatalogOptionResponse.serializer()),
            entry.file.readText(Charsets.UTF_8),
        )
    }

    fun encodePage(
        items: List<AttemptHistoryItem>,
        page: Int = 0,
        size: Int = HistoryFilter.HISTORY_PAGE_SIZE,
        totalItems: Long = items.size.toLong(),
        totalPages: Int = if (totalItems == 0L) 0 else ((totalItems + size - 1) / size).toInt(),
    ): String = json.encodeToString(
        PageResponseAttemptHistoryItem.serializer(),
        PageResponseAttemptHistoryItem(
            items = items,
            page = page,
            propertySize = size,
            totalItems = totalItems,
            totalPages = totalPages,
        ),
    )

    fun encodeCatalogOptions(options: List<CatalogOptionResponse>): String =
        json.encodeToString(ListSerializer(CatalogOptionResponse.serializer()), options)

    fun templateInProgress(): AttemptHistoryItem =
        loadDefaultPage().items.first { it.state == AttemptHistoryItem.State.IN_PROGRESS }

    fun templateScored(): AttemptHistoryItem =
        loadDefaultPage().items.first { it.state == AttemptHistoryItem.State.SCORED }

    fun makeAttempt(
        idSeed: String,
        examName: String = "Đề HSA $idSeed",
        examKindCode: String = "HSA",
        scope: String = "FULL",
        partCode: String? = null,
        subjectCode: String? = null,
        choices: Map<String, AttemptChoiceSummary> = emptyMap(),
        attemptNo: Int = 1,
        startedAt: OffsetDateTime? = OffsetDateTime.parse("2026-11-20T02:05:00Z"),
        state: AttemptHistoryItem.State = AttemptHistoryItem.State.SCORED,
        regrading: Boolean = false,
        displayScore: BigDecimal? = BigDecimal("82.5"),
        rawScore: BigDecimal? = BigDecimal("82.5"),
        scaleMax: BigDecimal? = BigDecimal("100"),
        maxScore: BigDecimal? = BigDecimal("100"),
        action: AttemptHistoryItem.Action = AttemptHistoryItem.Action.VIEW_RESULT,
        faultFlag: String? = null,
        compensated: Boolean = false,
    ): AttemptHistoryItem = templateScored().copy(
        attemptId = UUID.nameUUIDFromBytes("attempt:$idSeed".toByteArray()),
        examId = UUID.nameUUIDFromBytes("exam:$idSeed".toByteArray()),
        examName = examName,
        examKindCode = examKindCode,
        scope = scope,
        partCode = partCode,
        subjectCode = subjectCode,
        choices = choices,
        attemptNo = attemptNo,
        startedAt = startedAt,
        state = state,
        regrading = regrading,
        displayScore = displayScore,
        rawScore = rawScore,
        scaleMax = scaleMax,
        maxScore = maxScore,
        action = action,
        faultFlag = faultFlag,
        compensated = compensated,
    )
}
