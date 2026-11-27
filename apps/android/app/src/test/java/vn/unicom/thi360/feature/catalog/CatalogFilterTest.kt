package vn.unicom.thi360.feature.catalog

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class CatalogFilterTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun kindChips() {
        val h1 = PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondJson("GET", "/api/v1/public/storefront/filters", 200, CatalogTestFixtures.filtersWithPartsJson())
        }
        val h2 = PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            val fallbackBody = CatalogTestFixtures.filtersMutated { root ->
                root["catalogKinds"] = JsonArray(emptyList())
            }
            respondJson("GET", "/api/v1/public/storefront/filters", 200, fallbackBody)
        }
        val h3 = PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            val emptyKinds = CatalogTestFixtures.filtersMutated { root ->
                root["catalogKinds"] = JsonArray(emptyList())
                root["kinds"] = JsonArray(emptyList())
            }
            respondJson("GET", "/api/v1/public/storefront/filters", 200, emptyKinds)
        }

        try {
            val d1 = CatalogTestFixtures.createDependencies(h1)
            val d2 = CatalogTestFixtures.createDependencies(h2)
            val d3 = CatalogTestFixtures.createDependencies(h3)
            var activeDeps by mutableStateOf(d1)

            composeRule.setContent {
                val navController = rememberNavController()
                val navigator = androidx.compose.runtime.remember(navController) { AppNavigator(navController) }
                val currentDeps = activeDeps
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    CompositionLocalProvider(LocalCatalogDependencies provides currentDeps) {
                        CatalogRoute(
                            navigator = navigator,
                            showCompactBrandHeader = true,
                        )
                    }
                }
            }

            composeRule.waitUntilIdle {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(CatalogTestTags.kindChip("HSA")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.KIND_ALL_CHIP).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.kindChip("HSA")).assertIsDisplayed().performClick()
            composeRule.waitUntilIdle {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                h1.server.requestCount("GET", "/api/v1/public/storefront/products") == 2
            }
            val url = h1.server.lastRequest("GET", "/api/v1/public/storefront/products")!!.requestUrl!!
            assertEquals("HSA", url.queryParameter("kind"))
            assertEquals("0", url.queryParameter("page"))
            assertNull(url.queryParameter("part"))

            composeRule.runOnIdle { activeDeps = d2 }
            composeRule.waitForIdle()
            composeRule.waitUntilIdle {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                h2.server.requestCount("GET", "/api/v1/public/storefront/filters") >= 1 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.kindChip("HSA")).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.kindChip("QDA")).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag(CatalogTestTags.kindChip("HSA")).assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithTag(CatalogTestTags.kindChip("QDA")).fetchSemanticsNodes().isEmpty())

            composeRule.runOnIdle { activeDeps = d3 }
            composeRule.waitForIdle()
            composeRule.waitUntilIdle {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                h3.server.requestCount("GET", "/api/v1/public/storefront/products") >= 1 &&
                    h3.server.requestCount("GET", "/api/v1/public/storefront/filters") >= 1 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.KIND_ROW).fetchSemanticsNodes().isEmpty()
            }
            assertTrue(composeRule.onAllNodesWithTag(CatalogTestTags.KIND_ROW).fetchSemanticsNodes().isEmpty())
        } finally {
            h1.close()
            h2.close()
            h3.close()
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun formatOptions() {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 1 &&
                    harness.server.requestCount("GET", "/api/v1/public/storefront/filters") >= 1 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.LIST).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.FILTER_BUTTON).performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.formatChip("FULL")).assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.formatChip("BUNDLE")).assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithTag(CatalogTestTags.formatChip("PART")).fetchSemanticsNodes().isEmpty())
            assertTrue(composeRule.onAllNodesWithTag(CatalogTestTags.formatChip("SUBJECT")).fetchSemanticsNodes().isEmpty())
            composeRule.onNodeWithTag(CatalogTestTags.FILTER_FORMAT_HINT).assertIsDisplayed()

            composeRule.onNodeWithTag(CatalogTestTags.formatChip("BUNDLE")).performClick()
            composeRule.waitUntilIdle {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 2
            }
            val url = harness.server.lastRequest("GET", "/api/v1/public/storefront/products")!!.requestUrl!!
            assertEquals("BUNDLE", url.queryParameter("format"))
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun sort() {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 1 &&
                    harness.server.requestCount("GET", "/api/v1/public/storefront/filters") >= 1 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.LIST).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.FILTER_BUTTON).performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.sortChip(CatalogSort.PRICE_ASC))
                .performScrollTo()
                .performClick()
            composeRule.waitUntilIdle {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 2
            }
            assertEquals(
                "PRICE_ASC",
                harness.server.lastRequest("GET", "/api/v1/public/storefront/products")!!.requestUrl!!.queryParameter("sort"),
            )

            composeRule.onNodeWithTag(CatalogTestTags.sortChip(CatalogSort.PRICE_DESC))
                .performScrollTo()
                .performClick()
            composeRule.waitUntilIdle {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 3
            }
            assertEquals(
                "PRICE_DESC",
                harness.server.lastRequest("GET", "/api/v1/public/storefront/products")!!.requestUrl!!.queryParameter("sort"),
            )

            composeRule.onNodeWithTag(CatalogTestTags.FILTER_SHEET_CLOSE).performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.CLEAR_FILTERS_BUTTON).assertIsDisplayed().performClick()
            composeRule.waitUntilIdle {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 4
            }
            assertEquals(
                "NEWEST",
                harness.server.lastRequest("GET", "/api/v1/public/storefront/products")!!.requestUrl!!.queryParameter("sort"),
            )
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun partSubject() {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondJson("GET", "/api/v1/public/storefront/filters", 200, CatalogTestFixtures.filtersWithPartsJson())
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 1 &&
                    harness.server.requestCount("GET", "/api/v1/public/storefront/filters") >= 1 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.LIST).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.FILTER_BUTTON).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(CatalogTestTags.partSubjectChip("P:HSA:P1"))
                .performScrollTo()
                .assertTextContains("HSA · Tư duy định lượng")
            composeRule.onNodeWithTag(CatalogTestTags.partSubjectChip("P:QDA:Q1"))
                .performScrollTo()
                .assertTextContains("QDA · Toán QDA")

            composeRule.onNodeWithTag(CatalogTestTags.partSubjectChip("P:HSA:P1")).performClick()
            composeRule.waitUntilIdle {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 2
            }
            val afterPart = harness.server.lastRequest("GET", "/api/v1/public/storefront/products")!!.requestUrl!!
            assertEquals("HSA", afterPart.queryParameter("kind"))
            assertEquals("P1", afterPart.queryParameter("part"))
            assertNull(afterPart.queryParameter("subject"))

            composeRule.waitForIdle()
            composeRule.onNodeWithTag(CatalogTestTags.partSubjectChip("P:HSA:P1"))
                .performScrollTo()
                .assertTextContains("Tư duy định lượng")
            assertTrue(
                composeRule.onAllNodesWithTag(CatalogTestTags.partSubjectChip("P:QDA:Q1"))
                    .fetchSemanticsNodes()
                    .isEmpty(),
            )

            composeRule.onNodeWithTag(CatalogTestTags.partSubjectChip("S:TOAN"))
                .performScrollTo()
                .performClick()
            composeRule.waitUntilIdle {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 3
            }
            val afterSubject = harness.server.lastRequest("GET", "/api/v1/public/storefront/products")!!.requestUrl!!
            assertEquals("HSA", afterSubject.queryParameter("kind"))
            assertNull(afterSubject.queryParameter("part"))
            assertEquals("TOAN", afterSubject.queryParameter("subject"))
        }
    }
}
