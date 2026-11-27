package vn.unicom.thi360.feature.myexams

import java.util.UUID
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.ExamKindRef
import vn.unicom.thi360.core.network.generated.model.IntRange as ApiIntRange
import vn.unicom.thi360.core.network.generated.model.LocalizedName
import vn.unicom.thi360.core.network.generated.model.MeResponse
import vn.unicom.thi360.core.network.generated.model.MyExamBundleRef
import vn.unicom.thi360.core.network.generated.model.MyExamCard
import vn.unicom.thi360.core.network.generated.model.MyExamsResponse
import vn.unicom.thi360.testing.ContractFixtureIndex

internal object MyExamsTestFixtures {
    private val json = Serializer.kotlinxSerializationJson

    fun loadDefaultResponse(): MyExamsResponse {
        val entry = ContractFixtureIndex.find("listMyExams", 200, "my-exams-list-200.json")
        return json.decodeFromString(
            MyExamsResponse.serializer(),
            entry.file.readText(Charsets.UTF_8),
        )
    }

    fun loadEmptyResponse(): MyExamsResponse {
        val entry = ContractFixtureIndex.find("listMyExams", 200, "empty")
        return json.decodeFromString(
            MyExamsResponse.serializer(),
            entry.file.readText(Charsets.UTF_8),
        )
    }

    fun loadDefaultMe(): MeResponse {
        val entry = ContractFixtureIndex.find("me", 200)
        return json.decodeFromString(
            MeResponse.serializer(),
            entry.file.readText(Charsets.UTF_8),
        )
    }

    fun encodeResponse(items: List<MyExamCard>): String =
        json.encodeToString(MyExamsResponse.serializer(), MyExamsResponse(items = items))

    fun encodeMe(me: MeResponse): String =
        json.encodeToString(MeResponse.serializer(), me)

    fun templateCheckingCard(): MyExamCard =
        loadDefaultResponse().items.first { it.state == MyExamCard.State.CHECKING }

    fun templateClassCard(): MyExamCard =
        loadDefaultResponse().items.first { it.entitlementSource == ENTITLEMENT_SOURCE_CLASS }

    fun templateExhaustedCard(): MyExamCard =
        loadDefaultResponse().items.first { it.state == MyExamCard.State.EXHAUSTED }

    fun templateInProgressCard(): MyExamCard =
        loadDefaultResponse().items.first { it.state == MyExamCard.State.IN_PROGRESS }

    fun templateBundledCard(): MyExamCard =
        loadDefaultResponse().items.first { !it.bundles.isNullOrEmpty() }

    fun templateBundleRef(): MyExamBundleRef =
        templateBundledCard().bundles!!.first()

    fun makeCard(
        code: String,
        name: String = "Đề $code",
        entitlementId: UUID? = UUID.nameUUIDFromBytes("ent:$code".toByteArray()),
        state: MyExamCard.State = MyExamCard.State.CAN_START,
        saleState: MyExamCard.SaleState = MyExamCard.SaleState.ON_SALE,
        format: String = "FULL",
        scopeLabel: LocalizedName? = null,
        buildMode: String = "STANDARD",
        examKind: ExamKindRef = templateBundledCard().examKind,
        questionCount: ApiIntRange? = templateBundledCard().questionCount,
        durationMinutes: ApiIntRange? = templateBundledCard().durationMinutes,
        attemptsLeft: Int = 2,
        attemptsTotal: Int = 2,
        activeAttemptId: UUID? = null,
        resultAttemptId: UUID? = null,
        hasPendingFulfillment: Boolean = false,
        pendingOrderCode: String? = null,
        actions: List<MyExamCard.Actions> = listOf(MyExamCard.Actions.START),
        bundles: List<MyExamBundleRef> = emptyList(),
        entitlementSource: String? = "PURCHASE",
        className: String? = null,
        coverUrl: String? = null,
    ): MyExamCard = templateBundledCard().copy(
        entitlementId = entitlementId,
        code = code,
        name = name,
        examKind = examKind,
        format = format,
        scopeLabel = scopeLabel,
        buildMode = buildMode,
        coverUrl = coverUrl,
        questionCount = questionCount,
        durationMinutes = durationMinutes,
        attemptsLeft = attemptsLeft,
        attemptsTotal = attemptsTotal,
        saleState = saleState,
        state = state,
        activeAttemptId = activeAttemptId,
        resultAttemptId = resultAttemptId,
        hasPendingFulfillment = hasPendingFulfillment,
        pendingOrderCode = pendingOrderCode,
        actions = actions,
        bundles = bundles,
        entitlementSource = entitlementSource,
        className = className,
    )

    fun makeBundle(
        idSeed: String,
        code: String,
        name: String,
    ): MyExamBundleRef = templateBundleRef().copy(
        bundleId = UUID.nameUUIDFromBytes("bundle:$idSeed".toByteArray()),
        code = code,
        name = name,
    )
}
