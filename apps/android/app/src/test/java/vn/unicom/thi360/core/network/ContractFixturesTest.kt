package vn.unicom.thi360.core.network

import java.io.File
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType
import kotlin.coroutines.Continuation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.EmailCodeErrorResponse
import vn.unicom.thi360.core.network.generated.model.MobileTokenResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.ContractFixtureIndex

class ContractFixturesTest {

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private data class SpecOperation(
        val method: String,
        val path: String,
        val operationId: String,
        val responseSchemas: Map<Int, String?>,
    )

    private data class GeneratedEndpoint(
        val method: String,
        val path: String,
        val methodName: String,
        val responseType: java.lang.reflect.Type?,
    )

    private fun parseSpecOperations(): List<SpecOperation> {
        val specText = ContractFixtureLoader.openApiSpecFile().readText(Charsets.UTF_8)
        val specJson = Json.parseToJsonElement(specText).jsonObject
        val pathsObj = specJson["paths"]!!.jsonObject
        val httpMethods = setOf("get", "post", "put", "patch", "delete")
        val ops = mutableListOf<SpecOperation>()

        for ((path, pathItemElement) in pathsObj) {
            val pathItem = pathItemElement.jsonObject
            for (httpMethod in httpMethods) {
                val opObj = pathItem[httpMethod]?.jsonObject ?: continue
                val opId = opObj["operationId"]?.jsonPrimitive?.content.orEmpty()
                val responsesObj = opObj["responses"]?.jsonObject ?: JsonObject(emptyMap())
                val schemas = linkedMapOf<Int, String?>()
                for ((codeStr, respEl) in responsesObj) {
                    val status = codeStr.toIntOrNull() ?: continue
                    val ref = respEl.jsonObject["content"]?.jsonObject
                        ?.get("application/json")?.jsonObject
                        ?.get("schema")?.jsonObject
                        ?.get("\$ref")?.jsonPrimitive?.content
                    schemas[status] = ref?.substringAfterLast('/')
                }
                ops += SpecOperation(
                    method = httpMethod.uppercase(),
                    path = path,
                    operationId = opId,
                    responseSchemas = schemas,
                )
            }
        }
        return ops
    }

    private fun discoverGeneratedEndpoints(): List<GeneratedEndpoint> {
        val generatedApiDir = listOf(
            File("build/generated/openapi/src/main/kotlin/vn/unicom/thi360/core/network/generated/api"),
            File("app/build/generated/openapi/src/main/kotlin/vn/unicom/thi360/core/network/generated/api"),
        ).firstOrNull { it.isDirectory }
            ?: error("Generated OpenAPI api directory not found")

        val apiClasses = generatedApiDir.listFiles { f -> f.isFile && f.extension == "kt" }
            .orEmpty()
            .map { file ->
                val className = "vn.unicom.thi360.core.network.generated.api.${file.nameWithoutExtension}"
                Class.forName(className)
            }

        val endpoints = mutableListOf<GeneratedEndpoint>()
        for (apiClass in apiClasses) {
            for (method in apiClass.declaredMethods) {
                val httpInfo: Pair<String, String>? = when {
                    method.isAnnotationPresent(GET::class.java) -> "GET" to method.getAnnotation(GET::class.java)!!.value
                    method.isAnnotationPresent(POST::class.java) -> "POST" to method.getAnnotation(POST::class.java)!!.value
                    method.isAnnotationPresent(PUT::class.java) -> "PUT" to method.getAnnotation(PUT::class.java)!!.value
                    method.isAnnotationPresent(PATCH::class.java) -> "PATCH" to method.getAnnotation(PATCH::class.java)!!.value
                    method.isAnnotationPresent(DELETE::class.java) -> "DELETE" to method.getAnnotation(DELETE::class.java)!!.value
                    else -> null
                }
                if (httpInfo != null) {
                    val normalizedPath = if (httpInfo.second.startsWith("/")) httpInfo.second else "/${httpInfo.second}"
                    endpoints += GeneratedEndpoint(
                        method = httpInfo.first,
                        path = normalizedPath,
                        methodName = method.name,
                        responseType = extractSuspendResponseBodyType(method),
                    )
                }
            }
        }
        return endpoints
    }

    private fun extractSuspendResponseBodyType(method: java.lang.reflect.Method): java.lang.reflect.Type? {
        val lastParam = method.genericParameterTypes.lastOrNull() as? ParameterizedType ?: return null
        if (lastParam.rawType != Continuation::class.java) return null
        val contArg = unwrapWildcard(lastParam.actualTypeArguments.firstOrNull()) as? ParameterizedType ?: return null
        if (contArg.rawType != Response::class.java) return null
        return unwrapWildcard(contArg.actualTypeArguments.firstOrNull())
    }

    private fun unwrapWildcard(type: java.lang.reflect.Type?): java.lang.reflect.Type? = when (type) {
        is WildcardType -> type.lowerBounds.firstOrNull() ?: type.upperBounds.firstOrNull()
        else -> type
    }

    @Test
    fun generatedClientCoversEveryContractPath() {
        val specOps = parseSpecOperations()
        val specPaths = specOps.map { it.path }.toSet()
        val phase1Paths = setOf(
            "/api/v1/auth/change-password",
            "/api/v1/auth/mobile/login",
            "/api/v1/auth/mobile/logout",
            "/api/v1/auth/mobile/refresh",
            "/api/v1/auth/mobile/register/verify",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/register",
            "/api/v1/auth/register/resend",
            "/api/v1/me",
            "/api/v1/me/profile",
        )
        for (requiredPath in phase1Paths) {
            assertTrue("Phase-1 contract path missing from OpenAPI spec: $requiredPath", requiredPath in specPaths)
        }

        val generatedEndpoints = discoverGeneratedEndpoints()
        val generatedPairs = generatedEndpoints.map { it.method to it.path }.toSet()
        for (op in specOps) {
            assertTrue(
                "Generated client missing operation ${op.method} ${op.path} (${op.operationId})",
                (op.method to op.path) in generatedPairs,
            )
        }
    }

    @Test
    fun indexCoversFolder() {
        val indexFile = ContractFixtureIndex.indexFile()
        assertTrue(
            "Missing contracts/mobile/api/index.json (waiting for BE-1 merge): ${indexFile.canonicalPath}",
            indexFile.isFile,
        )
        val specOps = parseSpecOperations()
        val specByMethodPath = specOps.associateBy { it.method to it.path }
        val specByOpId = specOps.filter { it.operationId.isNotBlank() }.associateBy { it.operationId }

        val entries = ContractFixtureIndex.loadAll()
        assertTrue("index.json must contain fixture entries", entries.isNotEmpty())

        val indexedFiles = entries.map { it.fileName }.toSet()
        val folderFiles = ContractFixtureLoader.apiFixtureFiles()
            .map { it.name }
            .filter { it != "index.json" }
            .toSet()

        val missingFromIndex = folderFiles - indexedFiles
        assertTrue(
            "Fixture files present in contracts/mobile/api/ but missing from index.json: $missingFromIndex",
            missingFromIndex.isEmpty(),
        )
        val missingOnDisk = indexedFiles - folderFiles
        assertTrue(
            "Fixture files listed in index.json but missing on disk: $missingOnDisk",
            missingOnDisk.isEmpty(),
        )

        for (entry in entries) {
            val opMatches = (entry.method to entry.path) in specByMethodPath ||
                (entry.operationId.isNotBlank() && entry.operationId in specByOpId)
            assertTrue(
                "index.json entry ${entry.fileName} points to unknown operation ${entry.method} ${entry.path} (${entry.operationId})",
                opMatches,
            )
        }
    }

    @Test
    fun everyIndexedFixtureDecodes() {
        val indexFile = ContractFixtureIndex.indexFile()
        assertTrue(
            "Missing contracts/mobile/api/index.json (waiting for BE-1 merge): ${indexFile.canonicalPath}",
            indexFile.isFile,
        )

        val generatedJson = Serializer.kotlinxSerializationJson
        val authFixtures = ContractFixtureLoader.authFixtureFiles()
        assertTrue("Expected at least 15 auth fixtures, found ${authFixtures.size}", authFixtures.size >= 15)
        for (file in authFixtures) {
            val body = file.readText(Charsets.UTF_8)
            when {
                file.name.contains("-200") || file.name.contains("-201") -> {
                    val parsed = generatedJson.decodeFromString(MobileTokenResponse.serializer(), body)
                    assertTrue(parsed.accessToken.isNotBlank())
                    assertTrue(parsed.refreshToken.isNotBlank())
                    assertTrue(parsed.refreshExpiresIn > 0)
                }

                file.name.contains("email-code") -> {
                    val parsed = generatedJson.decodeFromString(EmailCodeErrorResponse.serializer(), body)
                    assertTrue(parsed.code.isNotBlank())
                    assertNotNull(ApiErrorParser.parse(body))
                }

                else -> {
                    val parsed = ApiErrorParser.parse(body)
                    assertNotNull("Failed to parse auth error fixture ${file.name}", parsed)
                    assertTrue(parsed!!.code.isNotBlank())
                }
            }
        }

        val specOps = parseSpecOperations()
        val specByMethodPath = specOps.associateBy { it.method to it.path }
        val generatedByMethodPath = discoverGeneratedEndpoints().associateBy { it.method to it.path }
        val entries = ContractFixtureIndex.loadAll()

        for (entry in entries) {
            val body = entry.file.readText(Charsets.UTF_8)
            if (entry.statusCode in 200..299) {
                if (body.isBlank()) continue
                val serializer: KSerializer<Any> = resolveSuccessSerializer(
                    entry = entry,
                    specOp = specByMethodPath[entry.method to entry.path],
                    endpoint = generatedByMethodPath[entry.method to entry.path],
                )
                val decoded = generatedJson.decodeFromString(serializer, body)
                assertNotNull("Failed to decode ${entry.fileName} with generated model", decoded)
            } else {
                val parsed = ApiErrorParser.parse(body)
                assertNotNull("Failed to parse error fixture ${entry.fileName} as ApiError", parsed)
                assertTrue("Blank error code in ${entry.fileName}", parsed!!.code.isNotBlank())
            }
        }
    }

    private fun resolveSuccessSerializer(
        entry: vn.unicom.thi360.testing.IndexedFixtureEntry,
        specOp: SpecOperation?,
        endpoint: GeneratedEndpoint?,
    ): KSerializer<Any> {
        val modelPkg = "vn.unicom.thi360.core.network.generated.model"
        val schemaName = entry.schema ?: specOp?.responseSchemas?.get(entry.statusCode)
        if (!schemaName.isNullOrBlank()) {
            val candidate = runCatching { Class.forName("$modelPkg.$schemaName") }.getOrNull()
            if (candidate != null) {
                return Serializer.kotlinxSerializationJson.serializersModule.serializer(candidate)
            }
        }
        val responseType = endpoint?.responseType
            ?: error("Cannot resolve generated response model for ${entry.fileName} (${entry.method} ${entry.path})")
        return Serializer.kotlinxSerializationJson.serializersModule.serializer(responseType)
    }

    @Test
    fun generatedApisSendAcceptLanguageAndBearerHeadersAgainstContractFixtures() = runTest {
        var locale = AppLocale.VI
        val store = InMemoryTokenStore(
            Tokens(
                accessToken = "bearer-token-123",
                refreshToken = "refresh-token-456",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
            ),
        )
        val noOpLogger = object : AppLogger {
            override fun debug(tag: String, message: String) = Unit
            override fun warn(tag: String, message: String) = Unit
        }
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = store,
            localeProvider = { locale },
            logger = noOpLogger,
        )

        server.respondAuthFixture("POST", "/api/v1/auth/mobile/login", 200, "login-200.json")
        server.respondApiFixture("GET", "/api/v1/me", 200, "me-200.json")
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")

        stack.sessionManager.login("student.demo", "SecretPass1!")
        val loginReq = server.requests("POST", "/api/v1/auth/mobile/login").single()
        assertEquals("vi", loginReq.getHeader("Accept-Language"))
        assertFalse("Public login must not attach Authorization header", loginReq.getHeader("Authorization") != null)

        locale = AppLocale.EN
        val meResp = stack.authenticatedAuthApi.me()
        assertTrue(meResp.isSuccessful)
        assertEquals("student.demo", meResp.body()?.username)
        val meReq = server.lastRequest("GET", "/api/v1/me")!!
        assertEquals("en", meReq.getHeader("Accept-Language"))
        assertEquals("Bearer fixture-access-token", meReq.getHeader("Authorization"))

        val profileResp = stack.profileApi.getMyProfile()
        assertTrue(profileResp.isSuccessful)
        assertEquals("student.demo@thi360.vn", profileResp.body()?.email)
        val profileReq = server.requests("GET", "/api/v1/me/profile").single()
        assertEquals("en", profileReq.getHeader("Accept-Language"))
        assertEquals("Bearer fixture-access-token", profileReq.getHeader("Authorization"))
    }

    @Test
    fun recoveryGatewayMatchesRegistrationOtpAndPasswordContractFixtures() = runTest {
        var locale = AppLocale.EN
        val store = InMemoryTokenStore(
            Tokens(
                accessToken = "bearer-change-pw",
                refreshToken = "refresh-change-pw",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
            ),
        )
        val noOpLogger = object : AppLogger {
            override fun debug(tag: String, message: String) = Unit
            override fun warn(tag: String, message: String) = Unit
        }
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = store,
            localeProvider = { locale },
            logger = noOpLogger,
        )
        val gateway = vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway(
            publicAuthApi = stack.publicAuthApi,
            authenticatedAuthApi = stack.authenticatedAuthApi,
        )

        server.respondApiFixture("POST", "/api/v1/auth/register", 200, "register-200.json")
        server.respondApiFixture("POST", "/api/v1/auth/register/resend", 429, "register-resend-429-rate-limited.json")
        server.respondSequence(
            "POST",
            "/api/v1/auth/mobile/register/verify",
            { ContractFakeServer.authFixtureResponse(422, "register-verify-422-email-code-invalid.json") },
            { ContractFakeServer.authFixtureResponse(201, "register-verify-201.json") },
        )
        server.respondApiFixture("POST", "/api/v1/auth/password/forgot", 202, "password-forgot-202.json")
        server.respondApiFixture("POST", "/api/v1/auth/password/reset", 422, "password-reset-422-reset-token-invalid.json")
        server.respondApiFixture("POST", "/api/v1/auth/change-password", 400, "change-password-400-wrong-old-password.json")

        val start = gateway.startRegistration("Nguyen Van An", "student.demo", "student.demo@thi360.vn", "Pass1234!", "2026-11-26")
        assertTrue(start is ApiResult.Success)
        val startData = (start as ApiResult.Success).data
        assertEquals(60L, startData.resendAfterSeconds)
        val regReq = server.requests("POST", "/api/v1/auth/register").single()
        assertEquals("en", regReq.getHeader("Accept-Language"))
        assertFalse(regReq.getHeader("Authorization") != null)

        val resend429 = gateway.resendRegistrationCode(startData.registrationId) as ApiResult.HttpFailure
        assertEquals(429, resend429.statusCode)
        assertEquals(60L, resend429.error?.retryAfter)

        val verifyInvalid = gateway.verifyRegistration(startData.registrationId, "000000", "student.demo") as ApiResult.HttpFailure
        assertEquals(422, verifyInvalid.statusCode)
        assertEquals("EMAIL_CODE_INVALID", verifyInvalid.error?.code)
        assertEquals(4, verifyInvalid.error?.remainingAttempts)

        val verifyOk = gateway.verifyRegistration(startData.registrationId, "123456", "student.demo") as ApiResult.Success
        assertEquals("fixture-access-token", verifyOk.data.accessToken)

        val forgotOk = gateway.forgotPassword("student.demo@thi360.vn")
        assertTrue(forgotOk is ApiResult.Success)

        val resetInvalid = gateway.resetPassword("expired", "NewPass1!") as ApiResult.HttpFailure
        assertEquals("RESET_TOKEN_INVALID", resetInvalid.error?.code)

        val changeWrong = gateway.changePassword("Wrong1!", "NewPass1!") as ApiResult.HttpFailure
        assertEquals("WRONG_OLD_PASSWORD", changeWrong.error?.code)
        val changeReq = server.requests("POST", "/api/v1/auth/change-password").single()
        assertEquals("Bearer bearer-change-pw", changeReq.getHeader("Authorization"))

        val appearanceRaw = ContractFixtureLoader.readAppearanceFixture("tokens-thi360-green.json")
        val appearanceParsed = vn.unicom.thi360.core.appearance.AppearanceTokensParser.parse(appearanceRaw)
        assertNotNull("Appearance contract fixture must parse cleanly", appearanceParsed)
        assertEquals(vn.unicom.thi360.core.theme.BrandPalette.Default, appearanceParsed!!.palette)
    }
}
