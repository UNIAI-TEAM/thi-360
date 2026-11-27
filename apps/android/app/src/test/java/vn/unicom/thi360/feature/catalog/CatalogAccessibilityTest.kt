package vn.unicom.thi360.feature.catalog

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertTrue
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
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class CatalogAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun fontScale130NoClippingTouchTargets48DpAndTalkBackListPriceLabel() {
        verifyCatalogAccessibility(fontScale = 1.3f, checkNoClipping = true)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun fontScale200ScrollableAndPrimaryActionsClickable() {
        verifyCatalogAccessibility(fontScale = 2.0f, checkNoClipping = false)
    }

    private fun verifyCatalogAccessibility(fontScale: Float, checkNoClipping: Boolean) {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            composeRule.setContent {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = base.density, fontScale = fontScale),
                    LocalCatalogDependencies provides deps,
                ) {
                    Thi360App(preferences = AppPreferences(locale = AppLocale.VI))
                }
            }
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(CatalogTestTags.productPrimaryAction("EXAM-DEMO-01"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            val titleNode = composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home))
                .fetchSemanticsNode()
            assertTrue(titleNode.config.contains(SemanticsProperties.Heading))

            val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
            assertTouchTargetAndBounds(CatalogTestTags.FILTER_BUTTON, rootBounds, fontScale, checkNoClipping)

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EXAM-DEMO-01")
            composeRule.waitForIdle()
            val listPriceNode = composeRule.onNodeWithTag(CatalogTestTags.productListPrice("EXAM-DEMO-01"))
                .fetchSemanticsNode()
            val listPriceDesc = listPriceNode.config.getOrNull(SemanticsProperties.ContentDescription)
                .orEmpty()
                .joinToString(" ")
            assertTrue(
                "Expected 'Giá gốc' in listPrice accessibility label, got '$listPriceDesc'",
                listPriceDesc.contains("Giá gốc"),
            )

            var attempts = 0
            while (attempts < 4) {
                val visible = runCatching {
                    composeRule.onNodeWithTag(CatalogTestTags.productViewDetail("EXAM-DEMO-01")).assertIsDisplayed()
                    true
                }.getOrDefault(false)
                if (visible) break
                composeRule.onNodeWithTag(CatalogTestTags.LIST).performTouchInput {
                    swipeUp(startY = bottom * 0.85f, endY = bottom * 0.45f)
                }
                composeRule.waitForIdle()
                attempts++
            }

            assertTouchTargetAndBounds(CatalogTestTags.productPrimaryAction("EXAM-DEMO-01"), rootBounds, fontScale, checkNoClipping)
            assertTouchTargetAndBounds(CatalogTestTags.productViewDetail("EXAM-DEMO-01"), rootBounds, fontScale, checkNoClipping)
        }
    }

    private fun assertTouchTargetAndBounds(
        tag: String,
        rootBounds: androidx.compose.ui.unit.DpRect,
        fontScale: Float,
        checkNoClipping: Boolean,
    ) {
        val node = composeRule.onNodeWithTag(tag)
        node.assertIsDisplayed()
        node.assertHasClickAction()
        val touchBounds = node.fetchSemanticsNode().touchBoundsInRoot
        val wDp = with(composeRule.density) { touchBounds.width.toDp() }
        val hDp = with(composeRule.density) { touchBounds.height.toDp() }
        assertTrue("$tag touch width=$wDp must be >= 48dp", wDp >= 47.5.dp)
        assertTrue("$tag touch height=$hDp must be >= 48dp", hDp >= 47.5.dp)
        if (checkNoClipping) {
            val b = node.getUnclippedBoundsInRoot()
            assertTrue("$tag clips right edge at fontScale=$fontScale", b.right <= rootBounds.right + 1.dp)
        }
    }
}
