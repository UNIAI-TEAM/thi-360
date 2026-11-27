package vn.unicom.thi360.feature.prepare

import java.util.UUID
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AttemptPrepareResponse
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.ChoiceOptionView
import vn.unicom.thi360.core.network.generated.model.ChoiceSubjectView
import vn.unicom.thi360.core.network.generated.model.GrantSourceView
import vn.unicom.thi360.core.network.generated.model.ListeningPolicyView
import vn.unicom.thi360.core.network.generated.model.PreChoiceView
import vn.unicom.thi360.core.network.generated.model.StartAttemptResponse
import vn.unicom.thi360.testing.ContractFixtureIndex

internal object PrepareTestFixtures {
    private val json = Serializer.kotlinxSerializationJson

    fun readPrepareRaw(variant: String, statusCode: Int = 200): String =
        ContractFixtureIndex.find("prepare", statusCode, variant).file.readText(Charsets.UTF_8)

    fun loadPrepare(variant: String): AttemptPrepareResponse =
        json.decodeFromString(AttemptPrepareResponse.serializer(), readPrepareRaw(variant, 200))

    fun encodePrepare(response: AttemptPrepareResponse): String =
        json.encodeToString(AttemptPrepareResponse.serializer(), response)

    fun readStartRaw(statusCode: Int, variant: String): String =
        ContractFixtureIndex.find("start", statusCode, variant).file.readText(Charsets.UTF_8)

    fun loadStart(statusCode: Int, variant: String): StartAttemptResponse =
        json.decodeFromString(StartAttemptResponse.serializer(), readStartRaw(statusCode, variant))

    fun encodeStart(response: StartAttemptResponse): String =
        json.encodeToString(StartAttemptResponse.serializer(), response)

    fun readAttemptStateRaw(statusCode: Int, variant: String): String =
        ContractFixtureIndex.find("getState", statusCode, variant).file.readText(Charsets.UTF_8)

    fun loadAttemptState(variant: String): AttemptStateResponse =
        json.decodeFromString(AttemptStateResponse.serializer(), readAttemptStateRaw(200, variant))

    fun encodeAttemptState(response: AttemptStateResponse): String =
        json.encodeToString(AttemptStateResponse.serializer(), response)

    fun readRateLimitedRaw(): String =
        ContractFixtureIndex.find("saveAnswers", 429, "rate-limited").file.readText(Charsets.UTF_8)

    fun prepareWithSubjectChoice(
        entitlementId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000001"),
        preselectedCode: String? = null,
    ): AttemptPrepareResponse {
        val base = loadPrepare("per-part")
        val afterChoice = base.afterStartChoices.first()
        val preChoice = PreChoiceView(
            partCode = afterChoice.partCode,
            kind = afterChoice.kind,
            preselectedCode = preselectedCode,
            options = afterChoice.options,
        )
        return base.copy(
            entitlementId = entitlementId,
            preChoice = preChoice,
            afterStartChoices = emptyList(),
        )
    }

    fun prepareWithZeroPreChoiceOptions(
        entitlementId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000001"),
    ): AttemptPrepareResponse {
        val base = loadPrepare("pre-choice")
        val emptyPreChoice = base.preChoice!!.copy(
            preselectedCode = null,
            options = emptyList(),
        )
        return base.copy(
            entitlementId = entitlementId,
            preChoice = emptyPreChoice,
        )
    }

    fun prepareWithListeningAndReview(
        entitlementId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000001"),
        maxPlays: Int? = null,
        seekable: Boolean = false,
    ): AttemptPrepareResponse {
        val base = loadPrepare("pre-choice")
        return base.copy(
            entitlementId = entitlementId,
            buildMode = PREPARE_BUILD_MODE_REVIEW,
            listeningPolicy = ListeningPolicyView(maxPlays = maxPlays, seekable = seekable),
            answerFormats = listOf("SINGLE_CHOICE", "TRUE_FALSE", "FUTURE_UNKNOWN_FMT"),
            instructions = "<p>Đọc kỹ <strong>đề bài</strong> trước khi làm.</p><img src=\"https://example.invalid/blocked.png\" alt=\"blocked\" /><ul><li>Không mở tab khác</li></ul>",
        )
    }

    fun prepareExhaustedVariant(
        source: GrantSourceView.Source?,
        servingStatus: String,
    ): AttemptPrepareResponse {
        val base = loadPrepare("exhausted-purchase")
        val grant = source?.let { src ->
            GrantSourceView(
                source = src,
                orderCode = if (src == GrantSourceView.Source.PURCHASE) "DH261126000001" else null,
                grantedAt = loadPrepare("class").grantSource?.grantedAt,
            )
        }
        return base.copy(
            attemptsLeft = 0,
            servingStatus = servingStatus,
            grantSource = grant,
        )
    }

    fun subjectOption(
        code: String,
        name: String,
        chooseSubjects: Int,
        subjects: List<ChoiceSubjectView>,
    ): ChoiceOptionView = ChoiceOptionView(
        code = code,
        name = name,
        kind = PREPARE_OPTION_KIND_SUBJECT_CHOICE,
        chooseSubjects = chooseSubjects,
        countsByPick = emptyList(),
        subjects = subjects,
        questionCount = 30,
        durationMinutes = 60,
        maxPoints = java.math.BigDecimal("30"),
    )
}
