package vn.unicom.thi360.feature.result

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.AppLogger
import vn.unicom.thi360.testing.ContractFakeServer

class ResultLogRedactionTest {

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private suspend fun verifyRedaction(environment: Environment) {
        val secretAccess = "eyJhbGciOiJIUzI1NiJ9.SECRET_ACCESS_TOKEN_RESULT_42"
        val secretRefresh = "SECRET_REFRESH_TOKEN_RESULT_99"
        val secretStudentAnswer = "UNIQUE_STUDENT_ESSAY_SECRET_42_DO_NOT_LOG"
        val secretSolutionText = "UNIQUE_SOLUTION_TEXT_SECRET_77_DO_NOT_LOG"

        val logs = mutableListOf<String>()
        val logger = object : AppLogger {
            override fun debug(tag: String, message: String) {
                logs += "D/$tag: $message"
            }

            override fun warn(tag: String, message: String) {
                logs += "W/$tag: $message"
            }
        }

        val scoredRaw = ResultTestFixtures.readIndexedRaw("getResult", 200, "scored")
        val reviewRaw = ResultTestFixtures.readIndexedRaw("getReview", 200, "full-solution")
            .replace("Mapid context", "$secretStudentAnswer $secretSolutionText")
        val notFoundRaw = ResultTestFixtures.readIndexedRaw("getReview", 404, "attempt-not-found")

        server.respondJson(
            "GET",
            "/api/v1/me/attempts/${ResultTestFixtures.ATTEMPT_UUID_1}/result",
            200,
            scoredRaw,
        )
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/${ResultTestFixtures.ATTEMPT_UUID_1}/review",
            200,
            reviewRaw,
        )
        server.respondJson(
            "GET",
            "/api/v1/me/attempts/${ResultTestFixtures.ATTEMPT_UUID_2}/review",
            404,
            notFoundRaw,
        )

        val stack = ResultTestFixtures.createSignedInStack(
            server = server,
            environment = environment,
            accessToken = secretAccess,
            refreshToken = secretRefresh,
            logger = logger,
        )
        val repository = ResultRepository.fromSessionStack(stack)

        assertTrue(repository.result(ResultTestFixtures.ATTEMPT_UUID_1) is ApiResult.Success)
        assertTrue(repository.review(ResultTestFixtures.ATTEMPT_UUID_1) is ApiResult.Success)
        assertTrue(repository.review(ResultTestFixtures.ATTEMPT_UUID_2) is ApiResult.HttpFailure)

        assertTrue("Expected HTTP summary logs for $environment", logs.isNotEmpty())
        val combined = logs.joinToString("\n")
        assertFalse("Access token leaked in $environment logs", combined.contains(secretAccess))
        assertFalse("Refresh token leaked in $environment logs", combined.contains(secretRefresh))
        assertFalse("Student answer leaked in $environment logs", combined.contains(secretStudentAnswer))
        assertFalse("Solution text leaked in $environment logs", combined.contains(secretSolutionText))
    }

    @Test
    fun demoLogsRedactTokensAndStudentAnswers() = runTest {
        verifyRedaction(Environment.DEMO)
    }

    @Test
    fun devLogsRedactTokensAndStudentAnswers() = runTest {
        verifyRedaction(Environment.DEV)
    }
}
