package vn.unicom.thi360.feature.catalog

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import coil3.request.ImageRequest
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.image.Thi360ImageLoader
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class CoverImageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun validPngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    @Test
    fun thi360ImageLoaderOmitsAuthorizationHeaderForPublicStorefrontCovers() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val png = validPngBytes()

        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            respond("GET", "/api/v1/public/storefront/covers/cover-1") {
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(png))
            }
            respond("GET", "/media/avatar.png") {
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(png))
            }
        }.use { harness ->
            val loader = Thi360ImageLoader.create(
                context = context,
                apiBaseUrl = harness.server.url("/"),
                sessionStack = harness.stack,
            )
            try {
                val coverUrl = CatalogFormatting.resolveCoverUrl(
                    harness.server.url("/"),
                    "/api/v1/public/storefront/covers/cover-1",
                )!!
                loader.imageLoader.execute(
                    ImageRequest.Builder(context).data(coverUrl).build(),
                )
                val coverReq = harness.server.lastRequest("GET", "/api/v1/public/storefront/covers/cover-1")!!
                assertNull(
                    "Public storefront cover must NOT carry Authorization header",
                    coverReq.getHeader("Authorization"),
                )

                loader.imageLoader.execute(
                    ImageRequest.Builder(context).data(harness.server.url("/media/avatar.png")).build(),
                )
                val avatarReq = harness.server.lastRequest("GET", "/media/avatar.png")!!
                assertEquals("Bearer catalog-access-token", avatarReq.getHeader("Authorization"))
            } finally {
                loader.close()
            }
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun bundleSkipsImageRequestFailedCoverFallsBackWithoutSizeChangeAndCoverHasNoContentDescription() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val png = validPngBytes()
        val pageJson = CatalogTestFixtures.productsPageFromCertifiedCard(
            codes = listOf("EX-COVER-OK", "EX-COVER-404", "BD-COVER-SKIP"),
            page = 0,
            totalItems = 3,
            totalPages = 1,
        ) { idx, obj ->
            when (idx) {
                0 -> obj["coverUrl"] = JsonPrimitive("/api/v1/public/storefront/covers/ok.png")
                1 -> obj["coverUrl"] = JsonPrimitive("/api/v1/public/storefront/covers/missing.png")
                2 -> {
                    obj["format"] = JsonPrimitive("BUNDLE")
                    obj["productType"] = JsonPrimitive("BUNDLE")
                    obj["coverUrl"] = JsonPrimitive("/api/v1/public/storefront/covers/should-never-load.png")
                }
            }
        }

        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondJson("GET", "/api/v1/public/storefront/products", 200, pageJson)
            respond("GET", "/api/v1/public/storefront/covers/ok.png") {
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(png))
            }
            respond("GET", "/api/v1/public/storefront/covers/missing.png") {
                MockResponse().setResponseCode(404)
            }
        }.use { harness ->
            val loader = Thi360ImageLoader.create(
                context = context,
                apiBaseUrl = harness.server.url("/"),
                sessionStack = harness.stack,
            )
            try {
                val deps = CatalogTestFixtures.createDependencies(harness, imageLoader = loader.imageLoader)
                composeRule.setContent {
                    CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                        Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                    }
                }

                composeRule.waitUntil(5_000) {
                    harness.server.requestCount("GET", "/api/v1/public/storefront/covers/ok.png") >= 1
                }
                composeRule.waitForIdle()

                val okReq = harness.server.lastRequest("GET", "/api/v1/public/storefront/covers/ok.png")!!
                assertNull(okReq.getHeader("Authorization"))
                val coverNode = composeRule.onNodeWithTag(
                    CatalogTestTags.productCover("EX-COVER-OK"),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                val contentDesc = coverNode.config.getOrNull(SemanticsProperties.ContentDescription)
                assertTrue(
                    "Decorative cover image must not set contentDescription",
                    contentDesc.isNullOrEmpty(),
                )
                val okBounds = composeRule.onNodeWithTag(CatalogTestTags.productCard("EX-COVER-OK"))
                    .getUnclippedBoundsInRoot()

                composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EX-COVER-404")
                composeRule.waitUntil(5_000) {
                    harness.server.requestCount("GET", "/api/v1/public/storefront/covers/missing.png") >= 1
                }
                composeRule.waitForIdle()
                composeRule.onNodeWithTag(
                    CatalogTestTags.productCoverFallback("EX-COVER-404"),
                    useUnmergedTree = true,
                ).assertIsDisplayed()
                val fallbackBounds = composeRule.onNodeWithTag(CatalogTestTags.productCard("EX-COVER-404"))
                    .getUnclippedBoundsInRoot()
                assertTrue(
                    "Fallback card width (${fallbackBounds.width}) should match normal card (${okBounds.width})",
                    abs(fallbackBounds.width.value - okBounds.width.value) <= 2f,
                )
                assertTrue(
                    "Fallback card height (${fallbackBounds.height}) should match normal card (${okBounds.height})",
                    abs(fallbackBounds.height.value - okBounds.height.value) <= 2f,
                )

                composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("BD-COVER-SKIP")
                composeRule.waitForIdle()
                composeRule.onNodeWithTag(
                    CatalogTestTags.productCoverFallback("BD-COVER-SKIP"),
                    useUnmergedTree = true,
                ).assertIsDisplayed()
                assertEquals(
                    0,
                    harness.server.requestCount("GET", "/api/v1/public/storefront/covers/should-never-load.png"),
                )
            } finally {
                loader.close()
            }
        }
    }
}
