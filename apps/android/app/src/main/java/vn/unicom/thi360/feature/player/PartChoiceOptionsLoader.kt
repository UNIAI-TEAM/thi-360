package vn.unicom.thi360.feature.player

import androidx.compose.runtime.Immutable
import java.util.UUID
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.generated.api.MyExamsApi
import vn.unicom.thi360.core.network.generated.model.AfterStartChoiceView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptPrepareResponse
import vn.unicom.thi360.core.network.generated.model.ChoiceSubjectView
import vn.unicom.thi360.core.network.generated.model.MyExamsResponse
import vn.unicom.thi360.core.network.safeApiCall
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.toLoadFailed
import vn.unicom.thi360.feature.prepare.PrepareApi
import vn.unicom.thi360.feature.prepare.StartContextStore

interface PartChoiceMetadataApi {
    suspend fun listMyExams(): ApiResult<MyExamsResponse>
    suspend fun prepare(entitlementId: UUID): ApiResult<AttemptPrepareResponse>
}

class OpenApiPartChoiceMetadataApi(
    private val myExamsApi: MyExamsApi,
    private val prepareApi: PrepareApi,
) : PartChoiceMetadataApi {
    override suspend fun listMyExams(): ApiResult<MyExamsResponse> =
        safeApiCall { myExamsApi.listMyExams() }

    override suspend fun prepare(entitlementId: UUID): ApiResult<AttemptPrepareResponse> =
        prepareApi.prepare(entitlementId)
}

@Immutable
data class PartChoiceMetadata(
    val entitlementId: UUID,
    val choices: List<AfterStartChoiceView>,
    val subjectsByCode: Map<String, ChoiceSubjectView>,
) {
    fun choiceForPart(part: AttemptPartStateView): AfterStartChoiceView? {
        val code = part.partCode?.trim().takeUnless { it.isNullOrEmpty() }
        if (code != null) {
            choices.firstOrNull { it.partCode?.trim().equals(code, ignoreCase = true) }
                ?.let { return it }
        }
        val ord = part.ord
        if (ord != null) {
            choices.firstOrNull { it.ord == ord }?.let { return it }
        }
        return null
    }
}

class PartChoiceOptionsLoader(
    private val startContextStore: StartContextStore?,
    private val api: PartChoiceMetadataApi,
    private val isOnlineProvider: () -> Boolean = { true },
) {
    suspend fun load(attemptId: UUID): LoadState<PartChoiceMetadata> {
        val fromStore = startContextStore?.read(attemptId)?.entitlementId?.let(::parseUuidOrNull)
        val entitlementId = fromStore ?: when (val examsResult = api.listMyExams()) {
            is ApiResult.Success -> {
                val matched = examsResult.data.items.orEmpty()
                    .firstOrNull { it.activeAttemptId == attemptId }
                    ?.entitlementId
                if (matched == null) {
                    return LoadState.Failed(
                        kind = LoadFailureKind.NotFound,
                        canRetry = true,
                    )
                }
                matched
            }

            is ApiResult.HttpFailure -> return examsResult.toLoadFailed().copy(canRetry = true)
            is ApiResult.NetworkFailure -> return examsResult.toLoadFailed(isOnline = isOnlineProvider()).copy(canRetry = true)
        }

        return when (val prepareResult = api.prepare(entitlementId)) {
            is ApiResult.Success -> {
                val data = prepareResult.data
                val subjects = LinkedHashMap<String, ChoiceSubjectView>()
                data.preChoice?.options.orEmpty().forEach { opt ->
                    opt.subjects.orEmpty().forEach { subj ->
                        val code = subj.code?.trim()
                        if (!code.isNullOrEmpty()) {
                            subjects[code] = subj
                            subjects[code.uppercase()] = subj
                        }
                    }
                }
                data.afterStartChoices.orEmpty().forEach { choice ->
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
                LoadState.Loaded(
                    PartChoiceMetadata(
                        entitlementId = entitlementId,
                        choices = data.afterStartChoices.orEmpty(),
                        subjectsByCode = subjects,
                    ),
                )
            }

            is ApiResult.HttpFailure -> prepareResult.toLoadFailed().copy(canRetry = true)
            is ApiResult.NetworkFailure -> prepareResult.toLoadFailed(isOnline = isOnlineProvider()).copy(canRetry = true)
        }
    }

    private fun parseUuidOrNull(raw: String): UUID? =
        runCatching { UUID.fromString(raw.trim()) }.getOrNull()
}
