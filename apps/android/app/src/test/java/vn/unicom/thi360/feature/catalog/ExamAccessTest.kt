package vn.unicom.thi360.feature.catalog

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class ExamAccessTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun loadingPlaceholderHidesBuyButtonMissingCodeFallsBackToNoneAndFailedBatchRetriesSameCodes() {
        val accessGate = CompletableDeferred<Unit>()
        val failFirstAccess = AtomicBoolean(true)
        val pageCodes = (1..12).map { "AC-${it.toString().padStart(2, '0')}" }
        val pageJson = CatalogTestFixtures.productsPageFromCertifiedCard(
            codes = pageCodes,
            page = 0,
            totalItems = 12,
            totalPages = 1,
        )

        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondJson("GET", "/api/v1/public/storefront/products", 200, pageJson)
            respondDynamic("POST", "/api/v1/me/exam-access/query") { req ->
                val reqCodes = CatalogTestFixtures.parseAccessRequestCodes(req.body.clone().readUtf8())
                if (failFirstAccess.getAndSet(false)) {
                    ContractFakeServer.jsonResponse(400, CatalogTestFixtures.examAccess400Json())
                } else {
                    ContractFakeServer.jsonResponse(
                        200,
                        CatalogTestFixtures.accessResponseForCodes(reqCodes.filter { it != "AC-01" }),
                    )
                }
            }
        }.use { harness ->
            val realGateway = OpenApiCatalogGateway.create(harness.server.url("/"), harness.stack)
            val gatedGateway = object : CatalogGateway by realGateway {
                override suspend fun queryExamAccess(codes: List<String>): ApiResult<List<ExamAccessRow>> {
                    accessGate.await()
                    return realGateway.queryExamAccess(codes)
                }
            }
            val deps = CatalogTestFixtures.createDependencies(harness).copy(gateway = gatedGateway)
            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }

            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.productAccessLoading("AC-01"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("AC-01")
            composeRule.onNodeWithTag(CatalogTestTags.LIST).performTouchInput {
                swipeUp(startY = bottom * 0.65f, endY = bottom * 0.40f, durationMillis = 1000)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(CatalogTestTags.productAccessLoading("AC-01")).assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithText("Mua bài thi").fetchSemanticsNodes().isEmpty())

            accessGate.complete(Unit)
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                harness.server.requestCount("POST", "/api/v1/me/exam-access/query") == 1 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.productAccessError("AC-01"))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            val firstBatchCodes = CatalogTestFixtures.parseAccessRequestCodes(
                harness.server.lastRequest("POST", "/api/v1/me/exam-access/query")!!.body.clone().readUtf8(),
            )
            assertEquals(pageCodes, firstBatchCodes)

            composeRule.onNodeWithTag(CatalogTestTags.productAccessError("AC-01")).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.productAccessRetry("AC-01")).assertIsDisplayed().performClick()

            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                harness.server.requestCount("POST", "/api/v1/me/exam-access/query") == 2 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.productPrimaryAction("AC-01"))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.productPrimaryAction("AC-01"))
                .assertIsDisplayed()
                .assertTextContains("Mua bài thi")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun handles401RefreshAndRetriesExamAccess() {
        PortalTestHarness.create(initialTokens = CatalogTestFixtures.activeStudentTokens()) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            inject401("POST", "/api/v1/me/exam-access/query", count = 1)
            respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }
            composeRule.waitUntilIdle {
                harness.server.requestCount("POST", "/api/v1/auth/mobile/refresh") >= 1 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.productPrimaryAction("EXAM-DEMO-01"))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            assertTrue(harness.stack.sessionManager.state.value is SessionState.Active)
            composeRule.onNodeWithTag(CatalogTestTags.productPrimaryAction("EXAM-DEMO-01"))
                .assertIsDisplayed()
                .assertTextContains("Vào bài thi")
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun handles401RefreshFailureSignsOutWithExpiredReason() {
        PortalTestHarness.create(initialTokens = CatalogTestFixtures.activeStudentTokens()) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            inject401("POST", "/api/v1/me/exam-access/query", count = 2)
            respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 401, "refresh-401-session-revoked.json")
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }
            composeRule.waitUntilIdle {
                harness.stack.sessionManager.state.value is SessionState.SignedOut
            }
            val signedOut = harness.stack.sessionManager.state.value as SessionState.SignedOut
            assertEquals(SignOutReason.Expired, signedOut.reason)
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun handles403RoleDowngradePerA77() {
        PortalTestHarness.create(initialTokens = CatalogTestFixtures.activeStudentTokens()) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondEmpty("POST", "/api/v1/me/exam-access/query", 403)
            respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 403, "refresh-403-app-student-only.json")
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }
            composeRule.waitUntilIdle {
                harness.stack.sessionManager.state.value is SessionState.SignedOut
            }
            val signedOut = harness.stack.sessionManager.state.value as SessionState.SignedOut
            assertTrue(
                "Expected SignOutReason.StudentOnly after 403 -> refresh APP_STUDENT_ONLY, got ${signedOut.reason}",
                signedOut.reason is SignOutReason.StudentOnly,
            )
        }
    }
}
