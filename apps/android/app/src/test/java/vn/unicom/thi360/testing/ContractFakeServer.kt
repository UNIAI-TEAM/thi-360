package vn.unicom.thi360.testing

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import vn.unicom.thi360.core.network.ContractFixtureLoader

class ContractFakeServer(
    installDefaultMeHandler: Boolean = true,
) {
    private data class RouteKey(val method: String, val path: String)

    private data class PrefixRoute(
        val method: String?,
        val prefix: String,
        val handler: (RecordedRequest) -> MockResponse,
    )

    private data class RegexRoute(
        val method: String?,
        val regex: Regex,
        val handler: (RecordedRequest) -> MockResponse,
    )

    private val exactHandlers = ConcurrentHashMap<RouteKey, (RecordedRequest) -> MockResponse>()
    private val exactQueues = ConcurrentHashMap<RouteKey, ConcurrentLinkedQueue<(RecordedRequest) -> MockResponse>>()
    private val faultQueues = ConcurrentHashMap<RouteKey, ConcurrentLinkedQueue<(RecordedRequest) -> MockResponse>>()
    private val prefixRoutes = CopyOnWriteArrayList<PrefixRoute>()
    private val regexRoutes = CopyOnWriteArrayList<RegexRoute>()
    private val recorded = CopyOnWriteArrayList<RecordedRequest>()
    private val fallbackCount = AtomicInteger(0)

    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorded.add(request)
                val method = request.method.orEmpty().uppercase()
                val rawPath = request.path.orEmpty()
                val cleanPath = rawPath.substringBefore('?')

                val fullKey = RouteKey(method, rawPath)
                val cleanKey = RouteKey(method, cleanPath)

                faultQueues[fullKey]?.poll()?.let { return it(request) }
                faultQueues[cleanKey]?.poll()?.let { return it(request) }

                exactQueues[fullKey]?.poll()?.let { return it(request) }
                exactQueues[cleanKey]?.poll()?.let { return it(request) }

                exactHandlers[fullKey]?.let { return it(request) }
                exactHandlers[cleanKey]?.let { return it(request) }

                for (route in regexRoutes) {
                    if ((route.method == null || route.method == method) &&
                        (route.regex.matches(cleanPath) || route.regex.matches(rawPath))
                    ) {
                        return route.handler(request)
                    }
                }
                for (route in prefixRoutes) {
                    if ((route.method == null || route.method == method) &&
                        cleanPath.startsWith(route.prefix)
                    ) {
                        return route.handler(request)
                    }
                }

                fallbackCount.incrementAndGet()
                return MockResponse()
                    .setResponseCode(404)
                    .addHeader("Content-Type", "application/json")
                    .setBody("""{"code":"ROUTE_NOT_MOCKED","message":"No handler for $method $rawPath"}""")
            }
        }
    }

    init {
        if (installDefaultMeHandler) {
            respondApiFixture("GET", "/api/v1/me", 200, "me-200.json")
        }
    }

    fun start(): ContractFakeServer {
        server.start()
        return this
    }

    fun shutdown() {
        server.shutdown()
    }

    fun url(path: String = "/"): String = server.url(path).toString()

    fun hostName(): String = server.hostName

    fun port(): Int = server.port

    fun respond(method: String, path: String, response: () -> MockResponse): ContractFakeServer {
        exactHandlers[RouteKey(method.uppercase(), path)] = { response() }
        return this
    }

    fun respondDynamic(
        method: String,
        path: String,
        handler: (RecordedRequest) -> MockResponse,
    ): ContractFakeServer {
        exactHandlers[RouteKey(method.uppercase(), path)] = handler
        return this
    }

    fun respondJson(
        method: String,
        path: String,
        statusCode: Int,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): ContractFakeServer = respond(method, path) {
        buildJsonResponse(statusCode, body, headers)
    }

    fun respondEmpty(
        method: String,
        path: String,
        statusCode: Int,
        headers: Map<String, String> = emptyMap(),
    ): ContractFakeServer = respond(method, path) {
        MockResponse().setResponseCode(statusCode).apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }
    }

    fun respondAuthFixture(
        method: String,
        path: String,
        statusCode: Int,
        fixtureName: String,
        transform: (String) -> String = { it },
    ): ContractFakeServer {
        val body = transform(ContractFixtureLoader.readAuthFixture(fixtureName))
        return respondJson(method, path, statusCode, body)
    }

    fun respondApiFixture(
        method: String,
        path: String,
        statusCode: Int,
        fixtureName: String,
        transform: (String) -> String = { it },
    ): ContractFakeServer {
        val body = transform(ContractFixtureLoader.readApiFixture(fixtureName))
        return respondJson(method, path, statusCode, body)
    }

    fun respondAppearanceFixture(
        method: String,
        path: String,
        statusCode: Int,
        fixtureName: String,
        transform: (String) -> String = { it },
    ): ContractFakeServer {
        val body = transform(ContractFixtureLoader.readAppearanceFixture(fixtureName))
        return respondJson(method, path, statusCode, body)
    }

    fun respondIndexed(
        operationId: String,
        statusCode: Int,
        pathOverride: String? = null,
        fixtureNameFilter: String? = null,
        transform: (String) -> String = { it },
    ): ContractFakeServer {
        val entry = ContractFixtureIndex.find(operationId, statusCode, fixtureNameFilter)
        val body = transform(entry.file.readText(Charsets.UTF_8))
        val targetPath = pathOverride ?: entry.path
        return respondJson(entry.method, targetPath, entry.statusCode, body)
    }

    fun respondSequence(
        method: String,
        path: String,
        vararg responses: () -> MockResponse,
    ): ContractFakeServer {
        val queue = ConcurrentLinkedQueue<(RecordedRequest) -> MockResponse>()
        responses.forEach { supplier -> queue.add { supplier() } }
        exactQueues[RouteKey(method.uppercase(), path)] = queue
        return this
    }

    fun respondDynamicSequence(
        method: String,
        path: String,
        handlers: List<(RecordedRequest) -> MockResponse>,
    ): ContractFakeServer {
        val queue = ConcurrentLinkedQueue<(RecordedRequest) -> MockResponse>()
        handlers.forEach { queue.add(it) }
        exactQueues[RouteKey(method.uppercase(), path)] = queue
        return this
    }

    fun enqueueOnRoute(
        method: String,
        path: String,
        response: () -> MockResponse,
    ): ContractFakeServer {
        val key = RouteKey(method.uppercase(), path)
        val queue = exactQueues.computeIfAbsent(key) { ConcurrentLinkedQueue() }
        queue.add { response() }
        return this
    }

    fun enqueueDynamicOnRoute(
        method: String,
        path: String,
        handler: (RecordedRequest) -> MockResponse,
    ): ContractFakeServer {
        val key = RouteKey(method.uppercase(), path)
        val queue = exactQueues.computeIfAbsent(key) { ConcurrentLinkedQueue() }
        queue.add(handler)
        return this
    }

    fun respondPathPrefix(
        method: String? = null,
        prefix: String,
        handler: (RecordedRequest) -> MockResponse,
    ): ContractFakeServer {
        prefixRoutes.add(PrefixRoute(method?.uppercase(), prefix, handler))
        return this
    }

    fun respondRegex(
        method: String? = null,
        regex: Regex,
        handler: (RecordedRequest) -> MockResponse,
    ): ContractFakeServer {
        regexRoutes.add(RegexRoute(method?.uppercase(), regex, handler))
        return this
    }

    fun injectNetworkError(
        method: String,
        path: String,
        count: Int = 1,
        policy: SocketPolicy = SocketPolicy.DISCONNECT_AT_START,
    ): ContractFakeServer {
        val key = RouteKey(method.uppercase(), path)
        val queue = faultQueues.computeIfAbsent(key) { ConcurrentLinkedQueue() }
        repeat(count) {
            queue.add { MockResponse().setSocketPolicy(policy) }
        }
        return this
    }

    fun inject401(
        method: String,
        path: String,
        count: Int = 1,
        body: String = """{"code":"UNAUTHORIZED","message":"Unauthorized"}""",
    ): ContractFakeServer {
        val key = RouteKey(method.uppercase(), path)
        val queue = faultQueues.computeIfAbsent(key) { ConcurrentLinkedQueue() }
        repeat(count) {
            queue.add { buildJsonResponse(401, body) }
        }
        return this
    }

    fun inject429(
        method: String,
        path: String,
        retryAfterHeaderSeconds: Long? = null,
        bodyRetryAfterSeconds: Long? = null,
        count: Int = 1,
    ): ContractFakeServer {
        val key = RouteKey(method.uppercase(), path)
        val queue = faultQueues.computeIfAbsent(key) { ConcurrentLinkedQueue() }
        val body = if (bodyRetryAfterSeconds != null) {
            """{"code":"RATE_LIMITED","message":"Too many requests","retryAfter":$bodyRetryAfterSeconds}"""
        } else {
            """{"code":"RATE_LIMITED","message":"Too many requests"}"""
        }
        val headers = if (retryAfterHeaderSeconds != null) {
            mapOf("Retry-After" to retryAfterHeaderSeconds.toString())
        } else {
            emptyMap()
        }
        repeat(count) {
            queue.add { buildJsonResponse(429, body, headers) }
        }
        return this
    }

    fun requests(method: String? = null, path: String? = null): List<RecordedRequest> {
        val normalizedMethod = method?.uppercase()
        return recorded.filter { req ->
            val methodMatch = normalizedMethod == null || req.method.equals(normalizedMethod, ignoreCase = true)
            val reqPath = req.path.orEmpty()
            val pathMatch = path == null ||
                reqPath == path ||
                reqPath.substringBefore('?') == path
            methodMatch && pathMatch
        }
    }

    fun requestCount(method: String? = null, path: String? = null): Int =
        requests(method, path).size

    fun lastRequest(method: String? = null, path: String? = null): RecordedRequest? =
        requests(method, path).lastOrNull()

    val totalRequestCount: Int
        get() = recorded.size

    fun resetRecordedRequests() {
        recorded.clear()
    }

    fun clearRoutes() {
        exactHandlers.clear()
        exactQueues.clear()
        faultQueues.clear()
        prefixRoutes.clear()
        regexRoutes.clear()
    }

    companion object {
        fun jsonResponse(
            statusCode: Int,
            body: String,
            headers: Map<String, String> = emptyMap(),
        ): MockResponse = buildJsonResponse(statusCode, body, headers)

        fun authFixtureResponse(
            statusCode: Int,
            fixtureName: String,
            transform: (String) -> String = { it },
        ): MockResponse = buildJsonResponse(
            statusCode,
            transform(ContractFixtureLoader.readAuthFixture(fixtureName)),
        )

        fun apiFixtureResponse(
            statusCode: Int,
            fixtureName: String,
            transform: (String) -> String = { it },
        ): MockResponse = buildJsonResponse(
            statusCode,
            transform(ContractFixtureLoader.readApiFixture(fixtureName)),
        )

        private fun buildJsonResponse(
            statusCode: Int,
            body: String,
            headers: Map<String, String> = emptyMap(),
        ): MockResponse = MockResponse()
            .setResponseCode(statusCode)
            .addHeader("Content-Type", "application/json")
            .apply {
                headers.forEach { (k, v) -> addHeader(k, v) }
            }
            .setBody(body)
    }
}

data class IndexedFixtureEntry(
    val fileName: String,
    val file: File,
    val method: String,
    val path: String,
    val operationId: String,
    val statusCode: Int,
    val schema: String?,
    val variant: String = "",
)

internal object ContractFixtureIndex {
    private val json = Json { ignoreUnknownKeys = true }

    fun indexFile(): File {
        val specDir = ContractFixtureLoader.openApiSpecFile().parentFile
            ?: error("Cannot locate contracts/mobile directory")
        return File(specDir, "api/index.json")
    }

    fun exists(): Boolean = indexFile().isFile

    fun loadAll(): List<IndexedFixtureEntry> {
        val file = indexFile()
        require(file.isFile) { "Missing fixture index: ${file.canonicalPath}" }
        val apiDir = checkNotNull(file.parentFile) { "Missing parent directory for ${file.canonicalPath}" }
        val specDir = checkNotNull(apiDir.parentFile) { "Missing contracts/mobile directory for ${apiDir.canonicalPath}" }
        val root = json.parseToJsonElement(file.readText(Charsets.UTF_8))
        val items: List<JsonElement> = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                val arrayCandidate = root["fixtures"] ?: root["entries"] ?: root["items"]
                if (arrayCandidate is JsonArray) {
                    arrayCandidate
                } else {
                    root.entries.mapNotNull { (key, value) ->
                        if (value is JsonObject) {
                            JsonObject(value + ("file" to kotlinx.serialization.json.JsonPrimitive(key)))
                        } else {
                            null
                        }
                    }
                }
            }
            else -> error("Unsupported index.json structure")
        }

        return items.map { element ->
            val obj = element.jsonObject
            val fileName = obj.stringField("file", "fixture", "name", "filename")
                ?: error("Missing fixture file name in index entry: $obj")
            val method = (obj.stringField("method", "httpMethod") ?: "GET").uppercase()
            val path = obj.stringField("path", "endpoint", "url")
                ?: error("Missing path in index entry for $fileName")
            val operationId = obj.stringField("operationId", "operation") ?: ""
            val statusCode = obj.intField("status", "statusCode", "httpStatus")
                ?: error("Missing statusCode in index entry for $fileName")
            val schema = obj.stringField("schema", "model", "responseSchema", "responseModel")
            val variant = obj.stringField("variant") ?: ""
            val resolvedFile = if (fileName.contains('/')) File(specDir, fileName) else File(apiDir, fileName)
            IndexedFixtureEntry(
                fileName = fileName,
                file = resolvedFile,
                method = method,
                path = path,
                operationId = operationId,
                statusCode = statusCode,
                schema = schema,
                variant = variant,
            )
        }
    }

    fun find(operationId: String, statusCode: Int, fixtureNameFilter: String? = null): IndexedFixtureEntry {
        val entries = loadAll()
        return entries.firstOrNull { entry ->
            entry.operationId == operationId &&
                entry.statusCode == statusCode &&
                (
                    fixtureNameFilter == null ||
                        entry.variant == fixtureNameFilter ||
                        entry.fileName.contains(fixtureNameFilter)
                    )
        } ?: error("No indexed fixture matching operationId=$operationId status=$statusCode filter=$fixtureNameFilter")
    }

    private fun JsonObject.stringField(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } }

    private fun JsonObject.intField(vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.intOrNull }
}
