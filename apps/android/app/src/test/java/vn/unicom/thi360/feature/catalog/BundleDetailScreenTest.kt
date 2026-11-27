package vn.unicom.thi360.feature.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.RoutePlaceholderTestTags
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class BundleDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun rendersFourItemsAccessChipsOwnedAndPendingNotesAndNavigatesToChildExamAndCheckout() {
        val baseBundle = CatalogTestFixtures.json.parseToJsonElement(CatalogTestFixtures.bundleDetailJson()).jsonObject
        val baseItems = baseBundle["items"]!!.jsonArray
        val item1 = baseItems[0].jsonObject
        val item2 = baseItems[1].jsonObject
        val item3 = JsonObject(
            item1.toMutableMap().apply {
                put("examId", JsonPrimitive("00000000-0000-7000-8000-000000000009"))
                put("code", JsonPrimitive("EXAM-DEMO-05"))
                put("name", JsonPrimitive("Môn Vật lí"))
                put("scope", JsonPrimitive("SUBJECT"))
                put("partCode", JsonNull)
            },
        )
        val item4 = JsonObject(
            item2.toMutableMap().apply {
                put("examId", JsonPrimitive("00000000-0000-7000-8000-000000000010"))
                put("code", JsonPrimitive("EXAM-MISSING-404"))
                put("name", JsonPrimitive("Bài ẩn trong bộ"))
            },
        )
        val fourItemBundle = CatalogTestFixtures.bundleDetailJson { root ->
            root["items"] = JsonArray(listOf(item1, item2, item3, item4))
        }

        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondJson("GET", "/api/v1/public/storefront/products/BUNDLE-DEMO-01", 200, fourItemBundle)
            respondJson(
                "GET",
                "/api/v1/public/storefront/products/EXAM-MISSING-404",
                404,
                CatalogTestFixtures.notFound404Json(),
            )
            respondDynamic("POST", "/api/v1/me/exam-access/query") { req ->
                val reqCodes = CatalogTestFixtures.parseAccessRequestCodes(req.body.clone().readUtf8())
                val body = CatalogTestFixtures.accessResponseForCodes(reqCodes) { code, obj ->
                    if (code == "BUNDLE-DEMO-01") {
                        obj["state"] = JsonPrimitive("NONE")
                        obj["items"] = buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("examId", "00000000-0000-7000-8000-000000000001")
                                    put("code", "EXAM-DEMO-02")
                                    put("name", "Phần Tư duy định lượng")
                                    put("state", "CAN_START")
                                    put("attemptsLeft", 2)
                                    put("entitlementId", "00000000-0000-7000-8000-000000000081")
                                    put("activeAttemptId", JsonNull)
                                    put("hasPendingFulfillment", false)
                                    put("pendingOrderCode", JsonNull)
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("examId", "00000000-0000-7000-8000-000000000003")
                                    put("code", "EXAM-DEMO-04")
                                    put("name", "Môn Ngữ văn")
                                    put("state", "CHECKING")
                                    put("attemptsLeft", 0)
                                    put("entitlementId", JsonNull)
                                    put("activeAttemptId", JsonNull)
                                    put("hasPendingFulfillment", true)
                                    put("pendingOrderCode", "DH261126000077")
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("examId", "00000000-0000-7000-8000-000000000009")
                                    put("code", "EXAM-DEMO-05")
                                    put("name", "Môn Vật lí")
                                    put("state", "CHECKING")
                                    put("attemptsLeft", 0)
                                    put("entitlementId", JsonNull)
                                    put("activeAttemptId", JsonNull)
                                    put("hasPendingFulfillment", true)
                                    put("pendingOrderCode", JsonNull)
                                },
                            )
                        }
                    }
                }
                ContractFakeServer.jsonResponse(200, body)
            }
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            var navRef: AppNavigator? = null

            composeRule.setContent {
                val navController = rememberNavController()
                val appNav = AppNavigator(navController)
                navRef = appNav
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        navController = navController,
                        navigator = appNav,
                    )
                }
            }

            composeRule.runOnIdle { navRef!!.openProduct("BUNDLE-DEMO-01") }
            composeRule.waitUntil(5_000) {
                harness.server.requestCount("POST", "/api/v1/me/exam-access/query") >= 1
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(ProductDetailTestTags.BUNDLE_BUY_BUTTON)
                .performScrollTo()
                .assertIsDisplayed()
                .assertIsEnabled()
                .assertTextContains("Mua bộ đề")
            assertTrue(composeRule.onAllNodesWithText("Tiếp tục").fetchSemanticsNodes().isEmpty())
            assertTrue(composeRule.onAllNodesWithText("Vào bài thi").fetchSemanticsNodes().isEmpty())

            composeRule.onNodeWithTag(ProductDetailTestTags.BUNDLE_OWNED_NOTES).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Bạn đã có 2 lượt bài Phần Tư duy định lượng; mua bộ sẽ cộng thêm lượt")
                .assertIsDisplayed()
            composeRule.onNodeWithTag(ProductDetailTestTags.BUNDLE_PENDING_NOTES).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Đơn DH261126000077 cho bài Môn Ngữ văn đang được kiểm tra", substring = true)
                .assertIsDisplayed()
            composeRule.onNodeWithText("Có đơn đang được kiểm tra cho bài Môn Vật lí", substring = true)
                .assertIsDisplayed()

            composeRule.onNodeWithTag(ProductDetailTestTags.bundleItemAccessChip("EXAM-DEMO-02"))
                .performScrollTo()
                .assertTextContains("Còn 2 lượt")
            composeRule.onNodeWithTag(ProductDetailTestTags.bundleItemAccessChip("EXAM-DEMO-04"))
                .performScrollTo()
                .assertTextContains("Đang kiểm tra")
            composeRule.onNodeWithTag(ProductDetailTestTags.bundleItemRow("EXAM-DEMO-04"))
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onAllNodesWithText("Ngữ văn · Bài thi theo môn").onFirst().assertIsDisplayed()

            composeRule.onNodeWithTag(ProductDetailTestTags.BUNDLE_HOW_SECTION).performScrollTo().assertIsDisplayed()

            composeRule.onNodeWithTag(ProductDetailTestTags.bundleItemTitle("EXAM-MISSING-404"))
                .performScrollTo()
                .performClick()
            composeRule.waitUntil(5_000) {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products/EXAM-MISSING-404") == 1
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ProductDetailTestTags.STATUS_NOT_FOUND).assertIsDisplayed()

            composeRule.onNodeWithTag(ProductDetailTestTags.BACK_BUTTON).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ProductDetailTestTags.BUNDLE_BUY_BUTTON).performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertTextContains("checkout")
            composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS)
                .assertTextContains("code=BUNDLE-DEMO-01", substring = true)
                .assertTextContains("seenVersionNo=1", substring = true)
                .assertTextContains("seenPrice=320000", substring = true)
        }
    }
}
