package vn.unicom.thi360.feature.attemptengine

import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import vn.unicom.thi360.core.network.ApiErrorParser
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.Thi360SessionStack
import vn.unicom.thi360.core.network.generated.api.AttemptsApi
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.ClaimSessionRequest
import vn.unicom.thi360.core.network.generated.model.ClaimSessionResponse
import vn.unicom.thi360.core.network.generated.model.ConfirmPartChoiceRequest
import vn.unicom.thi360.core.network.generated.model.FinalAnswerResult
import vn.unicom.thi360.core.network.generated.model.FinishPartRequest
import vn.unicom.thi360.core.network.generated.model.FinishPartResponse
import vn.unicom.thi360.core.network.generated.model.HeartbeatResponse
import vn.unicom.thi360.core.network.generated.model.PartChoiceResponse
import vn.unicom.thi360.core.network.generated.model.RetryPartRequest
import vn.unicom.thi360.core.network.generated.model.SaveAnswersRequest
import vn.unicom.thi360.core.network.generated.model.SaveAnswersResponse
import vn.unicom.thi360.core.network.generated.model.SubmitAttemptRequest
import vn.unicom.thi360.core.network.generated.model.SubmitAttemptResponse
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.ContractFixtureIndex

enum class AttemptOperation {
    GET_STATE,
    CLAIM_SESSION,
    HEARTBEAT,
    SAVE_ANSWERS,
    FINISH_PART,
    SUBMIT,
    CONFIRM_PART_CHOICE,
    RETRY_PART_GENERATION,
}

data class RecordedAttemptCall(
    val operation: AttemptOperation,
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String?,
) {
    val attemptSessionHeader: String?
        get() = headers.entries.firstOrNull { it.key.equals("X-Attempt-Session", ignoreCase = true) }?.value

    val idempotencyKeyHeader: String?
        get() = headers.entries.firstOrNull { it.key.equals("Idempotency-Key", ignoreCase = true) }?.value
}

data class FakeResponseOverride(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
    val networkFailure: Boolean = false,
    val socketPolicy: SocketPolicy = SocketPolicy.DISCONNECT_AT_START,
)

class FakeAttemptServer(
    val contractServer: ContractFakeServer = ContractFakeServer(),
) {
    private val json = Serializer.kotlinxSerializationJson
    private val lock = Any()

    private val recordedCalls = CopyOnWriteArrayList<RecordedAttemptCall>()
    private val queuedResponses = ConcurrentHashMap<AttemptOperation, ConcurrentLinkedQueue<FakeResponseOverride>>()
    private val endpointDelaysMs = ConcurrentHashMap<AttemptOperation, Long>()
    private val endpointGates = ConcurrentHashMap<AttemptOperation, ConcurrentLinkedQueue<CompletableDeferred<Unit>>>()
    private val activeSaveRequests = AtomicInteger(0)
    private val maxConcurrentSaveRequests = AtomicInteger(0)
    private val onRequestHooks = ConcurrentHashMap<AttemptOperation, (RecordedAttemptCall) -> Unit>()

    @Volatile
    var serverNow: OffsetDateTime = OffsetDateTime.parse("2026-11-20T10:00:00Z")

    @Volatile
    var activeSessionId: UUID? = null

    @Volatile
    var lastSeenAt: OffsetDateTime? = null

    @Volatile
    var evictionCode: String = OpenApiAttemptApi.CODE_ATTEMPT_SESSION_REVOKED

    @Volatile
    var takeoverGeneratesNewSessionId: Boolean = false

    @Volatile
    var stateMutator: ((AttemptStateResponse) -> AttemptStateResponse)? = null

    @Volatile
    var saveResultMode: SaveResultOverrideMode = SaveResultOverrideMode.Stateful

    private var baseAttemptState: AttemptStateResponse = decodeStateFixture("in-progress-per-part")
    private val serverAnswers = LinkedHashMap<UUID, AnswerValue?>()
    private val serverSeqs = LinkedHashMap<UUID, Long>()
    private val serverFlags = LinkedHashMap<UUID, Boolean>()
    private var serverPosition: EnginePosition? = null

    sealed interface SaveResultOverrideMode {
        data object Stateful : SaveResultOverrideMode
        data class FixedResult(
            val status: FinalAnswerResult.Status,
            val reason: String? = null,
            val seqOverride: Long? = null,
            val targetItemId: UUID? = null,
        ) : SaveResultOverrideMode
    }

    init {
        installRoutes()
    }

    fun start(): FakeAttemptServer {
        contractServer.start()
        return this
    }

    fun shutdown() {
        contractServer.shutdown()
    }

    fun url(path: String = "/"): String = contractServer.url(path)

    fun openApiAttemptApi(stack: Thi360SessionStack): AttemptApi =
        OpenApiAttemptApi(service = stack.authenticatedService(AttemptsApi::class.java))

    fun loadStateFixture(
        variant: String,
        transform: (AttemptStateResponse) -> AttemptStateResponse = { it },
    ): AttemptStateResponse = synchronized(lock) {
        val decoded = transform(decodeStateFixture(variant))
        setAttemptStateLocked(decoded)
        decoded
    }

    fun loadAttempt50Fixture(
        transform: (AttemptStateResponse) -> AttemptStateResponse = { it },
    ): AttemptStateResponse = synchronized(lock) {
        val richTextFile = File(ContractFixtureLoader.openApiSpecFile().parentFile, "rich-text/attempt-50.json")
        require(richTextFile.isFile) { "Missing RT-1 attempt-50 fixture: ${richTextFile.canonicalPath}" }
        val raw = richTextFile.readText(Charsets.UTF_8)
        val decoded = transform(json.decodeFromString(AttemptStateResponse.serializer(), raw))
        setAttemptStateLocked(decoded)
        decoded
    }

    fun setAttemptState(state: AttemptStateResponse) = synchronized(lock) {
        setAttemptStateLocked(state)
    }

    fun currentAttemptState(): AttemptStateResponse = synchronized(lock) {
        materializeAttemptStateLocked()
    }

    fun setServerItemState(itemId: UUID, seq: Long, answer: AnswerValue?, flagged: Boolean = false) = synchronized(lock) {
        serverSeqs[itemId] = seq
        serverAnswers[itemId] = answer
        serverFlags[itemId] = flagged
    }

    fun setServerPosition(position: EnginePosition?) = synchronized(lock) {
        serverPosition = position
    }

    fun setPartState(
        partId: UUID,
        state: AttemptPartStateView.State,
        deadlineAt: OffsetDateTime? = null,
        choiceDeadlineAt: OffsetDateTime? = null,
        closedAt: OffsetDateTime? = null,
    ) = synchronized(lock) {
        val updatedParts = baseAttemptState.parts.orEmpty().map { part ->
            if (part.partId == partId) {
                part.copy(
                    state = state,
                    deadlineAt = deadlineAt ?: part.deadlineAt,
                    choiceDeadlineAt = choiceDeadlineAt ?: part.choiceDeadlineAt,
                    closedAt = closedAt ?: part.closedAt,
                )
            } else {
                part
            }
        }
        baseAttemptState = baseAttemptState.copy(parts = updatedParts)
    }

    fun closeAttemptOnServer(
        state: AttemptStateResponse.State = AttemptStateResponse.State.GRADING,
        closedAt: OffsetDateTime = serverNow,
    ) = synchronized(lock) {
        val updatedParts = baseAttemptState.parts.orEmpty().map { part ->
            if (part.state == AttemptPartStateView.State.OPEN) {
                part.copy(state = AttemptPartStateView.State.CLOSED, closedAt = closedAt)
            } else if (part.state == AttemptPartStateView.State.AWAITING_CHOICE) {
                part.copy(state = AttemptPartStateView.State.SKIPPED, closedAt = closedAt)
            } else {
                part
            }
        }
        baseAttemptState = baseAttemptState.copy(state = state, parts = updatedParts)
    }

    fun simulateTakeoverByOtherDevice(
        otherSessionId: UUID = UUID.randomUUID(),
        seenAt: OffsetDateTime = serverNow,
        conflictCode: String = OpenApiAttemptApi.CODE_ATTEMPT_SESSION_REVOKED,
    ) = synchronized(lock) {
        activeSessionId = otherSessionId
        lastSeenAt = seenAt
        evictionCode = conflictCode
    }

    fun setEndpointDelay(operation: AttemptOperation, delayMs: Long) {
        if (delayMs <= 0L) {
            endpointDelaysMs.remove(operation)
        } else {
            endpointDelaysMs[operation] = delayMs
        }
    }

    fun gateNext(operation: AttemptOperation): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        endpointGates.computeIfAbsent(operation) { ConcurrentLinkedQueue() }.add(gate)
        return gate
    }

    fun onRequest(operation: AttemptOperation, callback: (RecordedAttemptCall) -> Unit) {
        onRequestHooks[operation] = callback
    }

    fun enqueueFixtureError(
        operation: AttemptOperation,
        operationId: String,
        statusCode: Int,
        variant: String? = null,
        headers: Map<String, String> = emptyMap(),
        mutateJson: (String) -> String = { it },
    ) {
        val entry = ContractFixtureIndex.find(operationId, statusCode, variant)
        val body = mutateJson(entry.file.readText(Charsets.UTF_8))
        enqueueResponse(
            operation,
            FakeResponseOverride(
                statusCode = statusCode,
                body = body,
                headers = headers,
            ),
        )
    }

    // Plan A6 unproducible-response exception: backend does not emit 409 TIME_UP on POST .../submit;
    // derived from certified attempts-finish-409-time-up.json (no field mutation needed).
    fun enqueueSubmit409TimeUpA6() {
        enqueueFixtureError(
            operation = AttemptOperation.SUBMIT,
            operationId = "finishPart",
            statusCode = 409,
            variant = "time-up",
        )
    }

    // Plan A6 unproducible-response exception: backend does not emit 409 PART_GENERATING on POST .../choice;
    // derived from certified attempts-retry-409-part-generating.json.
    fun enqueueChoice409PartGeneratingA6() {
        enqueueFixtureError(
            operation = AttemptOperation.CONFIRM_PART_CHOICE,
            operationId = "retryPartGeneration",
            statusCode = 409,
            variant = "part-generating",
        )
    }

    // Plan A6 unproducible-response exception: backend only emits ATTEMPT_OPEN_ELSEWHERE on claimSession,
    // not on heartbeat or saveAnswers; derived by mutating "code" on certified attempt-session-revoked fixture.
    fun enqueueOpenElsewhereConflictA6(operation: AttemptOperation) {
        val opId = when (operation) {
            AttemptOperation.HEARTBEAT -> "heartbeat"
            AttemptOperation.SAVE_ANSWERS -> "saveAnswers"
            else -> "claimSession"
        }
        enqueueFixtureError(
            operation = operation,
            operationId = opId,
            statusCode = 409,
            variant = if (opId == "claimSession") "attempt-open-elsewhere" else "attempt-session-revoked",
            mutateJson = { raw ->
                mutateJsonField(raw, "code", JsonPrimitive(OpenApiAttemptApi.CODE_ATTEMPT_OPEN_ELSEWHERE))
            },
        )
    }

    fun enqueueRateLimited(
        operation: AttemptOperation,
        retryAfterHeader: String? = null,
    ) {
        val headers = if (retryAfterHeader != null) {
            mapOf("Retry-After" to retryAfterHeader)
        } else {
            emptyMap()
        }
        enqueueFixtureError(
            operation = operation,
            operationId = "saveAnswers",
            statusCode = 429,
            variant = "rate-limited",
            headers = headers,
        )
    }

    fun enqueueServerError(operation: AttemptOperation, statusCode: Int = 503) {
        // Derived from certified error envelope by mutating code/message for 5xx fault injection.
        val base = ContractFixtureIndex.find("saveAnswers", 429, "rate-limited").file.readText(Charsets.UTF_8)
        val mutated = mutateJsonField(base, "code", JsonPrimitive("SERVICE_UNAVAILABLE"))
        enqueueResponse(
            operation,
            FakeResponseOverride(statusCode = statusCode, body = mutated),
        )
    }

    fun enqueueHttpError(
        operation: AttemptOperation,
        statusCode: Int,
        code: String,
        headers: Map<String, String> = emptyMap(),
    ) {
        val base = ContractFixtureIndex.find("saveAnswers", 429, "rate-limited").file.readText(Charsets.UTF_8)
        val mutated = mutateJsonField(base, "code", JsonPrimitive(code))
        enqueueResponse(
            operation,
            FakeResponseOverride(statusCode = statusCode, body = mutated, headers = headers),
        )
    }

    fun enqueueNetworkFailure(operation: AttemptOperation, count: Int = 1) {
        repeat(count) {
            enqueueResponse(
                operation,
                FakeResponseOverride(statusCode = 0, body = "", networkFailure = true),
            )
        }
    }

    fun enqueueResponse(operation: AttemptOperation, override: FakeResponseOverride) {
        queuedResponses.computeIfAbsent(operation) { ConcurrentLinkedQueue() }.add(override)
    }

    fun calls(operation: AttemptOperation? = null): List<RecordedAttemptCall> =
        if (operation == null) recordedCalls.toList() else recordedCalls.filter { it.operation == operation }

    fun callCount(operation: AttemptOperation): Int = calls(operation).size

    fun maxConcurrentSaveCalls(): Int = maxConcurrentSaveRequests.get()

    fun resetRecordedCalls() {
        recordedCalls.clear()
        maxConcurrentSaveRequests.set(0)
    }

    fun virtualTimeApi(): AttemptApi = VirtualTimeFakeAttemptApi()

    private fun setAttemptStateLocked(decoded: AttemptStateResponse) {
        baseAttemptState = decoded
        serverNow = decoded.serverNow
        serverAnswers.clear()
        serverSeqs.clear()
        serverFlags.clear()
        for (part in decoded.parts.orEmpty()) {
            for (item in part.items.orEmpty()) {
                val id = item.itemId ?: continue
                serverSeqs[id] = item.answerSeq ?: 0L
                serverAnswers[id] = item.answer
                serverFlags[id] = item.flagged ?: false
            }
        }
        serverPosition = PartSelection.parseServerPosition(decoded)
    }

    private fun materializeAttemptStateLocked(): AttemptStateResponse {
        val updatedParts = baseAttemptState.parts?.map { part ->
            val updatedItems = part.items?.map { item ->
                val id = item.itemId
                if (id != null && serverSeqs.containsKey(id)) {
                    item.copy(
                        answerSeq = serverSeqs[id],
                        answer = serverAnswers[id],
                        flagged = serverFlags[id],
                    )
                } else {
                    item
                }
            }
            part.copy(items = updatedItems)
        }
        val positionMap: Map<String, JsonElement>? = serverPosition?.let { pos ->
            buildMap {
                put("partId", JsonPrimitive(pos.partId.toString()))
                if (pos.itemId != null) {
                    put("itemId", JsonPrimitive(pos.itemId.toString()))
                }
            }
        }
        val state = baseAttemptState.copy(
            serverNow = serverNow,
            parts = updatedParts,
            lastPosition = positionMap ?: baseAttemptState.lastPosition,
        )
        return stateMutator?.invoke(state) ?: state
    }

    private fun decodeStateFixture(variant: String): AttemptStateResponse {
        val entry = ContractFixtureIndex.find("getState", 200, variant)
        return json.decodeFromString(
            AttemptStateResponse.serializer(),
            entry.file.readText(Charsets.UTF_8),
        )
    }

    private fun installRoutes() {
        contractServer.respondRegex("GET", Regex("""/api/v1/me/attempts/[^/]+$""")) { req ->
            handleHttp(AttemptOperation.GET_STATE, req)
        }
        contractServer.respondRegex("POST", Regex("""/api/v1/me/attempts/[^/]+/session$""")) { req ->
            handleHttp(AttemptOperation.CLAIM_SESSION, req)
        }
        contractServer.respondRegex("POST", Regex("""/api/v1/me/attempts/[^/]+/heartbeat$""")) { req ->
            handleHttp(AttemptOperation.HEARTBEAT, req)
        }
        contractServer.respondRegex("PUT", Regex("""/api/v1/me/attempts/[^/]+/answers$""")) { req ->
            handleHttp(AttemptOperation.SAVE_ANSWERS, req)
        }
        contractServer.respondRegex("POST", Regex("""/api/v1/me/attempts/[^/]+/parts/[^/]+/finish$""")) { req ->
            handleHttp(AttemptOperation.FINISH_PART, req)
        }
        contractServer.respondRegex("POST", Regex("""/api/v1/me/attempts/[^/]+/submit$""")) { req ->
            handleHttp(AttemptOperation.SUBMIT, req)
        }
        contractServer.respondRegex("POST", Regex("""/api/v1/me/attempts/[^/]+/parts/[^/]+/choice$""")) { req ->
            handleHttp(AttemptOperation.CONFIRM_PART_CHOICE, req)
        }
        contractServer.respondRegex("POST", Regex("""/api/v1/me/attempts/[^/]+/parts/[^/]+/retry$""")) { req ->
            handleHttp(AttemptOperation.RETRY_PART_GENERATION, req)
        }
    }

    private fun handleHttp(operation: AttemptOperation, request: RecordedRequest): MockResponse {
        val headerMap = LinkedHashMap<String, String>()
        for (i in 0 until request.headers.size) {
            headerMap[request.headers.name(i)] = request.headers.value(i)
        }
        val bodyStr = request.body.readUtf8().takeIf { it.isNotEmpty() }
        val call = RecordedAttemptCall(
            operation = operation,
            method = request.method.orEmpty().uppercase(),
            path = request.path.orEmpty(),
            headers = headerMap,
            body = bodyStr,
        )
        recordedCalls.add(call)
        onRequestHooks[operation]?.invoke(call)

        val delayMs = endpointDelaysMs[operation] ?: 0L
        val response = computeRawResponse(operation, call)
        if (response.networkFailure) {
            return MockResponse().setSocketPolicy(response.socketPolicy)
        }
        val mock = ContractFakeServer.jsonResponse(response.statusCode, response.body, response.headers)
        if (delayMs > 0L) {
            mock.setBodyDelay(delayMs, TimeUnit.MILLISECONDS)
        }
        return mock
    }

    private fun computeRawResponse(
        operation: AttemptOperation,
        call: RecordedAttemptCall,
    ): FakeResponseOverride {
        queuedResponses[operation]?.poll()?.let { return it }

        return synchronized(lock) {
            when (operation) {
                AttemptOperation.GET_STATE -> {
                    val state = materializeAttemptStateLocked()
                    FakeResponseOverride(
                        statusCode = 200,
                        body = json.encodeToString(AttemptStateResponse.serializer(), state),
                    )
                }

                AttemptOperation.CLAIM_SESSION -> handleClaimLocked(call)
                AttemptOperation.HEARTBEAT -> handleHeartbeatLocked(call)
                AttemptOperation.SAVE_ANSWERS -> handleSaveLocked(call)
                AttemptOperation.FINISH_PART -> handleFinishPartLocked(call)
                AttemptOperation.SUBMIT -> handleSubmitLocked(call)
                AttemptOperation.CONFIRM_PART_CHOICE -> handleConfirmChoiceLocked(call)
                AttemptOperation.RETRY_PART_GENERATION -> handleRetryGenerationLocked(call)
            }
        }
    }

    private fun handleClaimLocked(call: RecordedAttemptCall): FakeResponseOverride {
        val state = materializeAttemptStateLocked()
        if (state.state != AttemptStateResponse.State.IN_PROGRESS) {
            val fixture = ContractFixtureIndex.find("claimSession", 409, "attempt-not-open")
            return FakeResponseOverride(409, fixture.file.readText(Charsets.UTF_8))
        }
        val req = call.body?.let { json.decodeFromString(ClaimSessionRequest.serializer(), it) }
        val reqSid = req?.sessionId
        val takeover = req?.takeover == true
        val existing = activeSessionId

        if (existing != null && existing != reqSid && !takeover) {
            val template = ContractFixtureIndex.find("claimSession", 409, "attempt-open-elsewhere")
                .file.readText(Charsets.UTF_8)
            val body = mutateJsonField(
                template,
                "lastSeenAt",
                lastSeenAt?.let { JsonPrimitive(it.toString()) } ?: JsonNull,
            )
            return FakeResponseOverride(409, body)
        }

        val resolvedSid = when {
            existing == null -> reqSid ?: UUID.randomUUID()
            existing == reqSid -> reqSid
            takeover && takeoverGeneratesNewSessionId -> UUID.randomUUID()
            takeover && reqSid != null && reqSid != existing -> reqSid
            else -> UUID.randomUUID()
        }
        activeSessionId = resolvedSid
        lastSeenAt = serverNow

        val template = ContractFixtureIndex.find("claimSession", 200, null).file.readText(Charsets.UTF_8)
        val decoded = json.decodeFromString(ClaimSessionResponse.serializer(), template)
        val response = decoded.copy(sessionId = resolvedSid, serverNow = serverNow)
        return FakeResponseOverride(200, json.encodeToString(ClaimSessionResponse.serializer(), response))
    }

    private fun handleHeartbeatLocked(call: RecordedAttemptCall): FakeResponseOverride {
        validateSessionLocked(call, "heartbeat")?.let { return it }
        val state = materializeAttemptStateLocked()
        val hbState = HeartbeatResponse.State.entries.firstOrNull { it.value == state.state.value }
            ?: HeartbeatResponse.State.IN_PROGRESS
        if (hbState == HeartbeatResponse.State.IN_PROGRESS) {
            lastSeenAt = serverNow
        }
        val template = ContractFixtureIndex.find("heartbeat", 200, null).file.readText(Charsets.UTF_8)
        val decoded = json.decodeFromString(HeartbeatResponse.serializer(), template)
        val response = decoded.copy(serverNow = serverNow, state = hbState)
        return FakeResponseOverride(200, json.encodeToString(HeartbeatResponse.serializer(), response))
    }

    private fun handleSaveLocked(call: RecordedAttemptCall): FakeResponseOverride {
        validateSessionLocked(call, "saveAnswers")?.let { return it }
        val state = materializeAttemptStateLocked()
        if (state.state != AttemptStateResponse.State.IN_PROGRESS) {
            val fixture = ContractFixtureIndex.find("finishPart", 409, "time-up")
            return FakeResponseOverride(409, fixture.file.readText(Charsets.UTF_8))
        }
        val req = call.body?.let { json.decodeFromString(SaveAnswersRequest.serializer(), it) }
        val openPartIds = state.parts.orEmpty()
            .filter { it.state == AttemptPartStateView.State.OPEN }
            .mapNotNull { it.partId }
            .toSet()
        val itemPartMap = buildMap<UUID, UUID> {
            for (part in state.parts.orEmpty()) {
                val pid = part.partId ?: continue
                for (item in part.items.orEmpty()) {
                    val iid = item.itemId ?: continue
                    put(iid, pid)
                }
            }
        }

        val results = mutableListOf<FinalAnswerResult>()
        val overrideMode = saveResultMode
        for (change in req?.changes.orEmpty()) {
            if (overrideMode is SaveResultOverrideMode.FixedResult &&
                (overrideMode.targetItemId == null || overrideMode.targetItemId == change.itemId)
            ) {
                results.add(
                    FinalAnswerResult(
                        itemId = change.itemId,
                        seq = overrideMode.seqOverride ?: change.seq,
                        status = overrideMode.status,
                        reason = overrideMode.reason,
                    ),
                )
                continue
            }
            val partId = itemPartMap[change.itemId]
            if (partId == null) {
                results.add(
                    FinalAnswerResult(
                        itemId = change.itemId,
                        seq = change.seq,
                        status = FinalAnswerResult.Status.REJECTED,
                        reason = "NOT_IN_SCOPE",
                    ),
                )
            } else if (partId !in openPartIds) {
                results.add(
                    FinalAnswerResult(
                        itemId = change.itemId,
                        seq = change.seq,
                        status = FinalAnswerResult.Status.REJECTED,
                        reason = OpenApiAttemptApi.CODE_PART_CLOSED,
                    ),
                )
            } else {
                val currentSeq = serverSeqs[change.itemId] ?: 0L
                if (change.seq <= currentSeq) {
                    results.add(
                        FinalAnswerResult(
                            itemId = change.itemId,
                            seq = change.seq,
                            status = FinalAnswerResult.Status.STALE,
                            reason = null,
                        ),
                    )
                } else {
                    serverSeqs[change.itemId] = change.seq
                    serverAnswers[change.itemId] = change.answer
                    results.add(
                        FinalAnswerResult(
                            itemId = change.itemId,
                            seq = change.seq,
                            status = FinalAnswerResult.Status.SAVED,
                            reason = null,
                        ),
                    )
                }
            }
        }

        for (flag in req?.flags.orEmpty()) {
            val partId = itemPartMap[flag.itemId]
            if (partId != null && partId in openPartIds) {
                serverFlags[flag.itemId] = flag.flagged
            }
        }

        req?.position?.let { pos ->
            val pid = pos.partId
            if (pid != null && pid in openPartIds) {
                serverPosition = EnginePosition(partId = pid, itemId = pos.itemId)
            }
        }

        val template = ContractFixtureIndex.find("saveAnswers", 200, "saved").file.readText(Charsets.UTF_8)
        val decoded = json.decodeFromString(SaveAnswersResponse.serializer(), template)
        val response = decoded.copy(results = results, serverNow = serverNow)
        return FakeResponseOverride(200, json.encodeToString(SaveAnswersResponse.serializer(), response))
    }

    private fun handleFinishPartLocked(call: RecordedAttemptCall): FakeResponseOverride {
        validateSessionLocked(call, "finishPart")?.let { return it }
        val partIdStr = call.path.substringAfter("/parts/").substringBefore("/finish")
        val targetPartId = runCatching { UUID.fromString(partIdStr) }.getOrNull()
        val req = call.body?.let { json.decodeFromString(FinishPartRequest.serializer(), it) }
        val results = mutableListOf<FinalAnswerResult>()
        for (change in req?.finalAnswers.orEmpty()) {
            serverSeqs[change.itemId] = maxOf(serverSeqs[change.itemId] ?: 0L, change.seq)
            serverAnswers[change.itemId] = change.answer
            results.add(
                FinalAnswerResult(
                    itemId = change.itemId,
                    seq = change.seq,
                    status = FinalAnswerResult.Status.SAVED,
                    reason = null,
                ),
            )
        }

        if (targetPartId != null) {
            val sorted = baseAttemptState.parts.orEmpty().sortedBy { it.ord ?: 0 }
            val idx = sorted.indexOfFirst { it.partId == targetPartId }
            val updatedParts = sorted.mapIndexed { i, part ->
                when {
                    i == idx -> part.copy(state = AttemptPartStateView.State.CLOSED, closedAt = serverNow)
                    i == idx + 1 && part.state == AttemptPartStateView.State.PENDING ->
                        part.copy(state = AttemptPartStateView.State.OPEN, startedAt = serverNow)
                    else -> part
                }
            }
            baseAttemptState = baseAttemptState.copy(parts = updatedParts)
        }

        val template = ContractFixtureIndex.find("finishPart", 200, null).file.readText(Charsets.UTF_8)
        val decoded = json.decodeFromString(FinishPartResponse.serializer(), template)
        val response = decoded.copy(
            attemptId = baseAttemptState.attemptId,
            serverNow = serverNow,
            results = results,
        )
        return FakeResponseOverride(200, json.encodeToString(FinishPartResponse.serializer(), response))
    }

    private fun handleSubmitLocked(call: RecordedAttemptCall): FakeResponseOverride {
        validateSessionLocked(call, "submit")?.let { return it }
        val req = call.body?.let { json.decodeFromString(SubmitAttemptRequest.serializer(), it) }
        val results = mutableListOf<FinalAnswerResult>()
        for (change in req?.finalAnswers.orEmpty()) {
            serverSeqs[change.itemId] = maxOf(serverSeqs[change.itemId] ?: 0L, change.seq)
            serverAnswers[change.itemId] = change.answer
            results.add(
                FinalAnswerResult(
                    itemId = change.itemId,
                    seq = change.seq,
                    status = FinalAnswerResult.Status.SAVED,
                    reason = null,
                ),
            )
        }
        closeAttemptOnServer(AttemptStateResponse.State.GRADING, serverNow)
        val template = ContractFixtureIndex.find("submit", 200, null).file.readText(Charsets.UTF_8)
        val decoded = json.decodeFromString(SubmitAttemptResponse.serializer(), template)
        val response = decoded.copy(
            attemptId = baseAttemptState.attemptId,
            serverNow = serverNow,
            results = results,
        )
        return FakeResponseOverride(200, json.encodeToString(SubmitAttemptResponse.serializer(), response))
    }

    private fun handleConfirmChoiceLocked(call: RecordedAttemptCall): FakeResponseOverride {
        validateSessionLocked(call, "saveAnswers")?.let { return it }
        val partIdStr = call.path.substringAfter("/parts/").substringBefore("/choice")
        val targetPartId = runCatching { UUID.fromString(partIdStr) }.getOrNull()
        if (targetPartId != null) {
            setPartState(targetPartId, AttemptPartStateView.State.GENERATING)
        }
        val template = ContractFixtureIndex.find("confirmPartChoice", 202, null).file.readText(Charsets.UTF_8)
        val decoded = json.decodeFromString(PartChoiceResponse.serializer(), template)
        val response = decoded.copy(
            attemptId = baseAttemptState.attemptId,
            partId = targetPartId ?: decoded.partId,
            serverNow = serverNow,
        )
        return FakeResponseOverride(202, json.encodeToString(PartChoiceResponse.serializer(), response))
    }

    private fun handleRetryGenerationLocked(call: RecordedAttemptCall): FakeResponseOverride {
        validateSessionLocked(call, "saveAnswers")?.let { return it }
        val partIdStr = call.path.substringAfter("/parts/").substringBefore("/retry")
        val targetPartId = runCatching { UUID.fromString(partIdStr) }.getOrNull()
        if (targetPartId != null) {
            setPartState(targetPartId, AttemptPartStateView.State.GENERATING)
        }
        val template = ContractFixtureIndex.find("retryPartGeneration", 202, null).file.readText(Charsets.UTF_8)
        val decoded = json.decodeFromString(PartChoiceResponse.serializer(), template)
        val response = decoded.copy(
            attemptId = baseAttemptState.attemptId,
            partId = targetPartId ?: decoded.partId,
            serverNow = serverNow,
        )
        return FakeResponseOverride(202, json.encodeToString(PartChoiceResponse.serializer(), response))
    }

    private fun validateSessionLocked(
        call: RecordedAttemptCall,
        revokedFixtureOperationId: String,
    ): FakeResponseOverride? {
        val current = activeSessionId ?: return null
        val sent = call.attemptSessionHeader?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (sent == current) return null
        val template = ContractFixtureIndex.find(revokedFixtureOperationId, 409, "attempt-session-revoked")
            .file.readText(Charsets.UTF_8)
        val body = if (evictionCode != OpenApiAttemptApi.CODE_ATTEMPT_SESSION_REVOKED) {
            mutateJsonField(template, "code", JsonPrimitive(evictionCode))
        } else {
            template
        }
        return FakeResponseOverride(409, body)
    }

    private fun mutateJsonField(rawJson: String, field: String, value: JsonElement): String {
        val obj = json.parseToJsonElement(rawJson).jsonObject
        val updated = buildJsonObject {
            for ((k, v) in obj) {
                put(k, if (k == field) value else v)
            }
            if (field !in obj) {
                put(field, value)
            }
        }
        return updated.toString()
    }

    private inner class VirtualTimeFakeAttemptApi : AttemptApi {
        private suspend fun dispatch(
            operation: AttemptOperation,
            method: String,
            path: String,
            headers: Map<String, String> = emptyMap(),
            body: String? = null,
        ): FakeResponseOverride {
            val call = RecordedAttemptCall(
                operation = operation,
                method = method,
                path = path,
                headers = headers,
                body = body,
            )
            recordedCalls.add(call)
            onRequestHooks[operation]?.invoke(call)

            val trackConcurrency = operation == AttemptOperation.SAVE_ANSWERS
            if (trackConcurrency) {
                val active = activeSaveRequests.incrementAndGet()
                maxConcurrentSaveRequests.accumulateAndGet(active, ::maxOf)
            }
            return try {
                endpointGates[operation]?.poll()?.await()
                val delayMs = endpointDelaysMs[operation] ?: 0L
                if (delayMs > 0L) {
                    delay(delayMs)
                }
                computeRawResponse(operation, call)
            } finally {
                if (trackConcurrency) {
                    activeSaveRequests.decrementAndGet()
                }
            }
        }

        private fun toHttpFailure(raw: FakeResponseOverride): ApiResult.HttpFailure {
            val parsedError = ApiErrorParser.parse(raw.body)
            val header = raw.headers.entries
                .firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
                ?.value
            val retryAfter = OpenApiAttemptApi.parseAttemptRetryAfterSeconds(header)
                ?: parsedError?.retryAfter?.takeIf { it >= 0L }?.coerceAtLeast(1L)
            return ApiResult.HttpFailure(
                statusCode = raw.statusCode,
                error = parsedError,
                retryAfterSeconds = retryAfter,
            )
        }

        override suspend fun getState(attemptId: UUID): ApiResult<AttemptStateResponse> {
            val raw = dispatch(AttemptOperation.GET_STATE, "GET", "/api/v1/me/attempts/$attemptId")
            if (raw.networkFailure) return ApiResult.NetworkFailure(IOException("Simulated network error"))
            if (raw.statusCode !in 200..299) return toHttpFailure(raw)
            return ApiResult.Success(json.decodeFromString(AttemptStateResponse.serializer(), raw.body))
        }

        override suspend fun claimSession(
            attemptId: UUID,
            request: ClaimSessionRequest,
        ): ClaimSessionApiResult {
            val encoded = json.encodeToString(ClaimSessionRequest.serializer(), request)
            val raw = dispatch(
                AttemptOperation.CLAIM_SESSION,
                "POST",
                "/api/v1/me/attempts/$attemptId/session",
                body = encoded,
            )
            if (raw.networkFailure) return ClaimSessionApiResult.NetworkFailure(IOException("Simulated network error"))
            if (raw.statusCode in 200..299) {
                return ClaimSessionApiResult.Claimed(
                    json.decodeFromString(ClaimSessionResponse.serializer(), raw.body),
                )
            }
            if (raw.statusCode == 409) {
                val parsed = runCatching {
                    json.decodeFromString(
                        vn.unicom.thi360.core.network.generated.model.AttemptSessionConflictResponse.serializer(),
                        raw.body,
                    )
                }.getOrNull()
                if (parsed?.code == OpenApiAttemptApi.CODE_ATTEMPT_OPEN_ELSEWHERE) {
                    return ClaimSessionApiResult.OpenElsewhere(parsed.lastSeenAt, parsed.code)
                }
            }
            return ClaimSessionApiResult.HttpFailure(toHttpFailure(raw))
        }

        override suspend fun heartbeat(attemptId: UUID, sessionId: UUID): ApiResult<HeartbeatResponse> {
            val raw = dispatch(
                AttemptOperation.HEARTBEAT,
                "POST",
                "/api/v1/me/attempts/$attemptId/heartbeat",
                headers = mapOf("X-Attempt-Session" to sessionId.toString()),
            )
            if (raw.networkFailure) return ApiResult.NetworkFailure(IOException("Simulated network error"))
            if (raw.statusCode !in 200..299) return toHttpFailure(raw)
            return ApiResult.Success(json.decodeFromString(HeartbeatResponse.serializer(), raw.body))
        }

        override suspend fun saveAnswers(
            attemptId: UUID,
            sessionId: UUID,
            request: SaveAnswersRequest,
        ): ApiResult<SaveAnswersResponse> {
            val encoded = json.encodeToString(SaveAnswersRequest.serializer(), request)
            val raw = dispatch(
                AttemptOperation.SAVE_ANSWERS,
                "PUT",
                "/api/v1/me/attempts/$attemptId/answers",
                headers = mapOf("X-Attempt-Session" to sessionId.toString()),
                body = encoded,
            )
            if (raw.networkFailure) return ApiResult.NetworkFailure(IOException("Simulated network error"))
            if (raw.statusCode !in 200..299) return toHttpFailure(raw)
            return ApiResult.Success(json.decodeFromString(SaveAnswersResponse.serializer(), raw.body))
        }

        override suspend fun finishPart(
            attemptId: UUID,
            partId: UUID,
            sessionId: UUID,
            request: FinishPartRequest,
        ): ApiResult<FinishPartResponse> {
            val encoded = json.encodeToString(FinishPartRequest.serializer(), request)
            val raw = dispatch(
                AttemptOperation.FINISH_PART,
                "POST",
                "/api/v1/me/attempts/$attemptId/parts/$partId/finish",
                headers = mapOf("X-Attempt-Session" to sessionId.toString()),
                body = encoded,
            )
            if (raw.networkFailure) return ApiResult.NetworkFailure(IOException("Simulated network error"))
            if (raw.statusCode !in 200..299) return toHttpFailure(raw)
            return ApiResult.Success(json.decodeFromString(FinishPartResponse.serializer(), raw.body))
        }

        override suspend fun submit(
            attemptId: UUID,
            sessionId: UUID,
            idempotencyKey: String,
            request: SubmitAttemptRequest,
        ): ApiResult<SubmitAttemptResponse> {
            val encoded = json.encodeToString(SubmitAttemptRequest.serializer(), request)
            val raw = dispatch(
                AttemptOperation.SUBMIT,
                "POST",
                "/api/v1/me/attempts/$attemptId/submit",
                headers = mapOf(
                    "X-Attempt-Session" to sessionId.toString(),
                    "Idempotency-Key" to idempotencyKey,
                ),
                body = encoded,
            )
            if (raw.networkFailure) return ApiResult.NetworkFailure(IOException("Simulated network error"))
            if (raw.statusCode !in 200..299) return toHttpFailure(raw)
            return ApiResult.Success(json.decodeFromString(SubmitAttemptResponse.serializer(), raw.body))
        }

        override suspend fun confirmPartChoice(
            attemptId: UUID,
            partId: UUID,
            sessionId: UUID,
            idempotencyKey: String,
            request: ConfirmPartChoiceRequest,
        ): ApiResult<PartChoiceResponse> {
            val encoded = json.encodeToString(ConfirmPartChoiceRequest.serializer(), request)
            val raw = dispatch(
                AttemptOperation.CONFIRM_PART_CHOICE,
                "POST",
                "/api/v1/me/attempts/$attemptId/parts/$partId/choice",
                headers = mapOf(
                    "X-Attempt-Session" to sessionId.toString(),
                    "Idempotency-Key" to idempotencyKey,
                ),
                body = encoded,
            )
            if (raw.networkFailure) return ApiResult.NetworkFailure(IOException("Simulated network error"))
            if (raw.statusCode !in 200..299) return toHttpFailure(raw)
            return ApiResult.Success(json.decodeFromString(PartChoiceResponse.serializer(), raw.body))
        }

        override suspend fun retryPartGeneration(
            attemptId: UUID,
            partId: UUID,
            sessionId: UUID,
            idempotencyKey: String,
            request: RetryPartRequest,
        ): ApiResult<PartChoiceResponse> {
            val encoded = json.encodeToString(RetryPartRequest.serializer(), request)
            val raw = dispatch(
                AttemptOperation.RETRY_PART_GENERATION,
                "POST",
                "/api/v1/me/attempts/$attemptId/parts/$partId/retry",
                headers = mapOf(
                    "X-Attempt-Session" to sessionId.toString(),
                    "Idempotency-Key" to idempotencyKey,
                ),
                body = encoded,
            )
            if (raw.networkFailure) return ApiResult.NetworkFailure(IOException("Simulated network error"))
            if (raw.statusCode !in 200..299) return toHttpFailure(raw)
            return ApiResult.Success(json.decodeFromString(PartChoiceResponse.serializer(), raw.body))
        }
    }
}
