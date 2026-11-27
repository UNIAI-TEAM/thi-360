package vn.unicom.thi360.feature.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class ProductCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun rendersChipsRangeStatsBundleStatsUnknownFormatFallbackAndNavigatesFromCoverTitleAndDetail() {
        val openedCodes = mutableListOf<String>()
        val pageJson = CatalogTestFixtures.productsPageFromCertifiedCard(
            codes = listOf("EXAM-RANGE-01", "BUNDLE-CARD-01", "EXAM-CUSTOM-01"),
            page = 0,
            totalItems = 3,
            totalPages = 1,
        ) { idx, obj ->
            when (idx) {
                0 -> {
                    obj["name"] = JsonPrimitive("Đề thi HSA có tên rất dài để kiểm thử giới hạn hai dòng trên thẻ danh mục")
                    obj["format"] = JsonPrimitive("PART")
                    obj["buildMode"] = JsonPrimitive("REVIEW")
                    obj["scopeLabel"] = buildJsonObject {
                        put("nameVi", "Tư duy định lượng")
                        put("nameEn", "Quantitative")
                    }
                    obj["questionCount"] = buildJsonObject {
                        put("min", 48)
                        put("max", 50)
                    }
                    obj["durationMinutes"] = buildJsonObject {
                        put("min", 75)
                        put("max", 75)
                    }
                    obj["attemptsGranted"] = JsonPrimitive(2)
                    obj["productType"] = JsonPrimitive("EXAM")
                }

                1 -> {
                    obj["name"] = JsonPrimitive("Bộ đề HSA 4 bài")
                    obj["format"] = JsonPrimitive("BUNDLE")
                    obj["productType"] = JsonPrimitive("BUNDLE")
                    obj["attemptsGranted"] = JsonPrimitive(4)
                    obj["promotion"] = kotlinx.serialization.json.JsonNull
                }

                2 -> {
                    obj["name"] = JsonPrimitive("Bài định dạng mới")
                    obj["format"] = JsonPrimitive("CUSTOM_FUTURE_FORMAT")
                    obj["productType"] = JsonPrimitive("EXAM")
                }
            }
        }

        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondJson("GET", "/api/v1/public/storefront/products", 200, pageJson)
            respondPathPrefix("GET", "/api/v1/public/storefront/products/") { req ->
                val code = req.path.orEmpty().substringAfterLast("/").substringBefore("?")
                openedCodes += code
                ContractFakeServer.jsonResponse(200, CatalogTestFixtures.examDetailJson())
            }
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }

            composeRule.waitUntil(5_000) {
                harness.server.requestCount("POST", "/api/v1/me/exam-access/query") >= 1
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Bài thi theo phần", useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithText("Tư duy định lượng", useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithText("Bài ôn", useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.productStats("EXAM-RANGE-01"))
                .assertTextContains("48–50 câu · 75 phút · 2 lượt")

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("BUNDLE-CARD-01")
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(CatalogTestTags.productStats("BUNDLE-CARD-01"))
                .assertTextContains("4 lượt mỗi bài", substring = true)

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EXAM-CUSTOM-01")
            composeRule.waitForIdle()
            composeRule.onNodeWithText("CUSTOM_FUTURE_FORMAT", useUnmergedTree = true).assertIsDisplayed()

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EXAM-RANGE-01")
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.productCoverFallback("EXAM-RANGE-01"), useUnmergedTree = true)
                .performTouchInput { click() }
            composeRule.waitUntil(5_000) { openedCodes.size == 1 }
            composeRule.onNodeWithTag(ProductDetailTestTags.BACK_BUTTON).performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.productTitle("EXAM-RANGE-01"), useUnmergedTree = true)
                .performTouchInput { click() }
            composeRule.waitUntil(5_000) { openedCodes.size == 2 }
            composeRule.onNodeWithTag(ProductDetailTestTags.BACK_BUTTON).performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.productViewDetail("EXAM-RANGE-01")).performClick()
            composeRule.waitUntil(5_000) { openedCodes.size == 3 }

            assertEquals(listOf("EXAM-RANGE-01", "EXAM-RANGE-01", "EXAM-RANGE-01"), openedCodes)
        }
    }
}
