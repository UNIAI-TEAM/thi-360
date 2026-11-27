package vn.unicom.thi360.feature.attachments

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.AudioPolicyView
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.feature.attachments.controller.AttemptAudioController
import vn.unicom.thi360.feature.attachments.model.AudioPlayerPhase
import vn.unicom.thi360.feature.attachments.model.PauseReason
import vn.unicom.thi360.feature.attachments.ui.AttachmentTestTags
import vn.unicom.thi360.feature.attachments.ui.AttemptAudioPlayer
import vn.unicom.thi360.feature.attemptengine.AttemptEngine
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.FakeAttemptServer
import vn.unicom.thi360.feature.attemptengine.ManualEngineClock
import vn.unicom.thi360.feature.attemptengine.createTempStoreRoot
import vn.unicom.thi360.feature.attemptengine.withFutureDeadlines

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AudioLeaveScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var storeRoot: File
    private lateinit var server: FakeAttemptServer
    private lateinit var engineScope: CoroutineScope

    private class FakeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-leave-screen-")
        server = FakeAttemptServer()
        engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        engineScope.cancel()
        runCatching { server.shutdown() }
        storeRoot.deleteRecursively()
    }

    private fun createFreeNavEngine(): Triple<AttemptEngine, FakeAudioServerAddon, Pair<UUID, UUID>> {
        val state = server.loadStateFixture("audio-playing") { base ->
            val parts = base.parts.orEmpty().mapIndexed { index, part ->
                part.copy(state = AttemptPartStateView.State.OPEN, ord = index)
            }
            base.copy(
                navigationMode = AttemptStateResponse.NavigationMode.FREE,
                audioPolicy = AudioPolicyView(maxPlays = 2, seekable = false),
                audioPlays = emptyList(),
                parts = parts,
            ).withFutureDeadlines()
        }
        val parts = requireNotNull(state.parts)
        val part1Id = requireNotNull(parts[0].partId)
        val part2Id = requireNotNull(parts[1].partId)
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val engine = AttemptEngine(
            attemptId = state.attemptId,
            accountId = "account-leave",
            api = server.virtualTimeApi(),
            store = AccountScopedStore(storeRoot, "account-leave"),
            clock = ManualEngineClock(),
            connectivity = MutableConnectivityMonitor(true),
            scope = engineScope,
        )
        assertTrue(engine.state.value.session is EditSessionState.Editing)
        return Triple(engine, audioServer, part1Id to part2Id)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun switchingPartInFreeModeStopsWithinOneSecondAndShowsResumeOnReturn() {
        val (engine, audioServer, parts) = createFreeNavEngine()
        val (part1Id, _) = parts
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val stoppedAtNs = AtomicLong(-1L)
        val playback = FakeAudioPlaybackEngine(
            mediaDurationMs = 35_000L,
            onStopHook = { stoppedAtNs.compareAndSet(-1L, System.nanoTime()) },
        )
        val controller = AttemptAudioController(
            attemptId = engine.attemptId,
            assetId = assetId,
            partId = part1Id,
            fileName = "phan-1-nghe.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = engineScope,
        )

        var activePartIndex by mutableStateOf(1)
        val switchClickedAtNs = AtomicLong(-1L)

        composeRule.setContent {
            AttachmentsTestHost {
                Column {
                    Row {
                        Button(onClick = { activePartIndex = 1 }) { Text("Phần 1") }
                        Button(onClick = {
                            switchClickedAtNs.set(System.nanoTime())
                            activePartIndex = 2
                        }) { Text("Phần 2") }
                    }
                    if (activePartIndex == 1) {
                        AttemptAudioPlayer(controller = controller)
                    } else {
                        Text("Nội dung Phần 2")
                    }
                }
            }
        }

        composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(assetId)).performClick()
        composeRule.waitForIdle()
        assertTrue(playback.isPlaying())
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Playing)
        assertEquals(1, controller.uiState.value.playsLeft)

        stoppedAtNs.set(-1L)
        composeRule.onNodeWithText("Phần 2").performClick()
        composeRule.waitForIdle()
        val stopElapsedMs = ((stoppedAtNs.get() - switchClickedAtNs.get()).coerceAtLeast(0L)) / 1_000_000L

        assertFalse("Audio must stop when switching from part 1 to part 2", playback.isPlaying())
        assertTrue("Expected onStopHook to fire on part switch", stoppedAtNs.get() > 0L)
        assertTrue("Audio must stop within 1s (actual ${stopElapsedMs}ms)", stopElapsedMs < 1_000L)
        assertEquals(0, playback.heldBytesSize)
        val paused = controller.uiState.value.phase as AudioPlayerPhase.Paused
        assertEquals(PauseReason.LeftScreen, paused.reason)

        // Return to Part 1: must show "Phát lại", playsLeft unchanged, and MUST NOT auto-play.
        composeRule.onNodeWithText("Phần 1").performClick()
        composeRule.waitForIdle()

        assertFalse("Returning to part 1 must not auto-play", playback.isPlaying())
        composeRule.onNodeWithText("Phát lại").assertIsDisplayed()
        composeRule.onNodeWithTag(AttachmentTestTags.audioPlaysBadge(assetId)).assertTextEquals("Còn 1/2 lần nghe")

        controller.close()
        engine.close()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun leavingAttemptToHistoryTabStopsWithinOneSecondAndShowsResumeOnReturn() {
        val (engine, audioServer, parts) = createFreeNavEngine()
        val (part1Id, _) = parts
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val stoppedAtNs = AtomicLong(-1L)
        val playback = FakeAudioPlaybackEngine(
            mediaDurationMs = 35_000L,
            onStopHook = { stoppedAtNs.compareAndSet(-1L, System.nanoTime()) },
        )
        val controller = AttemptAudioController(
            attemptId = engine.attemptId,
            assetId = assetId,
            partId = part1Id,
            fileName = "nghe-lich-su.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = engineScope,
        )

        val attemptRouteLifecycle = FakeLifecycleOwner().apply {
            registry.currentState = Lifecycle.State.RESUMED
        }
        var currentDestination by mutableStateOf("attempt")
        val navClickedAtNs = AtomicLong(-1L)

        composeRule.setContent {
            AttachmentsTestHost {
                Column {
                    Row {
                        Button(onClick = {
                            currentDestination = "attempt"
                            attemptRouteLifecycle.registry.currentState = Lifecycle.State.RESUMED
                        }) { Text("Làm bài") }
                        Button(onClick = {
                            navClickedAtNs.set(System.nanoTime())
                            attemptRouteLifecycle.registry.currentState = Lifecycle.State.CREATED
                            currentDestination = "history"
                        }) { Text("Lịch sử") }
                    }
                    if (currentDestination == "attempt") {
                        CompositionLocalProvider(LocalLifecycleOwner provides attemptRouteLifecycle) {
                            AttemptAudioPlayer(controller = controller)
                        }
                    } else {
                        Text("Màn Lịch sử")
                    }
                }
            }
        }

        composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(assetId)).performClick()
        composeRule.waitForIdle()
        assertTrue(playback.isPlaying())

        stoppedAtNs.set(-1L)
        composeRule.onNodeWithText("Lịch sử").performClick()
        composeRule.waitForIdle()
        val stopElapsedMs = ((stoppedAtNs.get() - navClickedAtNs.get()).coerceAtLeast(0L)) / 1_000_000L

        assertFalse("Audio must stop when leaving attempt screen to History tab", playback.isPlaying())
        assertTrue("Expected onStopHook to fire on navigation to History tab", stoppedAtNs.get() > 0L)
        assertTrue("Audio must stop within 1s (actual ${stopElapsedMs}ms)", stopElapsedMs < 1_000L)
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Paused)

        composeRule.onNodeWithText("Làm bài").performClick()
        composeRule.waitForIdle()

        assertFalse("Returning to attempt screen must not auto-play", playback.isPlaying())
        composeRule.onNodeWithText("Phát lại").assertIsDisplayed()
        composeRule.onNodeWithTag(AttachmentTestTags.audioPlaysBadge(assetId)).assertTextEquals("Còn 1/2 lần nghe")

        controller.close()
        engine.close()
    }

    @Test
    @Config(qualifiers = "w840dp-h1024dp-xhdpi")
    fun configChangeDisposePreservesPlaybackWhileRealLeavePauses() {
        val (engine, audioServer, parts) = createFreeNavEngine()
        val (part1Id, _) = parts
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val playback = FakeAudioPlaybackEngine(mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = engine.attemptId,
            assetId = assetId,
            partId = part1Id,
            fileName = "tablet-rotate.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = engineScope,
        )

        var changingConfig by mutableStateOf(true)
        var showPlayer by mutableStateOf(true)
        val rotatingActivity = object : Activity() {
            override fun isChangingConfigurations(): Boolean = changingConfig
        }

        composeRule.setContent {
            val baseContext = LocalContext.current
            val wrappedContext = object : ContextWrapper(rotatingActivity) {
                override fun getApplicationContext() = baseContext.applicationContext
                override fun getResources() = baseContext.resources
                override fun getTheme() = baseContext.theme
                override fun getPackageName() = baseContext.packageName
            }
            AttachmentsTestHost {
                CompositionLocalProvider(LocalContext provides wrappedContext) {
                    if (showPlayer) {
                        AttemptAudioPlayer(controller = controller)
                    } else {
                        Text("Recreated")
                    }
                }
            }
        }

        composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(assetId)).performClick()
        composeRule.waitForIdle()
        assertTrue(playback.isPlaying())

        // Dispose while isChangingConfigurations == true must NOT pause playback (R1-5).
        changingConfig = true
        showPlayer = false
        composeRule.waitForIdle()
        assertTrue("Configuration change dispose must preserve active playback", playback.isPlaying())
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Playing)

        // Re-attach, then dispose while isChangingConfigurations == false -> must pause playback.
        showPlayer = true
        composeRule.waitForIdle()
        changingConfig = false
        showPlayer = false
        composeRule.waitForIdle()
        assertFalse("Real leave-screen dispose must stop active playback", playback.isPlaying())
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Paused)

        controller.close()
        engine.close()
    }
}
