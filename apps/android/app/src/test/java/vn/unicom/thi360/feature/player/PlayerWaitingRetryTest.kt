package vn.unicom.thi360.feature.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import java.nio.file.Files
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.StartAttemptResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.feature.attemptengine.FakeAttemptServer
import vn.unicom.thi360.feature.prepare.PrepareTestTags
import vn.unicom.thi360.feature.prepare.StartContext
import vn.unicom.thi360.feature.prepare.StartContextStore
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.AppRoute
import vn.unicom.thi360.shell.routes.LocalPlayerRouteOverrides
import vn.unicom.thi360.shell.routes.PlayerRouteOverrides

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PlayerWaitingRetryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: FakeAttemptServer
    private lateinit var storeRoot: java.io.File

    @Before
    fun setUp() {
        server = FakeAttemptServer().start()
        storeRoot = Files.createTempDirectory("thi360-player-retry-").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        storeRoot.deleteRecursively()
    }

    @Test
    fun abortedWaitingRetryReplacesAttemptSoBackDoesNotReturnToOldAttempt() {
        val aborted = PlayerTestFixtures.attemptState("aborted")
        val newAttemptId = UUID.randomUUID()
        val readyNew = PlayerTestFixtures.withFutureDeadlines(
            PlayerTestFixtures.attemptState("in-progress-per-part").copy(attemptId = newAttemptId),
        )
        server.setAttemptState(aborted)

        val entitlementId = UUID.randomUUID()
        val store = AccountScopedStore(storeRoot, "acc-retry")
        val startContextStore = StartContextStore(store)
        startContextStore.save(
            aborted.attemptId,
            StartContext(
                entitlementId = entitlementId.toString(),
                examTitle = "HSA Retry Test",
                examKindLabel = "HSA",
            ),
        )

        val prepareApi = FakePlayerPrepareApi(
            getStateHandler = { ApiResult.Success(readyNew) },
            startHandler = { _, _ ->
                server.setAttemptState(readyNew)
                ApiResult.Success(
                    StartAttemptResponse(
                        attemptId = newAttemptId,
                        resumed = false,
                        retryable = false,
                        state = StartAttemptResponse.State.IN_PROGRESS,
                    ),
                )
            },
        )
        val overrides = PlayerRouteOverrides(
            attemptApi = server.virtualTimeApi(),
            prepareApi = prepareApi,
            store = store,
            accountId = "acc-retry",
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

        val nav = navigator!!
        composeRule.runOnIdle { nav.openAttempt(aborted.attemptId.toString()) }
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodes(hasTestTag(PrepareTestTags.WAITING_RETRY_BUTTON))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_RETRY_BUTTON).performClick()

        composeRule.waitUntil(10_000L) {
            nav.navController.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_ATTEMPT_ID) ==
                newAttemptId.toString()
        }
        assertEquals(
            newAttemptId.toString(),
            nav.navController.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_ATTEMPT_ID),
        )

        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodes(hasTestTag(PlayerTestTags.TOP_BAR)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR).assertIsDisplayed()

        composeRule.runOnIdle { nav.back() }
        composeRule.waitForIdle()
        assertFalse(
            "Back must NOT return to aborted attempt",
            nav.navController.currentDestination?.route == AppRoute.ATTEMPT_PATTERN,
        )
    }
}
