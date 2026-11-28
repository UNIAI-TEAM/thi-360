package vn.unicom.thi360.feature.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.prepare.PrepareTestTags
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.AppRoute
import vn.unicom.thi360.shell.routes.RoutePlaceholderTestTags
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class CatalogNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun singleDetailOnDoubleTap() {
        val now = AtomicLong(1_000L)
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness, clockMillis = { now.get() })
            var navControllerRef: NavHostController? = null

            composeRule.setContent {
                val navController = rememberNavController()
                navControllerRef = navController
                val navigator = AppNavigator(navController)
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        navController = navController,
                        navigator = navigator,
                    )
                }
            }

            composeRule.waitUntilIdle {
                harness.server.requestCount("POST", "/api/v1/me/exam-access/query") >= 1 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.productViewDetail("EXAM-DEMO-01"))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            val viewDetailBtn = composeRule.onNodeWithTag(CatalogTestTags.productViewDetail("EXAM-DEMO-01"))
            viewDetailBtn.performClick()
            now.addAndGet(100L)
            runCatching { viewDetailBtn.performClick() }
            composeRule.waitUntilIdle {
                harness.server.requestCount("GET", "/api/v1/public/storefront/products/EXAM-DEMO-01") == 1 &&
                    composeRule.onAllNodesWithTag(ProductDetailTestTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(ProductDetailTestTags.SCREEN).assertIsDisplayed()
            assertEquals(1, harness.server.requestCount("GET", "/api/v1/public/storefront/products/EXAM-DEMO-01"))

            composeRule.onNodeWithTag(ProductDetailTestTags.BACK_BUTTON).performClick()
            composeRule.waitUntilIdle {
                composeRule.onAllNodesWithTag(CatalogTestTags.LIST).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(CatalogTestTags.LIST).assertIsDisplayed()
            assertEquals("home/catalog", navControllerRef?.currentDestination?.route)
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun accessTargets() {
        val now = AtomicLong(1_000L)
        val codes = listOf("EX-NONE", "EX-START", "EX-START-NULL", "EX-CONT", "EX-CONT-NULL")
        val pageJson = CatalogTestFixtures.productsPageFromCertifiedCard(
            codes = codes,
            page = 0,
            totalItems = codes.size.toLong(),
            totalPages = 1,
        ) { _, obj ->
            obj["versionNo"] = JsonPrimitive(3)
            obj["price"] = JsonPrimitive(175_000)
        }

        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondJson("GET", "/api/v1/public/storefront/products", 200, pageJson)
            respondDynamic("POST", "/api/v1/me/exam-access/query") { req ->
                val reqCodes = CatalogTestFixtures.parseAccessRequestCodes(req.body.clone().readUtf8())
                val body = CatalogTestFixtures.accessResponseForCodes(reqCodes) { code, obj ->
                    when (code) {
                        "EX-NONE" -> obj["state"] = JsonPrimitive("NONE")
                        "EX-START" -> {
                            obj["state"] = JsonPrimitive("CAN_START")
                            obj["attemptsLeft"] = JsonPrimitive(2)
                            obj["entitlementId"] = JsonPrimitive("00000000-0000-7000-8000-000000000011")
                        }
                        "EX-START-NULL" -> {
                            obj["state"] = JsonPrimitive("CAN_START")
                            obj["attemptsLeft"] = JsonPrimitive(2)
                            obj["entitlementId"] = JsonNull
                        }
                        "EX-CONT" -> {
                            obj["state"] = JsonPrimitive("IN_PROGRESS")
                            obj["attemptsLeft"] = JsonPrimitive(1)
                            obj["activeAttemptId"] = JsonPrimitive("00000000-0000-7000-8000-000000000022")
                        }
                        "EX-CONT-NULL" -> {
                            obj["state"] = JsonPrimitive("IN_PROGRESS")
                            obj["attemptsLeft"] = JsonPrimitive(1)
                            obj["activeAttemptId"] = JsonNull
                        }
                    }
                }
                ContractFakeServer.jsonResponse(200, body)
            }
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness, clockMillis = { now.get() })
            var navigatorRef: AppNavigator? = null

            composeRule.setContent {
                val navController = rememberNavController()
                val appNav = AppNavigator(navController)
                navigatorRef = appNav
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        navController = navController,
                        navigator = appNav,
                    )
                }
            }

            composeRule.waitUntilIdle {
                harness.server.requestCount("POST", "/api/v1/me/exam-access/query") >= 1 &&
                    composeRule.onAllNodesWithTag(CatalogTestTags.productPrimaryAction("EX-NONE"))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(CatalogTestTags.productPrimaryAction("EX-NONE")).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_SCREEN).assertIsDisplayed()
            val checkoutArgs = navigatorRef!!.navController.currentBackStackEntry?.arguments
            assertEquals("EX-NONE", checkoutArgs?.getString(AppRoute.ARG_CODE))
            assertEquals("3", checkoutArgs?.getString(AppRoute.ARG_SEEN_VERSION_NO))
            assertEquals("175000", checkoutArgs?.getString(AppRoute.ARG_SEEN_PRICE))
            composeRule.runOnIdle { navigatorRef!!.back() }
            now.addAndGet(1_000L)

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EX-START")
            composeRule.onNodeWithTag(CatalogTestTags.productPrimaryAction("EX-START")).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PrepareTestTags.SCREEN).assertIsDisplayed()
            val prepareArgs = navigatorRef!!.navController.currentBackStackEntry?.arguments
            assertEquals(
                "00000000-0000-7000-8000-000000000011",
                prepareArgs?.getString(AppRoute.ARG_ENTITLEMENT_ID),
            )
            composeRule.runOnIdle { navigatorRef!!.back() }
            now.addAndGet(1_000L)

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EX-START-NULL")
            composeRule.onNodeWithTag(CatalogTestTags.productPrimaryAction("EX-START-NULL")).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.MyExams)).assertIsDisplayed()
            composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
            composeRule.waitForIdle()
            now.addAndGet(1_000L)

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EX-CONT")
            composeRule.onNodeWithTag(CatalogTestTags.productPrimaryAction("EX-CONT")).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(vn.unicom.thi360.feature.player.PlayerTestTags.SCREEN).assertIsDisplayed()
            val attemptArgs = navigatorRef!!.navController.currentBackStackEntry?.arguments
            assertEquals(
                "00000000-0000-7000-8000-000000000022",
                attemptArgs?.getString(AppRoute.ARG_ATTEMPT_ID),
            )
            composeRule.runOnIdle { navigatorRef!!.closeAttempt() }
            composeRule.waitForIdle()
            now.addAndGet(1_000L)

            composeRule.onNodeWithTag(CatalogTestTags.LIST).performScrollToKey("EX-CONT-NULL")
            composeRule.onNodeWithTag(CatalogTestTags.productPrimaryAction("EX-CONT-NULL")).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.MyExams)).assertIsDisplayed()
        }
    }
}
