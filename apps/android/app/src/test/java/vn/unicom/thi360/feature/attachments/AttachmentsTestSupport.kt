package vn.unicom.thi360.feature.attachments

import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AttemptAudioPlayResponse
import vn.unicom.thi360.core.network.generated.model.AttemptAudioPlayView
import vn.unicom.thi360.core.network.generated.model.AudioProgressRequest
import vn.unicom.thi360.core.network.generated.model.StartAudioPlayRequest
import vn.unicom.thi360.feature.attachments.gateway.AttemptAudioGateway
import vn.unicom.thi360.feature.attachments.gateway.OpenApiAttemptAudioGateway
import vn.unicom.thi360.feature.attachments.model.AudioError
import vn.unicom.thi360.feature.attachments.model.AudioGatewayResult
import vn.unicom.thi360.feature.attachments.model.AudioPlaySnapshot
import vn.unicom.thi360.feature.attachments.playback.AudioPlaybackCallbacks
import vn.unicom.thi360.feature.attachments.playback.AudioPlaybackEngine
import vn.unicom.thi360.feature.attachments.playback.PlaybackStartResult
import vn.unicom.thi360.feature.attemptengine.AttemptEngine
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.FakeAttemptServer
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.ContractFixtureIndex

enum class AudioEndpoint {
    OPEN_PLAY,
    RESUME,
    PROGRESS,
    END,
    FETCH_AUDIO,
}

data class RecordedAudioCall(
    val endpoint: AudioEndpoint,
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String?,
    val assetId: UUID?,
    val playId: UUID?,
    val requestId: String? = null,
    val positionMs: Long? = null,
) {
    val sessionHeader: String?
        get() = headers.entries.firstOrNull { it.key.equals("X-Attempt-Session", ignoreCase = true) }?.value

    val authorizationHeader: String?
        get() = headers.entries.firstOrNull { it.key.equals("Authorization", ignoreCase = true) }?.value
}

data class AudioServerOverride(
    val statusCode: Int,
    val body: String,
    val networkFailure: Boolean = false,
    val socketPolicy: SocketPolicy = SocketPolicy.DISCONNECT_AT_START,
    val afterServerMutation: Boolean = false,
)

class FakeAudioServerAddon(
    private val attemptServer: FakeAttemptServer,
    private val defaultDurationMs: Long = 35_000L,
    private val defaultMaxPlays: Int = 2,
    private val audioPayload: ByteArray = loadAttachmentResourceBytes("silence-35s.wav"),
    private val nonAudioAssets: Map<UUID, ByteArray> = emptyMap(),
) {
    private data class AssetPlayRecord(
        var playId: UUID? = null,
        var playsUsed: Int = 0,
        var durationMs: Long,
        var positionMs: Long = 0L,
        var state: AttemptAudioPlayResponse.State = AttemptAudioPlayResponse.State.IDLE,
        var endReason: AttemptAudioPlayResponse.EndReason? = null,
        var lastRequestId: String? = null,
        var leaseUntil: OffsetDateTime? = null,
    )

    private val json = Serializer.kotlinxSerializationJson
    private val lock = Any()
    private val records = ConcurrentHashMap<UUID, AssetPlayRecord>()
    private val recordedCalls = CopyOnWriteArrayList<RecordedAudioCall>()
    private val queuedOverrides = ConcurrentHashMap<AudioEndpoint, ConcurrentLinkedQueue<AudioServerOverride>>()
    private val endpointGates = ConcurrentHashMap<AudioEndpoint, ConcurrentLinkedQueue<CompletableDeferred<Unit>>>()

    @Volatile
    var leaseDurationSeconds: Long = 45L

    @Volatile
    var maxPlays: Int = defaultMaxPlays

    @Volatile
    var partClosed: Boolean = false

    @Volatile
    var timeUp: Boolean = false

    init {
        installHttpRoutes()
    }

    fun seedPlayRecord(
        assetId: UUID,
        playId: UUID?,
        playsUsed: Int,
        durationMs: Long = defaultDurationMs,
        positionMs: Long = 0L,
        state: AttemptAudioPlayResponse.State,
        endReason: AttemptAudioPlayResponse.EndReason? = null,
        leaseUntil: OffsetDateTime? = attemptServer.serverNow.plusSeconds(leaseDurationSeconds),
    ) = synchronized(lock) {
        records[assetId] = AssetPlayRecord(
            playId = playId,
            playsUsed = playsUsed,
            durationMs = durationMs,
            positionMs = positionMs,
            state = state,
            endReason = endReason,
            leaseUntil = leaseUntil,
        )
    }

    fun enqueueFixture(
        endpoint: AudioEndpoint,
        operationId: String,
        statusCode: Int,
        variant: String? = null,
        mutateJson: (String) -> String = { it },
    ) {
        val raw = ContractFixtureIndex.find(operationId, statusCode, variant).file.readText(Charsets.UTF_8)
        enqueueOverride(endpoint, AudioServerOverride(statusCode = statusCode, body = mutateJson(raw)))
    }

    fun enqueueNetworkDropAfterMutation(endpoint: AudioEndpoint) {
        enqueueOverride(
            endpoint,
            AudioServerOverride(
                statusCode = 0,
                body = "",
                networkFailure = true,
                afterServerMutation = true,
            ),
        )
    }

    fun enqueueNetworkFailure(endpoint: AudioEndpoint) {
        enqueueOverride(
            endpoint,
            AudioServerOverride(
                statusCode = 0,
                body = "",
                networkFailure = true,
            ),
        )
    }

    fun enqueueOverride(endpoint: AudioEndpoint, override: AudioServerOverride) {
        queuedOverrides.computeIfAbsent(endpoint) { ConcurrentLinkedQueue() }.add(override)
    }

    fun gateNext(endpoint: AudioEndpoint): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        endpointGates.computeIfAbsent(endpoint) { ConcurrentLinkedQueue() }.add(gate)
        return gate
    }

    fun calls(endpoint: AudioEndpoint? = null): List<RecordedAudioCall> =
        if (endpoint == null) recordedCalls.toList() else recordedCalls.filter { it.endpoint == endpoint }

    fun resetCalls() {
        recordedCalls.clear()
    }

    fun buildAudioPlayViews(partId: UUID): List<AttemptAudioPlayView> = synchronized(lock) {
        records.entries.map { (assetId, rec) ->
            AttemptAudioPlayView(
                assetId = assetId,
                partId = partId,
                durationMs = rec.durationMs,
                playsUsed = rec.playsUsed,
                playsLeft = (maxPlays - rec.playsUsed).coerceAtLeast(0),
                positionMs = rec.positionMs,
                playId = rec.playId,
                state = when (rec.state) {
                    AttemptAudioPlayResponse.State.IDLE -> AttemptAudioPlayView.State.IDLE
                    AttemptAudioPlayResponse.State.PLAYING -> AttemptAudioPlayView.State.PLAYING
                    AttemptAudioPlayResponse.State.ENDED -> AttemptAudioPlayView.State.ENDED
                },
                endReason = when (rec.endReason) {
                    AttemptAudioPlayResponse.EndReason.FINISHED -> AttemptAudioPlayView.EndReason.FINISHED
                    AttemptAudioPlayResponse.EndReason.CLOSED -> AttemptAudioPlayView.EndReason.CLOSED
                    null -> null
                },
            )
        }
    }

    fun virtualTimeGateway(
        engine: AttemptEngine,
        bearerTokenProvider: () -> String = { "test-bearer-token" },
    ): AttemptAudioGateway = object : AttemptAudioGateway {
        private fun requireSession(): UUID? = (engine.state.value.session as? EditSessionState.Editing)?.sessionId

        override suspend fun openPlay(assetId: UUID, requestId: String): AudioGatewayResult<AudioPlaySnapshot> {
            val sid = requireSession() ?: return AudioGatewayResult.Failure(AudioError.SessionRevoked)
            endpointGates[AudioEndpoint.OPEN_PLAY]?.poll()?.await()
            val call = RecordedAudioCall(
                endpoint = AudioEndpoint.OPEN_PLAY,
                method = "POST",
                path = "/api/v1/me/attempts/${engine.attemptId}/audio/$assetId/plays",
                headers = mapOf(
                    "Authorization" to "Bearer ${bearerTokenProvider()}",
                    "X-Attempt-Session" to sid.toString(),
                ),
                body = json.encodeToString(StartAudioPlayRequest.serializer(), StartAudioPlayRequest(requestId)),
                assetId = assetId,
                playId = null,
                requestId = requestId,
            )
            recordedCalls.add(call)
            return executePlayEndpoint(AudioEndpoint.OPEN_PLAY, call)
        }

        override suspend fun resume(assetId: UUID, playId: UUID): AudioGatewayResult<AudioPlaySnapshot> {
            val sid = requireSession() ?: return AudioGatewayResult.Failure(AudioError.SessionRevoked)
            endpointGates[AudioEndpoint.RESUME]?.poll()?.await()
            val call = RecordedAudioCall(
                endpoint = AudioEndpoint.RESUME,
                method = "POST",
                path = "/api/v1/me/attempts/${engine.attemptId}/audio/$assetId/plays/$playId/resume",
                headers = mapOf(
                    "Authorization" to "Bearer ${bearerTokenProvider()}",
                    "X-Attempt-Session" to sid.toString(),
                ),
                body = null,
                assetId = assetId,
                playId = playId,
            )
            recordedCalls.add(call)
            return executePlayEndpoint(AudioEndpoint.RESUME, call)
        }

        override suspend fun progress(
            assetId: UUID,
            playId: UUID,
            positionMs: Long,
        ): AudioGatewayResult<AudioPlaySnapshot> {
            val sid = requireSession() ?: return AudioGatewayResult.Failure(AudioError.SessionRevoked)
            endpointGates[AudioEndpoint.PROGRESS]?.poll()?.await()
            val call = RecordedAudioCall(
                endpoint = AudioEndpoint.PROGRESS,
                method = "PUT",
                path = "/api/v1/me/attempts/${engine.attemptId}/audio/$assetId/plays/$playId/progress",
                headers = mapOf(
                    "Authorization" to "Bearer ${bearerTokenProvider()}",
                    "X-Attempt-Session" to sid.toString(),
                ),
                body = json.encodeToString(AudioProgressRequest.serializer(), AudioProgressRequest(positionMs)),
                assetId = assetId,
                playId = playId,
                positionMs = positionMs,
            )
            recordedCalls.add(call)
            return executePlayEndpoint(AudioEndpoint.PROGRESS, call)
        }

        override suspend fun end(assetId: UUID, playId: UUID): AudioGatewayResult<AudioPlaySnapshot> {
            val sid = requireSession() ?: return AudioGatewayResult.Failure(AudioError.SessionRevoked)
            endpointGates[AudioEndpoint.END]?.poll()?.await()
            val call = RecordedAudioCall(
                endpoint = AudioEndpoint.END,
                method = "POST",
                path = "/api/v1/me/attempts/${engine.attemptId}/audio/$assetId/plays/$playId/end",
                headers = mapOf(
                    "Authorization" to "Bearer ${bearerTokenProvider()}",
                    "X-Attempt-Session" to sid.toString(),
                ),
                body = null,
                assetId = assetId,
                playId = playId,
            )
            recordedCalls.add(call)
            return executePlayEndpoint(AudioEndpoint.END, call)
        }

        override suspend fun fetchAudio(assetId: UUID, playId: UUID): AudioGatewayResult<ByteArray> {
            val sid = requireSession() ?: return AudioGatewayResult.Failure(AudioError.SessionRevoked)
            endpointGates[AudioEndpoint.FETCH_AUDIO]?.poll()?.await()
            val call = RecordedAudioCall(
                endpoint = AudioEndpoint.FETCH_AUDIO,
                method = "GET",
                path = "/api/v1/me/attempts/${engine.attemptId}/assets/$assetId?playId=$playId",
                headers = mapOf(
                    "Authorization" to "Bearer ${bearerTokenProvider()}",
                    "X-Attempt-Session" to sid.toString(),
                ),
                body = null,
                assetId = assetId,
                playId = playId,
            )
            recordedCalls.add(call)
            val override = queuedOverrides[AudioEndpoint.FETCH_AUDIO]?.poll()
            if (override != null) {
                if (override.networkFailure) {
                    return AudioGatewayResult.Failure(AudioError.Network(java.io.IOException("Injected network drop")))
                }
                return AudioGatewayResult.Failure(OpenApiAttemptAudioGateway.mapHttpError(override.statusCode, override.body))
            }
            return synchronized(lock) {
                val rec = records[assetId]
                val leaseValid = rec != null &&
                    rec.state == AttemptAudioPlayResponse.State.PLAYING &&
                    rec.playId == playId &&
                    rec.leaseUntil != null &&
                    attemptServer.serverNow.isBefore(rec.leaseUntil)
                if (!leaseValid) {
                    val notFound = ContractFixtureIndex.find("downloadAttemptAsset", 404, "not-found")
                        .file.readText(Charsets.UTF_8)
                    AudioGatewayResult.Failure(OpenApiAttemptAudioGateway.mapHttpError(404, notFound))
                } else {
                    AudioGatewayResult.Success(audioPayload.copyOf())
                }
            }
        }
    }

    private fun installHttpRoutes() {
        attemptServer.contractServer.respondRegex(
            "POST",
            Regex("""/api/v1/me/attempts/[^/]+/audio/([^/]+)/plays$"""),
        ) { req -> handleHttpPlay(AudioEndpoint.OPEN_PLAY, req) }

        attemptServer.contractServer.respondRegex(
            "POST",
            Regex("""/api/v1/me/attempts/[^/]+/audio/([^/]+)/plays/([^/]+)/resume$"""),
        ) { req -> handleHttpPlay(AudioEndpoint.RESUME, req) }

        attemptServer.contractServer.respondRegex(
            "PUT",
            Regex("""/api/v1/me/attempts/[^/]+/audio/([^/]+)/plays/([^/]+)/progress$"""),
        ) { req -> handleHttpPlay(AudioEndpoint.PROGRESS, req) }

        attemptServer.contractServer.respondRegex(
            "POST",
            Regex("""/api/v1/me/attempts/[^/]+/audio/([^/]+)/plays/([^/]+)/end$"""),
        ) { req -> handleHttpPlay(AudioEndpoint.END, req) }

        attemptServer.contractServer.respondRegex(
            "GET",
            Regex("""/api/v1/me/attempts/[^/]+/assets/([^/?]+)(?:\?.*)?$"""),
        ) { req -> handleHttpAsset(req) }
    }

    private fun handleHttpPlay(endpoint: AudioEndpoint, req: RecordedRequest): MockResponse {
        val headers = LinkedHashMap<String, String>()
        for (i in 0 until req.headers.size) {
            headers[req.headers.name(i)] = req.headers.value(i)
        }
        val bodyStr = req.body.readUtf8().takeIf { it.isNotEmpty() }
        val cleanPath = req.path.orEmpty().substringBefore('?')
        val segments = cleanPath.split('/')
        val audioIdx = segments.indexOf("audio")
        val assetId = segments.getOrNull(audioIdx + 1)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val playId = segments.getOrNull(audioIdx + 3)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val requestId = if (endpoint == AudioEndpoint.OPEN_PLAY && bodyStr != null) {
            runCatching { json.decodeFromString(StartAudioPlayRequest.serializer(), bodyStr).requestId }.getOrNull()
        } else {
            null
        }
        val positionMs = if (endpoint == AudioEndpoint.PROGRESS && bodyStr != null) {
            runCatching { json.decodeFromString(AudioProgressRequest.serializer(), bodyStr).positionMs }.getOrNull()
        } else {
            null
        }
        val call = RecordedAudioCall(
            endpoint = endpoint,
            method = req.method.orEmpty().uppercase(),
            path = req.path.orEmpty(),
            headers = headers,
            body = bodyStr,
            assetId = assetId,
            playId = playId,
            requestId = requestId,
            positionMs = positionMs,
        )
        recordedCalls.add(call)
        val raw = computeRawPlayResponse(endpoint, call)
        if (raw.networkFailure) {
            return MockResponse().setSocketPolicy(raw.socketPolicy)
        }
        return ContractFakeServer.jsonResponse(raw.statusCode, raw.body)
    }

    private fun handleHttpAsset(req: RecordedRequest): MockResponse {
        val headers = LinkedHashMap<String, String>()
        for (i in 0 until req.headers.size) {
            headers[req.headers.name(i)] = req.headers.value(i)
        }
        val rawPath = req.path.orEmpty()
        val cleanPath = rawPath.substringBefore('?')
        val assetIdStr = cleanPath.substringAfterLast('/')
        val assetId = runCatching { UUID.fromString(assetIdStr) }.getOrNull()
        val playIdStr = rawPath.substringAfter("playId=", "").substringBefore('&').takeIf { it.isNotBlank() }
        val playId = playIdStr?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        recordedCalls.add(
            RecordedAudioCall(
                endpoint = AudioEndpoint.FETCH_AUDIO,
                method = "GET",
                path = rawPath,
                headers = headers,
                body = null,
                assetId = assetId,
                playId = playId,
            ),
        )

        val override = queuedOverrides[AudioEndpoint.FETCH_AUDIO]?.poll()
        if (override != null) {
            if (override.networkFailure) return MockResponse().setSocketPolicy(override.socketPolicy)
            return ContractFakeServer.jsonResponse(override.statusCode, override.body)
        }

        if (playId == null && assetId != null && nonAudioAssets.containsKey(assetId)) {
            val payload = nonAudioAssets.getValue(assetId)
            return MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/pdf")
                .setBody(Buffer().write(payload))
        }

        val allowed = synchronized(lock) {
            val rec = assetId?.let { records[it] }
            rec != null &&
                rec.state == AttemptAudioPlayResponse.State.PLAYING &&
                rec.playId == playId &&
                rec.leaseUntil != null &&
                attemptServer.serverNow.isBefore(rec.leaseUntil)
        }
        if (!allowed) {
            val notFound = ContractFixtureIndex.find("downloadAttemptAsset", 404, "not-found")
                .file.readText(Charsets.UTF_8)
            return ContractFakeServer.jsonResponse(404, notFound)
        }
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "audio/wav")
            .setBody(Buffer().write(audioPayload))
    }

    private fun executePlayEndpoint(
        endpoint: AudioEndpoint,
        call: RecordedAudioCall,
    ): AudioGatewayResult<AudioPlaySnapshot> {
        val raw = computeRawPlayResponse(endpoint, call)
        if (raw.networkFailure) {
            return AudioGatewayResult.Failure(AudioError.Network(java.io.IOException("Injected network failure")))
        }
        if (raw.statusCode in 200..299) {
            val decoded = json.decodeFromString(AttemptAudioPlayResponse.serializer(), raw.body)
            return AudioGatewayResult.Success(AudioPlaySnapshot.fromResponse(decoded))
        }
        return AudioGatewayResult.Failure(OpenApiAttemptAudioGateway.mapHttpError(raw.statusCode, raw.body))
    }

    private fun computeRawPlayResponse(
        endpoint: AudioEndpoint,
        call: RecordedAudioCall,
    ): AudioServerOverride {
        val queued = queuedOverrides[endpoint]?.poll()
        if (queued != null && !queued.afterServerMutation) {
            return queued
        }

        val computed = synchronized(lock) {
            val activeSession = attemptServer.activeSessionId
            val sentSession = call.sessionHeader?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            if (activeSession != null && sentSession != activeSession) {
                val raw = ContractFixtureIndex.find("updateAudioProgress", 409, "attempt-session-revoked")
                    .file.readText(Charsets.UTF_8)
                return@synchronized AudioServerOverride(409, raw)
            }

            if (timeUp) {
                val raw = ContractFixtureIndex.find("finishPart", 409, "time-up").file.readText(Charsets.UTF_8)
                return@synchronized AudioServerOverride(409, raw)
            }
            if (partClosed && endpoint != AudioEndpoint.END) {
                val raw = ContractFixtureIndex.find("resumeAudioPlay", 409, "part-closed")
                    .file.readText(Charsets.UTF_8)
                return@synchronized AudioServerOverride(409, raw)
            }

            val assetId = call.assetId ?: error("Missing assetId in audio call: ${call.path}")
            val rec = records.computeIfAbsent(assetId) {
                AssetPlayRecord(durationMs = defaultDurationMs)
            }

            when (endpoint) {
                AudioEndpoint.OPEN_PLAY -> {
                    val reqId = call.requestId.orEmpty()
                    if (reqId.isNotBlank() && reqId == rec.lastRequestId) {
                        rec.leaseUntil = attemptServer.serverNow.plusSeconds(leaseDurationSeconds)
                        return@synchronized encodeRecordResponse("openAudioPlay", rec)
                    }
                    if (rec.state == AttemptAudioPlayResponse.State.PLAYING) {
                        val template = ContractFixtureIndex.find("openAudioPlay", 409, "audio-play-in-progress")
                            .file.readText(Charsets.UTF_8)
                        val body = mutateField(template, "playId", JsonPrimitive(rec.playId.toString()))
                        return@synchronized AudioServerOverride(409, body)
                    }
                    if (rec.playsUsed >= maxPlays) {
                        val raw = ContractFixtureIndex.find("openAudioPlay", 409, "audio-plays-exhausted")
                            .file.readText(Charsets.UTF_8)
                        return@synchronized AudioServerOverride(409, raw)
                    }
                    rec.playId = UUID.randomUUID()
                    rec.playsUsed += 1
                    rec.positionMs = 0L
                    rec.state = AttemptAudioPlayResponse.State.PLAYING
                    rec.endReason = null
                    rec.lastRequestId = reqId
                    rec.leaseUntil = attemptServer.serverNow.plusSeconds(leaseDurationSeconds)
                    encodeRecordResponse("openAudioPlay", rec)
                }

                AudioEndpoint.RESUME -> {
                    if (rec.state != AttemptAudioPlayResponse.State.PLAYING || rec.playId != call.playId) {
                        val raw = ContractFixtureIndex.find("updateAudioProgress", 409, "audio-play-stale")
                            .file.readText(Charsets.UTF_8)
                        return@synchronized AudioServerOverride(409, raw)
                    }
                    rec.leaseUntil = attemptServer.serverNow.plusSeconds(leaseDurationSeconds)
                    encodeRecordResponse("resumeAudioPlay", rec)
                }

                AudioEndpoint.PROGRESS -> {
                    if (rec.state != AttemptAudioPlayResponse.State.PLAYING || rec.playId != call.playId) {
                        val raw = ContractFixtureIndex.find("updateAudioProgress", 409, "audio-play-stale")
                            .file.readText(Charsets.UTF_8)
                        return@synchronized AudioServerOverride(409, raw)
                    }
                    val reported = call.positionMs ?: rec.positionMs
                    if (reported < rec.positionMs || reported > rec.durationMs) {
                        val template = ContractFixtureIndex.find("updateAudioProgress", 409, "audio-seek-rejected")
                            .file.readText(Charsets.UTF_8)
                        val body = mutateField(template, "positionMs", JsonPrimitive(rec.positionMs))
                        return@synchronized AudioServerOverride(409, body)
                    }
                    rec.positionMs = reported
                    rec.leaseUntil = attemptServer.serverNow.plusSeconds(leaseDurationSeconds)
                    encodeRecordResponse("updateAudioProgress", rec)
                }

                AudioEndpoint.END -> {
                    if (partClosed) {
                        rec.state = AttemptAudioPlayResponse.State.ENDED
                        rec.endReason = AttemptAudioPlayResponse.EndReason.CLOSED
                        return@synchronized encodeRecordResponse("endAudioPlay", rec, variant = "closed")
                    }
                    if (rec.playId != call.playId) {
                        val raw = ContractFixtureIndex.find("updateAudioProgress", 409, "audio-play-stale")
                            .file.readText(Charsets.UTF_8)
                        return@synchronized AudioServerOverride(409, raw)
                    }
                    rec.state = AttemptAudioPlayResponse.State.ENDED
                    rec.endReason = AttemptAudioPlayResponse.EndReason.FINISHED
                    rec.positionMs = rec.durationMs
                    encodeRecordResponse("endAudioPlay", rec)
                }

                AudioEndpoint.FETCH_AUDIO -> error("Unexpected endpoint")
            }
        }

        if (queued != null && queued.afterServerMutation) {
            return queued
        }
        return computed
    }

    private fun encodeRecordResponse(
        operationId: String,
        rec: AssetPlayRecord,
        variant: String? = null,
    ): AudioServerOverride {
        val template = ContractFixtureIndex.find(operationId, 200, variant).file.readText(Charsets.UTF_8)
        val decoded = json.decodeFromString(AttemptAudioPlayResponse.serializer(), template)
        val updated = decoded.copy(
            playId = rec.playId,
            playsUsed = rec.playsUsed,
            playsLeft = (maxPlays - rec.playsUsed).coerceAtLeast(0),
            positionMs = rec.positionMs,
            durationMs = rec.durationMs,
            state = rec.state,
            endReason = rec.endReason,
        )
        return AudioServerOverride(200, json.encodeToString(AttemptAudioPlayResponse.serializer(), updated))
    }

    companion object {
        private val json = Serializer.kotlinxSerializationJson

        fun mutateField(rawJson: String, field: String, value: JsonElement): String {
            val obj = json.parseToJsonElement(rawJson).jsonObject
            val updated = JsonObject(obj.toMutableMap().apply { put(field, value) })
            return json.encodeToString(JsonObject.serializer(), updated)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FakeAudioPlaybackEngine(
    private val testScope: TestScope? = null,
    private val mediaDurationMs: Long = 35_000L,
    private val onStopHook: (() -> Unit)? = null,
) : AudioPlaybackEngine {
    var failNextStart = false
    var startCount = 0
        private set
    var stopCount = 0
        private set
    var lastStartPositionMs: Long? = null
        private set
    var lastAuthorizedSeekMs: Long? = null
        private set
    var heldBytesSize: Int = 0
        private set

    private var playing = false
    private var anchorPositionMs = 0L
    private var anchorTimeMs = 0L
    private var manualPositionOverrideMs: Long? = null
    private var activeCallbacks: AudioPlaybackCallbacks? = null
    private var activeBytes: ByteArray? = null

    override suspend fun start(
        audioBytes: ByteArray,
        startPositionMs: Long,
        callbacks: AudioPlaybackCallbacks,
    ): PlaybackStartResult {
        if (failNextStart) {
            failNextStart = false
            return PlaybackStartResult.Failed
        }
        startCount += 1
        activeBytes = audioBytes
        heldBytesSize = audioBytes.size
        lastStartPositionMs = startPositionMs
        anchorPositionMs = startPositionMs
        anchorTimeMs = nowMs()
        manualPositionOverrideMs = null
        activeCallbacks = callbacks
        playing = true
        return PlaybackStartResult.Started(durationMs = mediaDurationMs)
    }

    override fun seekToAuthorized(positionMs: Long) {
        lastAuthorizedSeekMs = positionMs
        anchorPositionMs = positionMs
        anchorTimeMs = nowMs()
        manualPositionOverrideMs = null
    }

    override fun currentPositionMs(): Long {
        manualPositionOverrideMs?.let { return it }
        if (!playing) return anchorPositionMs
        val elapsed = (nowMs() - anchorTimeMs).coerceAtLeast(0L)
        return (anchorPositionMs + elapsed).coerceAtMost(mediaDurationMs)
    }

    override fun durationMs(): Long = mediaDurationMs

    override fun isPlaying(): Boolean = playing

    override fun stopAndRelease() {
        val wasPlaying = playing
        playing = false
        activeBytes?.fill(0)
        activeBytes = null
        heldBytesSize = 0
        activeCallbacks = null
        if (wasPlaying) {
            stopCount += 1
            onStopHook?.invoke()
        }
    }

    fun setManualPositionMs(positionMs: Long) {
        manualPositionOverrideMs = positionMs
    }

    fun triggerCompleted() {
        val cb = activeCallbacks ?: return
        cb.onCompleted()
    }

    fun triggerFocusLost() {
        val cb = activeCallbacks ?: return
        stopAndRelease()
        cb.onFocusLost()
    }

    fun triggerBecomingNoisy() {
        val cb = activeCallbacks ?: return
        stopAndRelease()
        cb.onBecomingNoisy()
    }

    fun triggerHeadsetPause() {
        val cb = activeCallbacks ?: return
        stopAndRelease()
        cb.onHeadsetPause()
    }

    private fun nowMs(): Long = testScope?.testScheduler?.currentTime ?: System.currentTimeMillis()
}

internal fun loadAttachmentResourceBytes(name: String): ByteArray {
    val stream = FakeAudioServerAddon::class.java.classLoader?.getResourceAsStream("attachments/$name")
    if (stream != null) {
        return stream.use { it.readBytes() }
    }
    val candidates = listOf(
        File("src/test/resources/attachments/$name"),
        File("app/src/test/resources/attachments/$name"),
    )
    val file = candidates.firstOrNull { it.isFile }
        ?: error("Missing test attachment resource attachments/$name from ${File(".").canonicalPath}")
    return file.readBytes()
}
