package vn.unicom.thi360.feature.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class CatalogSearchTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun debouncesRapidTypingTrimsBlankSendsOverlongQueryAndSupportsImmediateImeSearch() {
        val pendingDebounces = CopyOnWriteArrayList<Pair<Long, CompletableDeferred<Unit>>>()

        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(
                harness = harness,
                debounceWaiter = { ms ->
                    val gate = CompletableDeferred<Unit>()
                    pendingDebounces.add(ms to gate)
                    gate.await()
                },
            )

            composeRule.setContent {
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }

            composeRule.waitUntil(5_000) {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 1
            }
            composeRule.waitForIdle()

            val searchNode = composeRule.onNodeWithTag(CatalogTestTags.SEARCH_FIELD)
            searchNode.performTextInput("H")
            searchNode.performTextInput("S")
            searchNode.performTextInput("A")
            searchNode.performTextInput("-")
            searchNode.performTextInput("1")
            composeRule.waitForIdle()

            assertEquals(
                "Rapid typing within 400ms must not fire extra product requests before debounce finishes",
                1,
                harness.server.requestCount("GET", "/api/v1/public/storefront/products"),
            )
            assertTrue(pendingDebounces.isNotEmpty())
            assertEquals(400L, pendingDebounces.last().first)

            pendingDebounces.last().second.complete(Unit)
            composeRule.waitUntil(5_000) {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 2
            }
            val debouncedPath = harness.server.lastRequest("GET", "/api/v1/public/storefront/products")!!.path.orEmpty()
            assertTrue("Expected q=HSA-1 in $debouncedPath", debouncedPath.contains("q=HSA-1"))
            assertTrue("Expected page=0 in $debouncedPath", debouncedPath.contains("page=0"))

            searchNode.performTextReplacement("TOAN-99")
            searchNode.performImeAction()
            composeRule.waitUntil(5_000) {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 3
            }
            val imePath = harness.server.lastRequest("GET", "/api/v1/public/storefront/products")!!.path.orEmpty()
            assertTrue("Expected q=TOAN-99 in $imePath", imePath.contains("q=TOAN-99"))

            searchNode.performTextReplacement("   ")
            searchNode.performImeAction()
            composeRule.waitUntil(5_000) {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 4
            }
            val clearedPath = harness.server.lastRequest("GET", "/api/v1/public/storefront/products")!!.path.orEmpty()
            assertFalse("Whitespace-only search must omit q param: $clearedPath", clearedPath.contains("q="))

            val overlong = "X".repeat(145)
            searchNode.performTextReplacement(overlong)
            searchNode.performImeAction()
            composeRule.waitUntil(5_000) {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products") == 5
            }
            val overlongReq = harness.server.lastRequest("GET", "/api/v1/public/storefront/products")!!
            val qValue = overlongReq.requestUrl?.queryParameter("q").orEmpty()
            assertEquals(145, qValue.length)
            assertEquals(overlong, qValue)
        }
    }
}
