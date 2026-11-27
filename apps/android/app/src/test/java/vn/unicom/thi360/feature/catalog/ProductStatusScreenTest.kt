package vn.unicom.thi360.feature.catalog

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class ProductStatusScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun renders410GoneWithNameAndBlankNameAnd404NotFoundWithoutCallingExamAccess() {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondJson(
                "GET",
                "/api/v1/public/storefront/products/EXAM-GONE-01",
                410,
                CatalogTestFixtures.gone410Json(blankName = false),
            )
            respondJson(
                "GET",
                "/api/v1/public/storefront/products/EXAM-GONE-BLANK",
                410,
                CatalogTestFixtures.gone410Json(blankName = true),
            )
            respondJson(
                "GET",
                "/api/v1/public/storefront/products/EXAM-NOPE-404",
                404,
                CatalogTestFixtures.notFound404Json(),
            )
        }.use { harness ->
            val feedStore = CatalogFeedStore()
            val deps = CatalogTestFixtures.createDependencies(harness, feedStore = feedStore)
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
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 1 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.kindChip("HSA")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.kindChip("HSA")).performClick()
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 2 &&
                    harness.server.requestCount("POST", "/api/v1/me/exam-access/query") >= 2
            }
            composeRule.waitForIdle()
            val accessBeforeGone = harness.server.requestCount("POST", "/api/v1/me/exam-access/query")

            composeRule.runOnIdle { navRef!!.openProduct("EXAM-GONE-01") }
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(ProductDetailTestTags.STATUS_GONE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(ProductDetailTestTags.STATUS_GONE).assertIsDisplayed()
            composeRule.onNodeWithText("MÃ LỖI 410").assertIsDisplayed()
            composeRule.onNodeWithText("Đề thi HSA đã ngừng bán DEMO (EXAM-DEMO-01)", substring = true)
                .assertIsDisplayed()
            assertEquals(
                "410 Gone must NOT call exam-access/query",
                accessBeforeGone,
                harness.server.requestCount("POST", "/api/v1/me/exam-access/query"),
            )

            val productsBeforeBack = harness.server.requestCount("GET", "/api/v1/public/storefront/products")
            composeRule.onNodeWithTag(ProductDetailTestTags.STATUS_TO_CATALOG).performClick()
            composeRule.waitForIdle()
            assertEquals("HSA", feedStore.getOrCreate("acc-catalog-1", 0L, deps).state.value.query.kind)
            assertEquals(productsBeforeBack, harness.server.requestCount("GET", "/api/v1/public/storefront/products"))

            composeRule.runOnIdle { navRef!!.openProduct("EXAM-GONE-BLANK") }
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(ProductDetailTestTags.STATUS_GONE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("EXAM-DEMO-01 (EXAM-DEMO-01)", substring = true).assertIsDisplayed()
            composeRule.onNodeWithTag(ProductDetailTestTags.STATUS_TO_MY_EXAMS).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.MyExams)).assertIsDisplayed()

            composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
            composeRule.waitForIdle()
            val accessBefore404 = harness.server.requestCount("POST", "/api/v1/me/exam-access/query")
            composeRule.runOnIdle { navRef!!.openProduct("EXAM-NOPE-404") }
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(ProductDetailTestTags.STATUS_NOT_FOUND).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ProductDetailTestTags.STATUS_NOT_FOUND).assertIsDisplayed()
            composeRule.onNodeWithText("MÃ LỖI 404").assertIsDisplayed()
            assertEquals(
                "404 NotFound must NOT call exam-access/query",
                accessBefore404,
                harness.server.requestCount("POST", "/api/v1/me/exam-access/query"),
            )
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun bundleUnavailable409() {
        // 1) 409 PRODUCT_UNAVAILABLE without product (derived per Plan A6 from certified orders-create-409-product-unavailable.json).
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondJson(
                "GET",
                "/api/v1/public/storefront/products/BUNDLE-409-PLAIN",
                409,
                CatalogTestFixtures.conflict409WithoutProductJson(),
            )
            respondJson(
                "GET",
                "/api/v1/public/storefront/products/BUNDLE-409-FULL",
                409,
                CatalogTestFixtures.conflict409WithBundleProductJson(),
            )
            respondJson(
                "GET",
                "/api/v1/public/storefront/products/EXAM-409-NONBUNDLE",
                409,
                CatalogTestFixtures.conflict409WithExamProductJson(),
            )
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
            composeRule.waitUntil(5_000) {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 1 &&
                    harness.server.requestCount("POST", "/api/v1/me/exam-access/query") >= 1
            }
            composeRule.waitForIdle()
            val accessBefore = harness.server.requestCount("POST", "/api/v1/me/exam-access/query")

            composeRule.runOnIdle { navRef!!.openProduct("BUNDLE-409-PLAIN") }
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(ProductDetailTestTags.STATUS_UNAVAILABLE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ProductDetailTestTags.STATUS_UNAVAILABLE).assertIsDisplayed()
            composeRule.onNodeWithText("TẠM NGỪNG BÁN").assertIsDisplayed()
            composeRule.onNodeWithTag(ProductDetailTestTags.BUNDLE_BUY_BUTTON).assertIsNotEnabled()
            assertEquals(accessBefore, harness.server.requestCount("POST", "/api/v1/me/exam-access/query"))

            composeRule.runOnIdle { navRef!!.openProduct("BUNDLE-409-FULL") }
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(ProductDetailTestTags.BUNDLE_UNAVAILABLE_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ProductDetailTestTags.BUNDLE_UNAVAILABLE_BANNER).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag(ProductDetailTestTags.BUNDLE_BUY_BUTTON).performScrollTo().assertIsNotEnabled()
            composeRule.onNodeWithTag(ProductDetailTestTags.BUNDLE_ITEMS_SECTION).performScrollTo().assertIsDisplayed()
            assertEquals(accessBefore, harness.server.requestCount("POST", "/api/v1/me/exam-access/query"))

            composeRule.runOnIdle { navRef!!.openProduct("EXAM-409-NONBUNDLE") }
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(ProductDetailTestTags.STATUS_UNAVAILABLE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ProductDetailTestTags.STATUS_UNAVAILABLE).assertIsDisplayed()
            assertEquals(accessBefore, harness.server.requestCount("POST", "/api/v1/me/exam-access/query"))
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun bundleSaleStateUnavailable200() {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondJson(
                "GET",
                "/api/v1/public/storefront/products/BUNDLE-200-UNAVAIL",
                200,
                CatalogTestFixtures.bundleDetail200UnavailableJson(),
            )
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
            composeRule.waitUntil(5_000) {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 1 &&
                    harness.server.requestCount("POST", "/api/v1/me/exam-access/query") >= 1
            }
            composeRule.waitForIdle()
            val accessBefore = harness.server.requestCount("POST", "/api/v1/me/exam-access/query")

            composeRule.runOnIdle { navRef!!.openProduct("BUNDLE-200-UNAVAIL") }
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(ProductDetailTestTags.BUNDLE_UNAVAILABLE_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(ProductDetailTestTags.BUNDLE_UNAVAILABLE_BANNER)
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithTag(ProductDetailTestTags.BUNDLE_BUY_BUTTON)
                .performScrollTo()
                .assertIsNotEnabled()
                .assertTextContains("Mua bộ đề")
            assertEquals(
                "200 saleState=UNAVAILABLE bundle must NOT call exam-access/query",
                accessBefore,
                harness.server.requestCount("POST", "/api/v1/me/exam-access/query"),
            )
        }
    }
}
