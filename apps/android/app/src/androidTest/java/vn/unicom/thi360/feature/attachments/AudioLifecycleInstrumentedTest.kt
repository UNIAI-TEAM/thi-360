package vn.unicom.thi360.feature.attachments

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.attachments.controller.AttemptAudioController
import vn.unicom.thi360.feature.attachments.model.AudioPlayerPhase
import vn.unicom.thi360.feature.attachments.playback.AndroidMediaPlayerEngine
import vn.unicom.thi360.feature.attachments.playback.AudioFocusOwner
import vn.unicom.thi360.feature.attachments.playback.AudioMediaSessionBridge
import vn.unicom.thi360.feature.attachments.ui.AttachmentTestTags
import vn.unicom.thi360.feature.attachments.ui.AttemptAudioPlayer

@RunWith(AndroidJUnit4::class)
class AudioLifecycleInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AudioMediaSessionBridge.getInstance(context).releaseForTests()
        runCatching {
            runBlocking { AppSessionContainer.get(context).sessionManager.clearSession() }
        }
    }

    @Test
    fun homeStopsWithinOneSecond() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wavBytes = AttachmentAndroidTestSupport.loadAssetBytes("attachments/silence-35s.wav")
        val audioAssetId = UUID.fromString("00000000-0000-7000-8000-000000000528")

        val server = EmbeddedAttachmentServer(defaultDurationMs = 35_000L)
        server.registerAsset(audioAssetId, "audio/wav", wavBytes, durationMs = 35_000L)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val harness = runBlocking { AttachmentAndroidTestSupport.createHarness(context, server, scope) }
        val playbackEngine = AndroidMediaPlayerEngine(context = context, scope = scope)
        val controller = AttemptAudioController(
            attemptId = server.attemptId,
            assetId = audioAssetId,
            partId = server.partId,
            fileName = "home-lifecycle.wav",
            engine = harness.engine,
            gateway = harness.gateway,
            playbackEngine = playbackEngine,
            scope = scope,
        )

        try {
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                    AttemptAudioPlayer(
                        controller = controller,
                        modifier = Modifier.padding(16.dp),
                        attachLifecycle = true,
                    )
                }
            }

            composeRule.waitForIdle()
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioAssetId)).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                controller.uiState.value.phase is AudioPlayerPhase.Playing && playbackEngine.isPlaying()
            }

            val playingPhase = controller.uiState.value.phase as AudioPlayerPhase.Playing
            // Simulate playing at 00:25 while last confirmed server progress was 00:20 (ĐT 528).
            playbackEngine.seekToAuthorized(25_000L)
            server.overrideServerPositionMs(playingPhase.playId, 20_000L)

            val onStopAtMs = AtomicLong(-1L)
            val stoppedAtMs = AtomicLong(-1L)
            val stopObserver = object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    onStopAtMs.compareAndSet(-1L, SystemClock.elapsedRealtime())
                }
            }
            composeRule.runOnUiThread {
                composeRule.activity.lifecycle.addObserver(stopObserver)
            }

            // Record the exact timestamp when MediaPlayer stops without waiting for ActivityScenario's post-stop idle sync.
            val monitor = thread(isDaemon = true, name = "audio-stop-latency-monitor") {
                val watchDeadline = SystemClock.elapsedRealtime() + 5_000L
                while (SystemClock.elapsedRealtime() < watchDeadline) {
                    if (!playbackEngine.isPlaying()) {
                        stoppedAtMs.compareAndSet(-1L, SystemClock.elapsedRealtime())
                        break
                    }
                    Thread.sleep(5L)
                }
            }

            val startedStopMs = SystemClock.elapsedRealtime()
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            monitor.join(2_000L)

            val recordedStopMs = stoppedAtMs.get()
            assertTrue("Audio did not stop after backgrounding", recordedStopMs > 0L)
            val fromOnStopMs = if (onStopAtMs.get() > 0L) (recordedStopMs - onStopAtMs.get()).coerceAtLeast(0L) else 0L
            val fromRequestMs = (recordedStopMs - startedStopMs).coerceAtLeast(0L)
            assertTrue(
                "Audio must stop within 1000ms of ON_STOP, took ${fromOnStopMs}ms (total from moveToState=${fromRequestMs}ms)",
                fromOnStopMs < 1_000L && fromRequestMs < 1_000L,
            )

            // Return to foreground: must NOT auto-play, button is "Phát lại" with A-92 explanation.
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.runOnUiThread {
                composeRule.activity.lifecycle.removeObserver(stopObserver)
            }
            composeRule.waitForIdle()
            Thread.sleep(300L)
            assertFalse("Audio must not auto-play upon returning to foreground", playbackEngine.isPlaying())
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioAssetId))
                .assertIsDisplayed()
                .assertTextEquals("Phát lại")
            composeRule.onNodeWithText("Âm thanh đã tạm dừng. Bấm Phát lại để nghe tiếp mà không mất lượt nghe.")
                .assertIsDisplayed()

            // Simulate biometric lock then unlock cycle while paused: must remain "Phát lại" without auto-play.
            val sessionManager = AppSessionContainer.get(context).sessionManager
            runBlocking {
                sessionManager.adoptSession(
                    Tokens(
                        accessToken = "test-access",
                        refreshToken = "test-refresh",
                        accessExpiresIn = 300,
                        refreshExpiresIn = 604800,
                        mustChangePassword = false,
                        username = "student.audio",
                    ),
                )
                sessionManager.lockSession()
            }
            composeRule.waitForIdle()
            runBlocking { sessionManager.unlockAndRefresh() }
            composeRule.waitForIdle()
            Thread.sleep(250L)
            assertFalse("Audio must remain paused after biometric unlock", playbackEngine.isPlaying())
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioAssetId))
                .assertTextEquals("Phát lại")

            // Tapping "Phát lại" calls /resume and starts from server-saved 00:20 with playsLeft unchanged.
            server.resetRequests()
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioAssetId)).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                controller.uiState.value.phase is AudioPlayerPhase.Playing && playbackEngine.isPlaying()
            }
            assertEquals(1, server.requests().count { it.path.endsWith("/resume") })
            val resumedPosition = playbackEngine.currentPositionMs()
            assertTrue(
                "Resumed audio must start near server-saved 20000ms (not 25000ms), got ${resumedPosition}ms",
                resumedPosition in 19_000L..23_500L,
            )
            assertEquals(1, controller.uiState.value.playsLeft)
        } finally {
            runCatching { composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED) }
            controller.close()
            playbackEngine.stopAndRelease()
            harness.close()
            server.close()
            scope.cancel()
        }
    }

    @Test
    fun focusLossStops() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wavBytes = AttachmentAndroidTestSupport.loadAssetBytes("attachments/silence-35s.wav")
        val audioAssetId = UUID.fromString("00000000-0000-7000-8000-000000000529")

        val server = EmbeddedAttachmentServer(defaultDurationMs = 35_000L)
        server.registerAsset(audioAssetId, "audio/wav", wavBytes, durationMs = 35_000L)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val harness = runBlocking { AttachmentAndroidTestSupport.createHarness(context, server, scope) }
        val playbackEngine = AndroidMediaPlayerEngine(context = context, scope = scope)
        val controller = AttemptAudioController(
            attemptId = server.attemptId,
            assetId = audioAssetId,
            partId = server.partId,
            fileName = "focus-loss.wav",
            engine = harness.engine,
            gateway = harness.gateway,
            playbackEngine = playbackEngine,
            scope = scope,
        )

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val competingFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()

        try {
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                    AttemptAudioPlayer(
                        controller = controller,
                        modifier = Modifier.padding(16.dp),
                        attachLifecycle = false,
                    )
                }
            }

            composeRule.waitForIdle()
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioAssetId)).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                controller.uiState.value.phase is AudioPlayerPhase.Playing && playbackEngine.isPlaying()
            }

            val answersBefore = harness.engine.state.value.answers
            val positionBefore = harness.engine.state.value.position

            // Competing audio focus request (simulating an incoming call / another media app).
            val focusResult = audioManager.requestAudioFocus(competingFocusRequest)
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, focusResult)

            composeRule.waitUntil(timeoutMillis = 2_000L) {
                !playbackEngine.isPlaying() && controller.uiState.value.phase is AudioPlayerPhase.Paused
            }
            assertEquals("Engine answers must remain unchanged on focus loss", answersBefore, harness.engine.state.value.answers)
            assertEquals("Engine position must remain unchanged on focus loss", positionBefore, harness.engine.state.value.position)

            // End competing call: app must NOT auto-play and must keep button "Phát lại".
            audioManager.abandonAudioFocusRequest(competingFocusRequest)
            Thread.sleep(350L)
            assertFalse("Audio must not auto-play after competing focus ends", playbackEngine.isPlaying())
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioAssetId))
                .assertTextEquals("Phát lại")

            // Simulate returning from background: engine runs criterion-407 refresh once and audio remains Paused.
            harness.engine.setForeground(false)
            server.resetRequests()
            harness.engine.setForeground(true)
            composeRule.waitUntil(timeoutMillis = 3_000L) {
                server.requests().any { it.method == "GET" && it.path == "/api/v1/me/attempts/${server.attemptId}" }
            }
            Thread.sleep(250L)
            assertEquals(
                "Criterion 407 refresh must run once on foreground",
                1,
                server.requests().count { it.method == "GET" && it.path == "/api/v1/me/attempts/${server.attemptId}" },
            )
            assertFalse("Audio must stay paused after foreground refresh", playbackEngine.isPlaying())
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioAssetId))
                .assertTextEquals("Phát lại")
        } finally {
            runCatching { audioManager.abandonAudioFocusRequest(competingFocusRequest) }
            controller.close()
            playbackEngine.stopAndRelease()
            harness.close()
            server.close()
            scope.cancel()
        }
    }

    @Test
    fun becomingNoisyStops() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wavBytes = AttachmentAndroidTestSupport.loadAssetBytes("attachments/silence-35s.wav")
        val audioAssetId = UUID.fromString("00000000-0000-7000-8000-000000000531")

        val server = EmbeddedAttachmentServer(defaultDurationMs = 35_000L)
        server.registerAsset(audioAssetId, "audio/wav", wavBytes, durationMs = 35_000L)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val harness = runBlocking { AttachmentAndroidTestSupport.createHarness(context, server, scope) }
        val playbackEngine = AndroidMediaPlayerEngine(context = context, scope = scope)
        val controller = AttemptAudioController(
            attemptId = server.attemptId,
            assetId = audioAssetId,
            partId = server.partId,
            fileName = "becoming-noisy.wav",
            engine = harness.engine,
            gateway = harness.gateway,
            playbackEngine = playbackEngine,
            scope = scope,
        )

        try {
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                    AttemptAudioPlayer(
                        controller = controller,
                        modifier = Modifier.padding(16.dp),
                        attachLifecycle = false,
                    )
                }
            }

            composeRule.waitForIdle()
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioAssetId)).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                controller.uiState.value.phase is AudioPlayerPhase.Playing && playbackEngine.isPlaying()
            }

            val focusOwner = AudioFocusOwner.getInstance(context)
            assertTrue(
                "ACTION_AUDIO_BECOMING_NOISY receiver must be registered while playing",
                focusOwner.isNoisyReceiverRegistered(),
            )

            // Deliver ACTION_AUDIO_BECOMING_NOISY to the registered BroadcastReceiver (protected system action).
            val noisyIntent = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            composeRule.runOnUiThread {
                focusOwner.noisyBroadcastReceiver.onReceive(context.applicationContext, noisyIntent)
            }

            composeRule.waitUntil(timeoutMillis = 1_500L) {
                !playbackEngine.isPlaying() && controller.uiState.value.phase is AudioPlayerPhase.Paused
            }
            assertFalse(
                "Noisy receiver must be unregistered once playback stops",
                focusOwner.isNoisyReceiverRegistered(),
            )
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioAssetId))
                .assertIsDisplayed()
                .assertTextEquals("Phát lại")
        } finally {
            runCatching { composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED) }
            controller.close()
            playbackEngine.stopAndRelease()
            harness.close()
            server.close()
            scope.cancel()
        }
    }
}
