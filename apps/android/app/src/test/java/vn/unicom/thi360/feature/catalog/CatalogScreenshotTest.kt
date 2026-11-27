package vn.unicom.thi360.feature.catalog

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.image.Thi360ImageLoader
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CatalogScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun assertNoHorizontalOverflow() {
        val root = composeRule.onRoot().fetchSemanticsNode()
        val maxRight = root.boundsInRoot.right + 1.5f
        fun check(node: SemanticsNode) {
            val bounds = node.boundsInRoot
            if (bounds.width > 0f && bounds.height > 0f) {
                assertTrue(
                    "Node ${node.id} overflows horizontally: right=${bounds.right} > maxRight=$maxRight",
                    bounds.right <= maxRight,
                )
            }
            node.children.forEach(::check)
        }
        check(root)
    }

    private fun captureCatalog(snapshotName: String, themeMode: ThemeMode) {
        val deps = CatalogTestFixtures.createDeterministicFixtureDependencies()
        composeRule.setContent {
            CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                Thi360App(
                    preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode),
                )
            }
        }
        composeRule.waitUntil(5_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(CatalogTestTags.productPrimaryAction("EXAM-DEMO-01"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        assertNoHorizontalOverflow()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$snapshotName.png")
    }

    private fun captureProductRoute(
        snapshotName: String,
        code: String,
        themeMode: ThemeMode,
        readyTag: String,
        outcomeOverride: ProductOutcome? = null,
    ) {
        val baseDeps = CatalogTestFixtures.createDeterministicFixtureDependencies()
        val deps = if (outcomeOverride == null) {
            baseDeps
        } else {
            val delegate = baseDeps.gateway
            baseDeps.copy(
                gateway = object : CatalogGateway by delegate {
                    override suspend fun getProductDetail(code: String): CatalogDetailResult =
                        CatalogDetailResult.Success(outcomeOverride)
                },
            )
        }
        var navRef: AppNavigator? = null
        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navRef = appNav
            CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                Thi360App(
                    preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode),
                    navController = navController,
                    navigator = appNav,
                )
            }
        }
        composeRule.runOnIdle { navRef!!.openProduct(code) }
        composeRule.waitUntil(5_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(readyTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        assertNoHorizontalOverflow()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$snapshotName.png")
    }

    private fun deterministicCoverPng(): ByteArray {
        val bmp = Bitmap.createBitmap(160, 60, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF047857.toInt())
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun catalog_360x640_light() = captureCatalog("catalog_360x640_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun catalog_360x640_dark() = captureCatalog("catalog_360x640_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun catalog_412x915_light() = captureCatalog("catalog_412x915_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun catalog_412x915_dark() = captureCatalog("catalog_412x915_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun catalog_840x720_light() = captureCatalog("catalog_840x720_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun catalog_840x720_dark() = captureCatalog("catalog_840x720_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun catalog_1280x800_light() = captureCatalog("catalog_1280x800_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun catalog_1280x800_dark() = captureCatalog("catalog_1280x800_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun exam_detail_412x915_light() = captureProductRoute(
        snapshotName = "exam_detail_412x915_light",
        code = "EXAM-DEMO-01",
        themeMode = ThemeMode.LIGHT,
        readyTag = ProductDetailTestTags.PRODUCT_CODE,
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun exam_detail_412x915_dark() = captureProductRoute(
        snapshotName = "exam_detail_412x915_dark",
        code = "EXAM-DEMO-01",
        themeMode = ThemeMode.DARK,
        readyTag = ProductDetailTestTags.PRODUCT_CODE,
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun exam_detail_with_cover_412x915_light() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val png = deterministicCoverPng()
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            val withCover = CatalogTestFixtures.examDetailJson { root ->
                root["coverUrl"] = JsonPrimitive("/api/v1/public/storefront/covers/exam-hero-1")
            }
            respondJson("GET", "/api/v1/public/storefront/products/EXAM-DEMO-01", 200, withCover)
            respond("GET", "/api/v1/public/storefront/covers/exam-hero-1") {
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
                val deps = CatalogTestFixtures.createDependencies(
                    harness = harness,
                    imageLoader = loader.imageLoader,
                )
                var navRef: AppNavigator? = null
                composeRule.setContent {
                    val navController = rememberNavController()
                    val appNav = AppNavigator(navController)
                    navRef = appNav
                    CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                        Thi360App(
                            preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT),
                            navController = navController,
                            navigator = appNav,
                        )
                    }
                }
                composeRule.runOnIdle { navRef!!.openProduct("EXAM-DEMO-01") }
                composeRule.waitUntil(5_000) {
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    composeRule.onAllNodesWithTag(ProductDetailTestTags.PRODUCT_CODE)
                        .fetchSemanticsNodes().isNotEmpty() &&
                        composeRule.onAllNodesWithTag(ProductDetailTestTags.HERO_BRAND_PLACEHOLDER)
                            .fetchSemanticsNodes().isEmpty()
                }
                composeRule.waitForIdle()
                assertNoHorizontalOverflow()
                composeRule.onRoot().captureRoboImage("src/test/snapshots/exam_detail_with_cover_412x915_light.png")
            } finally {
                loader.imageLoader.shutdown()
            }
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun exam_detail_840x720_light() = captureProductRoute(
        snapshotName = "exam_detail_840x720_light",
        code = "EXAM-DEMO-01",
        themeMode = ThemeMode.LIGHT,
        readyTag = ProductDetailTestTags.PRODUCT_CODE,
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun bundle_detail_412x915_light() = captureProductRoute(
        snapshotName = "bundle_detail_412x915_light",
        code = "BUNDLE-DEMO-01",
        themeMode = ThemeMode.LIGHT,
        readyTag = ProductDetailTestTags.BUNDLE_BUY_BUTTON,
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun bundle_detail_412x915_dark() = captureProductRoute(
        snapshotName = "bundle_detail_412x915_dark",
        code = "BUNDLE-DEMO-01",
        themeMode = ThemeMode.DARK,
        readyTag = ProductDetailTestTags.BUNDLE_BUY_BUTTON,
    )

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun bundle_detail_1280x800_light() = captureProductRoute(
        snapshotName = "bundle_detail_1280x800_light",
        code = "BUNDLE-DEMO-01",
        themeMode = ThemeMode.LIGHT,
        readyTag = ProductDetailTestTags.BUNDLE_BUY_BUTTON,
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun product_status_410_412x915_light() = captureProductRoute(
        snapshotName = "product_status_410_412x915_light",
        code = "EXAM-GONE-01",
        themeMode = ThemeMode.LIGHT,
        readyTag = ProductDetailTestTags.STATUS_GONE,
        outcomeOverride = ProductOutcome.Gone(
            code = "EXAM-DEMO-01",
            name = "Đề thi HSA đã ngừng bán DEMO",
        ),
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun product_status_404_412x915_dark() = captureProductRoute(
        snapshotName = "product_status_404_412x915_dark",
        code = "EXAM-NOT-FOUND",
        themeMode = ThemeMode.DARK,
        readyTag = ProductDetailTestTags.STATUS_NOT_FOUND,
        outcomeOverride = ProductOutcome.NotFound,
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun product_status_409_412x915_light() = captureProductRoute(
        snapshotName = "product_status_409_412x915_light",
        code = "BUNDLE-UNAVAILABLE-01",
        themeMode = ThemeMode.LIGHT,
        readyTag = ProductDetailTestTags.STATUS_UNAVAILABLE,
        outcomeOverride = ProductOutcome.BundleUnavailable,
    )
}
