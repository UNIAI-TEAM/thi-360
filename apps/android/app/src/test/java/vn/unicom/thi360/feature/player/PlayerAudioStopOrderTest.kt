package vn.unicom.thi360.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.feature.attemptengine.AttemptEngine
import vn.unicom.thi360.feature.attemptengine.AttemptOperation
import vn.unicom.thi360.feature.attemptengine.CloseTarget
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.FakeAttemptServer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PlayerAudioStopOrderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: FakeAttemptServer
    private lateinit var storeRoot: java.io.File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        server = FakeAttemptServer().start()
        storeRoot = Files.createTempDirectory("thi360-audio-order-").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
        storeRoot.deleteRecursively()
    }

    @Test
    fun stopsAudioBeforeSendingFinishPartHttpRequest() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        server.setAttemptState(attempt)

        val trace = CopyOnWriteArrayList<String>()
        server.onRequest(AttemptOperation.FINISH_PART) {
            trace.add("http_finish_part")
        }

        val fakeAudioHost = object : PlayerAttachmentHost {
            @Composable
            override fun RenderAttachments(context: PlayerAttachmentContext) = Unit

            override fun onBeforeClose(target: CloseTarget) {
                if (target is CloseTarget.Part) {
                    trace.add("stop_audio_part")
                }
            }
        }

        val engine = AttemptEngine(
            attemptId = attempt.attemptId,
            accountId = "acc-audio-1",
            api = server.virtualTimeApi(),
            store = AccountScopedStore(storeRoot, "acc-audio-1"),
            connectivity = MutableConnectivityMonitor(true),
            scope = scope,
        )

        composeRule.setContent {
            PlayerTestTheme {
                CompositionLocalProvider(LocalPlayerAttachmentHost provides fakeAudioHost) {
                    PlayerHost(
                        attemptId = attempt.attemptId.toString(),
                        engine = engine,
                        onCloseAttempt = {},
                        onBackToMyExams = {},
                        onViewResult = {},
                        onReplaceAttempt = {},
                        onOpenPrepare = {},
                    )
                }
            }
        }

        waitForEngineState(composeRule, engine) { it.session is EditSessionState.Editing }

        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_ACTION_BUTTON).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.FINISH_PART_CONFIRM).performClick()

        composeRule.waitUntil(5_000L) {
            trace.contains("http_finish_part")
        }
        assertEquals(listOf("stop_audio_part", "http_finish_part"), trace.toList())
    }

    @Test
    fun stopsAudioBeforeSendingSubmitHttpRequest() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-shared"))
            .copy(navigationMode = vn.unicom.thi360.core.network.generated.model.AttemptStateResponse.NavigationMode.FREE)
        server.setAttemptState(attempt)

        val trace = CopyOnWriteArrayList<String>()
        server.onRequest(AttemptOperation.SUBMIT) {
            trace.add("http_submit")
        }

        val fakeAudioHost = object : PlayerAttachmentHost {
            @Composable
            override fun RenderAttachments(context: PlayerAttachmentContext) = Unit

            override fun onBeforeClose(target: CloseTarget) {
                if (target == CloseTarget.Attempt) {
                    trace.add("stop_audio_attempt")
                }
            }
        }

        val engine = AttemptEngine(
            attemptId = attempt.attemptId,
            accountId = "acc-audio-2",
            api = server.virtualTimeApi(),
            store = AccountScopedStore(storeRoot, "acc-audio-2"),
            connectivity = MutableConnectivityMonitor(true),
            scope = scope,
        )

        composeRule.setContent {
            PlayerTestTheme {
                CompositionLocalProvider(LocalPlayerAttachmentHost provides fakeAudioHost) {
                    PlayerHost(
                        attemptId = attempt.attemptId.toString(),
                        engine = engine,
                        onCloseAttempt = {},
                        onBackToMyExams = {},
                        onViewResult = {},
                        onReplaceAttempt = {},
                        onOpenPrepare = {},
                    )
                }
            }
        }

        waitForEngineState(composeRule, engine) { it.session is EditSessionState.Editing }

        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_ACTION_BUTTON).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.SUBMIT_CONFIRM).performClick()

        composeRule.waitUntil(5_000L) {
            trace.contains("http_submit")
        }
        assertEquals(listOf("stop_audio_attempt", "http_submit"), trace.toList())
    }
}
