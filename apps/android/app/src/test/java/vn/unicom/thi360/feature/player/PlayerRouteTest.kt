package vn.unicom.thi360.feature.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.compose.rememberNavController
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.feature.attemptengine.FakeAttemptServer
import vn.unicom.thi360.feature.prepare.PrepareTestTags
import vn.unicom.thi360.feature.prepare.StartContext
import vn.unicom.thi360.feature.prepare.StartContextStore
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.LocalPlayerRouteOverrides
import vn.unicom.thi360.shell.routes.PlayerRouteOverrides

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PlayerRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: FakeAttemptServer
    private lateinit var storeRoot: java.io.File

    @Before
    fun setUp() {
        server = FakeAttemptServer().start()
        storeRoot = Files.createTempDirectory("thi360-player-route-").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        storeRoot.deleteRecursively()
    }

    @Test
    fun waitingHosted() {
        val starting = PlayerTestFixtures.attemptState("starting")
        val ready = PlayerTestFixtures.withFutureDeadlines(
            PlayerTestFixtures.attemptState("in-progress-per-part").copy(attemptId = starting.attemptId),
        )
        server.setAttemptState(starting)

        val pollCount = AtomicInteger(0)
        val prepareApi = FakePlayerPrepareApi(
            getStateHandler = {
                val n = pollCount.incrementAndGet()
                if (n >= 2) {
                    server.setAttemptState(ready)
                    ApiResult.Success(ready)
                } else {
                    ApiResult.Success(starting)
                }
            },
        )
        val store = AccountScopedStore(storeRoot, "acc-wait")
        val startContextStore = StartContextStore(store)
        startContextStore.save(
            starting.attemptId,
            StartContext(
                entitlementId = java.util.UUID.randomUUID().toString(),
                examTitle = "HSA Waiting Test",
                examKindLabel = "HSA",
            ),
        )

        val overrides = PlayerRouteOverrides(
            attemptApi = server.virtualTimeApi(),
            prepareApi = prepareApi,
            store = store,
            accountId = "acc-wait",
            connectivity = MutableConnectivityMonitor(true),
            startContextStore = startContextStore,
        )

        var navigator: AppNavigator? = null
        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navigator = appNav
            CompositionLocalProvider(LocalPlayerRouteOverrides provides overrides) {
                Thi360App(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    navController = navController,
                    navigator = appNav,
                )
            }
        }

        composeRule.runOnIdle { navigator!!.openAttempt(starting.attemptId.toString()) }
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithTag(PrepareTestTags.WAITING_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_SCREEN).assertIsDisplayed()

        composeRule.waitUntil(15_000L) {
            composeRule.onAllNodesWithTag(PlayerTestTags.TOP_BAR).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTag(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag))
}
