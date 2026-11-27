package vn.unicom.thi360.feature.attachments

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.network.generated.api.AttemptsApi
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AttemptAudioPlayResponse
import vn.unicom.thi360.core.network.generated.model.AttemptAudioPlayView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.AudioPolicyView
import vn.unicom.thi360.core.network.generated.model.ClaimSessionResponse
import vn.unicom.thi360.core.network.generated.model.HeartbeatResponse
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.feature.attachments.gateway.OpenApiAttemptAudioGateway
import vn.unicom.thi360.feature.attemptengine.AttemptEngine
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.OpenApiAttemptApi
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource

internal data class RecordedHttpRequest(
    val method: String,
    val path: String,
    val query: String?,
    val headers: Map<String, String>,
    val body: String,
) {
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

internal class MutableAndroidConnectivityMonitor(
    initialOnline: Boolean = true,
) : ConnectivityMonitor {
    private val _online = MutableStateFlow(initialOnline)
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    fun setOnline(online: Boolean) {
        _online.value = online
    }
}

internal class EmbeddedAttachmentServer(
    val attemptId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000101"),
    val partId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000201"),
    val maxPlays: Int = 2,
    val defaultDurationMs: Long = 35_000L,
) : AutoCloseable {
    private val json = Serializer.kotlinxSerializationJson
    private val serverSocket = ServerSocket(0)
    private val running = AtomicBoolean(true)
    private val lock = Any()

    val baseUrl: String = "http://127.0.0.1:${serverSocket.localPort}/"
    val sessionId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000301")

    private val recorded = CopyOnWriteArrayList<RecordedHttpRequest>()
    private val assetPayloads = ConcurrentHashMap<UUID, Pair<String, ByteArray>>()
    private val assetDurations = ConcurrentHashMap<UUID, Long>()
    private val playsByAsset = LinkedHashMap<UUID, PlayTrack>()
    private val playsById = LinkedHashMap<UUID, PlayTrack>()
    private val playsByRequestId = LinkedHashMap<Pair<UUID, String>, PlayTrack>()

    private data class PlayTrack(
        val playId: UUID,
        val assetId: UUID,
        var playsUsed: Int,
        var playsLeft: Int,
        var positionMs: Long,
        val durationMs: Long,
        var state: AttemptAudioPlayResponse.State,
    )

    private val acceptThread = thread(isDaemon = true, name = "embedded-attachment-server") {
        while (running.get()) {
            try {
                val socket = serverSocket.accept()
                handleClient(socket)
            } catch (_: Throwable) {
                if (!running.get()) break
            }
        }
    }

    fun registerAsset(assetId: UUID, mime: String, bytes: ByteArray, durationMs: Long = defaultDurationMs) {
        assetPayloads[assetId] = mime to bytes
        assetDurations[assetId] = durationMs
    }

    fun overrideServerPositionMs(playId: UUID, positionMs: Long) {
        synchronized(lock) {
            playsById[playId]?.positionMs = positionMs
        }
    }

    fun serverPositionMs(playId: UUID): Long = synchronized(lock) {
        playsById[playId]?.positionMs ?: 0L
    }

    fun requests(): List<RecordedHttpRequest> = recorded.toList()

    fun resetRequests() {
        recorded.clear()
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket.close() }
        acceptThread.join(1_500L)
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            val input = s.getInputStream()
            val headerBytes = readUntilHeaderEnd(input) ?: return
            val headerText = headerBytes.toString(Charsets.UTF_8)
            val lines = headerText.split("\r\n")
            if (lines.isEmpty()) return
            val requestLineParts = lines[0].split(" ")
            if (requestLineParts.size < 2) return
            val method = requestLineParts[0]
            val target = requestLineParts[1]
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "").takeIf { it.isNotEmpty() }

            val headers = LinkedHashMap<String, String>()
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line.isBlank()) continue
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim()] = line.substring(colon + 1).trim()
                }
            }
            val contentLength = headers.entries
                .firstOrNull { it.key.equals("Content-Length", ignoreCase = true) }
                ?.value?.toIntOrNull() ?: 0
            val bodyBytes = if (contentLength > 0) readExact(input, contentLength) else ByteArray(0)
            val bodyText = bodyBytes.toString(Charsets.UTF_8)

            val req = RecordedHttpRequest(method, path, query, headers, bodyText)
            recorded.add(req)

            val response = route(req)
            val out = s.getOutputStream()
            val statusText = when (response.status) {
                200 -> "OK"
                401 -> "Unauthorized"
                404 -> "Not Found"
                409 -> "Conflict"
                else -> "Error"
            }
            val head = "HTTP/1.1 ${response.status} $statusText\r\n" +
                "Content-Type: ${response.contentType}\r\n" +
                "Content-Length: ${response.body.size}\r\n" +
                "Connection: close\r\n\r\n"
            out.write(head.toByteArray(Charsets.US_ASCII))
            out.write(response.body)
            out.flush()
        }
    }

    private data class HttpReply(val status: Int, val contentType: String, val body: ByteArray)

    private fun jsonReply(status: Int, body: String): HttpReply =
        HttpReply(status, "application/json; charset=utf-8", body.toByteArray(Charsets.UTF_8))

    private fun route(req: RecordedHttpRequest): HttpReply {
        val now = OffsetDateTime.now()
        val path = req.path
        val attemptPrefix = "/api/v1/me/attempts/$attemptId"

        if (req.method == "GET" && path == attemptPrefix) {
            val audioPlays = synchronized(lock) {
                playsByAsset.values.map { track ->
                    AttemptAudioPlayView(
                        assetId = track.assetId,
                        partId = partId,
                        durationMs = track.durationMs,
                        playsUsed = track.playsUsed,
                        playsLeft = track.playsLeft,
                        state = when (track.state) {
                            AttemptAudioPlayResponse.State.IDLE -> AttemptAudioPlayView.State.IDLE
                            AttemptAudioPlayResponse.State.PLAYING -> AttemptAudioPlayView.State.PLAYING
                            AttemptAudioPlayResponse.State.ENDED -> AttemptAudioPlayView.State.ENDED
                        },
                        playId = track.playId,
                        positionMs = track.positionMs,
                    )
                }
            }
            val state = AttemptStateResponse(
                attemptId = attemptId,
                serverNow = now,
                state = AttemptStateResponse.State.IN_PROGRESS,
                audioPlays = audioPlays,
                audioPolicy = AudioPolicyView(maxPlays = maxPlays, seekable = false),
                deadlineAt = now.plusMinutes(30),
                navigationMode = AttemptStateResponse.NavigationMode.FREE,
                timing = AttemptStateResponse.Timing.SHARED,
                parts = listOf(
                    AttemptPartStateView(
                        partId = partId,
                        ord = 0,
                        partCode = "P1",
                        partName = "Phần 1",
                        state = AttemptPartStateView.State.OPEN,
                        deadlineAt = now.plusMinutes(30),
                        items = emptyList(),
                    ),
                ),
            )
            return jsonReply(200, json.encodeToString(state))
        }

        if (req.method == "POST" && path == "$attemptPrefix/session") {
            val claim = ClaimSessionResponse(serverNow = now, sessionId = sessionId)
            return jsonReply(200, json.encodeToString(claim))
        }

        if (req.method == "POST" && path == "$attemptPrefix/heartbeat") {
            val hb = HeartbeatResponse(serverNow = now, state = HeartbeatResponse.State.IN_PROGRESS)
            return jsonReply(200, json.encodeToString(hb))
        }

        val openRegex = Regex("^$attemptPrefix/audio/([^/]+)/plays$")
        openRegex.matchEntire(path)?.let { match ->
            if (req.method != "POST") return jsonReply(404, """{"code":"NOT_FOUND"}""")
            val assetId = UUID.fromString(match.groupValues[1])
            val requestId = runCatching {
                json.parseToJsonElement(req.body).jsonObject["requestId"]?.jsonPrimitive?.content
            }.getOrNull().orEmpty()
            synchronized(lock) {
                val existingByReq = playsByRequestId[assetId to requestId]
                if (existingByReq != null) {
                    return jsonReply(200, json.encodeToString(existingByReq.toResponse()))
                }
                val prev = playsByAsset[assetId]
                val used = (prev?.playsUsed ?: 0) + 1
                val left = (maxPlays - used).coerceAtLeast(0)
                val duration = assetDurations[assetId] ?: defaultDurationMs
                val track = PlayTrack(
                    playId = UUID.randomUUID(),
                    assetId = assetId,
                    playsUsed = used,
                    playsLeft = left,
                    positionMs = 0L,
                    durationMs = duration,
                    state = AttemptAudioPlayResponse.State.PLAYING,
                )
                playsByAsset[assetId] = track
                playsById[track.playId] = track
                if (requestId.isNotEmpty()) {
                    playsByRequestId[assetId to requestId] = track
                }
                return jsonReply(200, json.encodeToString(track.toResponse()))
            }
        }

        val resumeRegex = Regex("^$attemptPrefix/audio/[^/]+/plays/([^/]+)/resume$")
        resumeRegex.matchEntire(path)?.let { match ->
            if (req.method != "POST") return jsonReply(404, """{"code":"NOT_FOUND"}""")
            val playId = UUID.fromString(match.groupValues[1])
            synchronized(lock) {
                val track = playsById[playId] ?: return jsonReply(404, """{"code":"NOT_FOUND"}""")
                track.state = AttemptAudioPlayResponse.State.PLAYING
                return jsonReply(200, json.encodeToString(track.toResponse()))
            }
        }

        val progressRegex = Regex("^$attemptPrefix/audio/[^/]+/plays/([^/]+)/progress$")
        progressRegex.matchEntire(path)?.let { match ->
            if (req.method != "PATCH") return jsonReply(404, """{"code":"NOT_FOUND"}""")
            val playId = UUID.fromString(match.groupValues[1])
            val posMs = runCatching {
                json.parseToJsonElement(req.body).jsonObject["positionMs"]?.jsonPrimitive?.content?.toLong()
            }.getOrNull() ?: 0L
            synchronized(lock) {
                val track = playsById[playId] ?: return jsonReply(404, """{"code":"NOT_FOUND"}""")
                track.positionMs = maxOf(track.positionMs, posMs.coerceIn(0L, track.durationMs))
                return jsonReply(200, json.encodeToString(track.toResponse()))
            }
        }

        val endRegex = Regex("^$attemptPrefix/audio/[^/]+/plays/([^/]+)/end$")
        endRegex.matchEntire(path)?.let { match ->
            if (req.method != "POST") return jsonReply(404, """{"code":"NOT_FOUND"}""")
            val playId = UUID.fromString(match.groupValues[1])
            synchronized(lock) {
                val track = playsById[playId] ?: return jsonReply(404, """{"code":"NOT_FOUND"}""")
                track.state = AttemptAudioPlayResponse.State.ENDED
                track.positionMs = track.durationMs
                return jsonReply(
                    200,
                    json.encodeToString(
                        track.toResponse(endReason = AttemptAudioPlayResponse.EndReason.FINISHED),
                    ),
                )
            }
        }

        val assetRegex = Regex("^$attemptPrefix/assets/([^/]+)$")
        assetRegex.matchEntire(path)?.let { match ->
            if (req.method != "GET") return jsonReply(404, """{"code":"NOT_FOUND"}""")
            val auth = req.header("Authorization")
            if (auth.isNullOrBlank() || !auth.startsWith("Bearer ")) {
                return jsonReply(401, """{"code":"UNAUTHORIZED"}""")
            }
            val assetId = UUID.fromString(match.groupValues[1])
            val payload = assetPayloads[assetId] ?: return jsonReply(404, """{"code":"NOT_FOUND"}""")
            return HttpReply(200, payload.first, payload.second)
        }

        return jsonReply(404, """{"code":"NOT_FOUND"}""")
    }

    private fun PlayTrack.toResponse(
        endReason: AttemptAudioPlayResponse.EndReason? = null,
    ): AttemptAudioPlayResponse = AttemptAudioPlayResponse(
        durationMs = durationMs,
        playsLeft = playsLeft,
        playsUsed = playsUsed,
        positionMs = positionMs,
        state = state,
        endReason = endReason,
        playId = playId,
    )

    private fun readUntilHeaderEnd(input: java.io.InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        var state = 0
        while (true) {
            val b = input.read()
            if (b == -1) return if (out.size() == 0) null else out.toByteArray()
            out.write(b)
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> return out.toByteArray()
                b == '\r'.code -> 1
                else -> 0
            }
        }
    }

    private fun readExact(input: java.io.InputStream, length: Int): ByteArray {
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buf, read, length - read)
            if (n <= 0) break
            read += n
        }
        return buf
    }
}

internal data class InstrumentedAudioHarness(
    val engine: AttemptEngine,
    val gateway: OpenApiAttemptAudioGateway,
    val assetSource: AttemptAssetSource,
    val connectivity: MutableAndroidConnectivityMonitor,
    val storeDir: File,
) : AutoCloseable {
    override fun close() {
        runCatching { engine.close() }
        runCatching { assetSource.purgeAll() }
        runCatching { storeDir.deleteRecursively() }
    }
}

internal object AttachmentAndroidTestSupport {
    fun loadAssetBytes(relativePath: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets
            .open(relativePath)
            .use { it.readBytes() }

    suspend fun createHarness(
        context: Context,
        server: EmbeddedAttachmentServer,
        scope: CoroutineScope,
    ): InstrumentedAudioHarness {
        val client = OkHttpClient.Builder()
            .cache(null)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", "Bearer android-instrumented-token")
                    .build()
                chain.proceed(req)
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.baseUrl)
            .client(client)
            .addConverterFactory(
                Serializer.kotlinxSerializationJson.asConverterFactory("application/json".toMediaType()),
            )
            .build()
        val attemptsApi = retrofit.create(AttemptsApi::class.java)
        val connectivity = MutableAndroidConnectivityMonitor(true)
        val storeDir = File(context.cacheDir, "audio-engine-test-${UUID.randomUUID()}").apply { mkdirs() }
        val engine = AttemptEngine(
            attemptId = server.attemptId,
            accountId = "acc-android-audio",
            api = OpenApiAttemptApi(attemptsApi),
            store = AccountScopedStore(storeDir, "acc-android-audio"),
            connectivity = connectivity,
            scope = scope,
        )

        val deadline = System.currentTimeMillis() + 5_000L
        while (engine.state.value.session !is EditSessionState.Editing && System.currentTimeMillis() < deadline) {
            delay(25L)
        }
        require(engine.state.value.session is EditSessionState.Editing) {
            "AttemptEngine failed to enter Editing state: ${engine.state.value.session}"
        }

        val gateway = OpenApiAttemptAudioGateway(
            attemptId = server.attemptId,
            service = attemptsApi,
            activeSessionIdProvider = { (engine.state.value.session as? EditSessionState.Editing)?.sessionId },
        )
        val assetSource = AttemptAssetSource(
            attemptId = server.attemptId.toString(),
            baseUrl = server.baseUrl,
            client = client,
            cacheDir = context.cacheDir,
        )
        return InstrumentedAudioHarness(
            engine = engine,
            gateway = gateway,
            assetSource = assetSource,
            connectivity = connectivity,
            storeDir = storeDir,
        )
    }

    fun listRegularFiles(root: File?): Set<String> {
        if (root == null || !root.exists()) return emptySet()
        return root.walkTopDown()
            .filter { it.isFile && it.name != "profileInstalled" }
            .map { it.canonicalPath }
            .toSet()
    }

    fun snapshotAppStorage(context: Context): Map<String, Set<String>> = mapOf(
        "filesDir" to listRegularFiles(context.filesDir),
        "cacheDir" to listRegularFiles(context.cacheDir),
        "noBackupFilesDir" to listRegularFiles(context.noBackupFilesDir),
        "externalCacheDir" to listRegularFiles(context.externalCacheDir),
    )

    fun queryMediaStoreIds(context: Context): Map<String, Set<Long>> {
        val resolver = context.contentResolver
        fun collect(uri: android.net.Uri): Set<Long> = runCatching {
            val out = LinkedHashSet<Long>()
            resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                if (col >= 0) {
                    while (cursor.moveToNext()) {
                        out.add(ContentUris.parseId(ContentUris.withAppendedId(uri, cursor.getLong(col))))
                    }
                }
            }
            out
        }.getOrDefault(emptySet())

        return mapOf(
            "downloads" to collect(MediaStore.Downloads.EXTERNAL_CONTENT_URI),
            "images" to collect(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            "audio" to collect(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
        )
    }
}
