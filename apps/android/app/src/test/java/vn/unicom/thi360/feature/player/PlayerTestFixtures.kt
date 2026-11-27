package vn.unicom.thi360.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import vn.unicom.thi360.core.network.ApiError
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptPrepareResponse
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.ChoiceSubjectView
import vn.unicom.thi360.core.network.generated.model.MyExamsResponse
import vn.unicom.thi360.core.network.generated.model.StartAttemptRequest
import vn.unicom.thi360.core.network.generated.model.StartAttemptResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.attemptengine.AnswerSyncStatus
import vn.unicom.thi360.feature.attemptengine.AttemptEngine
import vn.unicom.thi360.feature.attemptengine.Countdown
import vn.unicom.thi360.feature.attemptengine.CountdownTarget
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.EnginePosition
import vn.unicom.thi360.feature.attemptengine.EngineState
import vn.unicom.thi360.feature.attemptengine.ItemId
import vn.unicom.thi360.feature.attemptengine.LocalAnswer
import vn.unicom.thi360.feature.attemptengine.PartSelection
import vn.unicom.thi360.feature.prepare.PrepareApi
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.testing.ContractFixtureIndex

internal object PlayerTestFixtures {
    private val json = Serializer.kotlinxSerializationJson

    fun attemptState(variant: String): AttemptStateResponse {
        val entry = ContractFixtureIndex.find("getState", 200, variant)
        return json.decodeFromString(
            AttemptStateResponse.serializer(),
            entry.file.readText(Charsets.UTF_8),
        )
    }

    fun attempt50(): AttemptStateResponse {
        val file = File(ContractFixtureLoader.openApiSpecFile().parentFile, "rich-text/attempt-50.json")
        require(file.isFile) { "Missing attempt-50.json fixture: ${file.canonicalPath}" }
        return json.decodeFromString(
            AttemptStateResponse.serializer(),
            file.readText(Charsets.UTF_8),
        )
    }

    fun prepareResponse(variant: String = "per-part"): AttemptPrepareResponse {
        val entry = ContractFixtureIndex.find("prepare", 200, variant)
        return json.decodeFromString(
            AttemptPrepareResponse.serializer(),
            entry.file.readText(Charsets.UTF_8),
        )
    }

    fun myExamsResponse(variant: String = "my-exams-list-200.json"): MyExamsResponse {
        val entry = ContractFixtureIndex.find("listMyExams", 200, variant)
        return json.decodeFromString(
            MyExamsResponse.serializer(),
            entry.file.readText(Charsets.UTF_8),
        )
    }

    fun partChoiceMetadata(prepareVariant: String = "per-part"): PartChoiceMetadata {
        val resp = prepareResponse(prepareVariant)
        val subjects = LinkedHashMap<String, ChoiceSubjectView>()
        resp.afterStartChoices.orEmpty().forEach { choice ->
            choice.options.orEmpty().forEach { opt ->
                opt.subjects.orEmpty().forEach { subj ->
                    val code = subj.code?.trim()
                    if (!code.isNullOrEmpty()) {
                        subjects[code] = subj
                        subjects[code.uppercase()] = subj
                    }
                }
            }
        }
        return PartChoiceMetadata(
            entitlementId = resp.entitlementId,
            choices = resp.afterStartChoices.orEmpty(),
            subjectsByCode = subjects,
        )
    }

    fun activeEngineState(
        attempt: AttemptStateResponse,
        session: EditSessionState = EditSessionState.Editing(UUID.randomUUID()),
        remainingMs: Long = 15 * 60_000L,
        answersOverride: Map<ItemId, LocalAnswer>? = null,
        positionOverride: EnginePosition? = null,
    ): EngineState {
        val sorted = PartSelection.sortedParts(attempt)
        val firstPart = sorted.firstOrNull { PartSelection.isSelectableState(it.state) } ?: sorted.firstOrNull()
        val firstLeaf = firstPart?.let(PartSelection::leafItems)?.firstOrNull()?.itemId
        val pos = positionOverride ?: firstPart?.partId?.let { EnginePosition(it, firstLeaf) }
        val baseAnswers = LinkedHashMap<ItemId, LocalAnswer>()
        for (part in sorted) {
            for (item in PartSelection.leafItems(part)) {
                val id = item.itemId ?: continue
                baseAnswers[id] = LocalAnswer(
                    value = item.answer,
                    seq = item.answerSeq ?: 0L,
                    flagged = item.flagged == true,
                    sync = AnswerSyncStatus.Saved,
                )
            }
        }
        if (answersOverride != null) {
            baseAnswers.putAll(answersOverride)
        }
        val activePart = sorted.firstOrNull { it.partId == pos?.partId } ?: firstPart
        val target = when {
            activePart?.state == AttemptPartStateView.State.AWAITING_CHOICE && activePart.partId != null ->
                CountdownTarget.Choice(activePart.partId)
            attempt.timing == AttemptStateResponse.Timing.SHARED -> CountdownTarget.Attempt
            activePart?.partId != null -> CountdownTarget.Part(activePart.partId)
            else -> CountdownTarget.None
        }
        return EngineState(
            load = vn.unicom.thi360.core.ui.LoadState.Loaded(Unit),
            attempt = attempt,
            answers = baseAnswers,
            position = pos,
            session = session,
            clock = Countdown(target = target, remainingMs = remainingMs),
        )
    }

    fun withFutureDeadlines(
        attempt: AttemptStateResponse,
        partRemainingSeconds: Long = 900L,
        attemptRemainingSeconds: Long = 1_800L,
        choiceRemainingSeconds: Long = 600L,
    ): AttemptStateResponse {
        val now: OffsetDateTime = attempt.serverNow
        return attempt.copy(
            deadlineAt = if (attempt.timing == AttemptStateResponse.Timing.SHARED) {
                now.plusSeconds(attemptRemainingSeconds)
            } else {
                attempt.deadlineAt
            },
            parts = attempt.parts?.map { part ->
                part.copy(
                    deadlineAt = if (part.state == AttemptPartStateView.State.OPEN) {
                        now.plusSeconds(partRemainingSeconds)
                    } else {
                        part.deadlineAt
                    },
                    choiceDeadlineAt = if (part.state == AttemptPartStateView.State.AWAITING_CHOICE) {
                        now.plusSeconds(choiceRemainingSeconds)
                    } else {
                        part.choiceDeadlineAt
                    },
                )
            },
        )
    }

    fun leafItems(attempt: AttemptStateResponse): List<AttemptItemView> =
        PartSelection.sortedParts(attempt).flatMap { PartSelection.leafItems(it) }
}

internal class FakePartChoiceMetadataApi(
    var examsResult: () -> ApiResult<MyExamsResponse> = {
        ApiResult.Success(PlayerTestFixtures.myExamsResponse())
    },
    var prepareResult: (UUID) -> ApiResult<AttemptPrepareResponse> = {
        ApiResult.Success(PlayerTestFixtures.prepareResponse("per-part"))
    },
) : PartChoiceMetadataApi {
    var listExamsCalls: Int = 0
    val prepareCalls = mutableListOf<UUID>()

    override suspend fun listMyExams(): ApiResult<MyExamsResponse> {
        listExamsCalls += 1
        return examsResult()
    }

    override suspend fun prepare(entitlementId: UUID): ApiResult<AttemptPrepareResponse> {
        prepareCalls.add(entitlementId)
        return prepareResult(entitlementId)
    }
}

internal class FakePlayerPrepareApi(
    var getStateHandler: (UUID) -> ApiResult<AttemptStateResponse>,
    var startHandler: (String, StartAttemptRequest) -> ApiResult<StartAttemptResponse> = { _, _ ->
        ApiResult.HttpFailure(500, ApiError("SERVER_ERROR", "Not configured"))
    },
    var prepareHandler: (UUID) -> ApiResult<AttemptPrepareResponse> = {
        ApiResult.Success(PlayerTestFixtures.prepareResponse("per-part"))
    },
) : PrepareApi {
    var startCalls = 0
    var stateCalls = 0

    override suspend fun prepare(entitlementId: UUID): ApiResult<AttemptPrepareResponse> =
        prepareHandler(entitlementId)

    override suspend fun start(
        idempotencyKey: String,
        request: StartAttemptRequest,
    ): ApiResult<StartAttemptResponse> {
        startCalls += 1
        return startHandler(idempotencyKey, request)
    }

    override suspend fun getAttemptState(attemptId: UUID): ApiResult<AttemptStateResponse> {
        stateCalls += 1
        return getStateHandler(attemptId)
    }
}

@Composable
internal fun PlayerTestTheme(
    locale: AppLocale = AppLocale.VI,
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFormulaEngine provides SourceBoxFormulaEngine(),
    ) {
        Thi360Theme(
            preferences = AppPreferences(locale = locale, themeMode = themeMode),
            content = content,
        )
    }
}

internal fun waitForEngineState(
    composeRule: androidx.compose.ui.test.junit4.ComposeTestRule,
    engine: AttemptEngine,
    timeoutMs: Long = 10_000L,
    predicate: (EngineState) -> Boolean,
) {
    composeRule.waitUntil(timeoutMs) {
        predicate(engine.state.value)
    }
    composeRule.waitForIdle()
}
