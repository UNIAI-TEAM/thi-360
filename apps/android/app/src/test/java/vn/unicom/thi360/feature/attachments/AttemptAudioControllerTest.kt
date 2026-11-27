package vn.unicom.thi360.feature.attachments

import android.util.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import vn.unicom.thi360.BuildConfig
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.AppLogger
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.generated.api.AttemptsApi
import vn.unicom.thi360.core.network.generated.model.AttemptAudioPlayResponse
import vn.unicom.thi360.core.network.generated.model.AttemptAudioPlayView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.AudioPolicyView
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.attachments.controller.AttemptAudioController
import vn.unicom.thi360.feature.attachments.gateway.OpenApiAttemptAudioGateway
import vn.unicom.thi360.feature.attachments.model.AudioGatewayResult
import vn.unicom.thi360.feature.attachments.model.AudioLockReason
import vn.unicom.thi360.feature.attachments.model.AudioPlayerPhase
import vn.unicom.thi360.feature.attachments.model.PauseReason
import vn.unicom.thi360.feature.attemptengine.AttemptOperation
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.FakeAttemptServer
import vn.unicom.thi360.feature.attemptengine.createTempStoreRoot
import vn.unicom.thi360.feature.attemptengine.firstLeafItem
import vn.unicom.thi360.feature.attemptengine.firstOpenPart
import vn.unicom.thi360.feature.attemptengine.newEngineHarness
import vn.unicom.thi360.feature.attemptengine.withFutureDeadlines
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.ContractFixtureIndex

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AttemptAudioControllerTest {

    private lateinit var storeRoot: File
    private lateinit var server: FakeAttemptServer

    @Before
    fun setUp() {
        ShadowLog.clear()
        storeRoot = createTempStoreRoot("thi360-and-audio-")
        server = FakeAttemptServer()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
        storeRoot.deleteRecursively()
    }

    private fun loadAudioAttempt(
        assetId: UUID = UUID.fromString("00000000-0000-7000-8000-000000000008"),
        maxPlays: Int = 2,
        initialPlays: List<AttemptAudioPlayView> = emptyList(),
        variant: String = "audio-playing",
        timing: AttemptStateResponse.Timing = AttemptStateResponse.Timing.PER_PART,
        partRemainingSeconds: Long = 900L,
    ): Pair<AttemptStateResponse, UUID> {
        val state = server.loadStateFixture(variant) { base ->
            val withTiming = base.copy(
                timing = timing,
                audioPolicy = AudioPolicyView(maxPlays = maxPlays, seekable = false),
                audioPlays = initialPlays,
            )
            withTiming.withFutureDeadlines(
                serverNow = base.serverNow,
                attemptRemainingSeconds = partRemainingSeconds,
                partRemainingSeconds = partRemainingSeconds,
            )
        }
        val partId = state.firstOpenPart().partId!!
        return state to partId
    }

    @Test
    fun firstPlay() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()
        assertTrue(engine.state.value.session is EditSessionState.Editing)

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "nghe-hieu.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        val initial = controller.uiState.value
        assertEquals(AudioPlayerPhase.Idle(playsLeft = 2), initial.phase)
        assertEquals(2, initial.playsLeft)
        assertEquals(2, initial.effectiveMaxPlays)
        assertEquals(0L, initial.positionMs)

        controller.onPrimaryAction()
        runCurrent()

        val playing = controller.uiState.value
        assertTrue("Expected Playing phase, got ${playing.phase}", playing.phase is AudioPlayerPhase.Playing)
        assertEquals(1, playing.playsLeft)
        assertEquals(1, playing.playsUsed)
        assertEquals(35_000L, playing.durationMs)
        assertTrue(playback.isPlaying())
        assertEquals(0L, playback.lastStartPositionMs)

        val openCall = audioServer.calls(AudioEndpoint.OPEN_PLAY).single()
        val expectedSession = (engine.state.value.session as EditSessionState.Editing).sessionId.toString()
        assertEquals(expectedSession, openCall.sessionHeader)
        assertFalse(openCall.requestId.isNullOrBlank())

        val fetchCall = audioServer.calls(AudioEndpoint.FETCH_AUDIO).single()
        assertTrue(fetchCall.authorizationHeader?.startsWith("Bearer ") == true)
        assertEquals((playing.phase as AudioPlayerPhase.Playing).playId, fetchCall.playId)

        controller.close()
        engine.close()
    }

    @Test
    fun endAndExhausted() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, maxPlays = 2, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "nghe-hieu.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        runCurrent()
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Playing)

        playback.triggerCompleted()
        runCurrent()

        assertEquals(1, audioServer.calls(AudioEndpoint.END).size)
        assertEquals(AudioPlayerPhase.Idle(playsLeft = 1), controller.uiState.value.phase)
        assertEquals(1, controller.uiState.value.playsLeft)
        assertEquals(0, playback.heldBytesSize)

        controller.onPrimaryAction()
        runCurrent()
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Playing)
        assertEquals(0, controller.uiState.value.playsLeft)

        playback.triggerCompleted()
        runCurrent()

        assertEquals(2, audioServer.calls(AudioEndpoint.END).size)
        assertEquals(AudioPlayerPhase.Exhausted, controller.uiState.value.phase)
        assertTrue(controller.uiState.value.isExhausted)
        assertEquals(0, controller.uiState.value.playsLeft)
        assertEquals(2, controller.uiState.value.effectiveMaxPlays)

        // Boundary check: custom audioPolicy.maxPlays = 3 and 409 AUDIO_PLAYS_EXHAUSTED handling.
        val asset3 = UUID.fromString("00000000-0000-7000-8000-000000000099")
        val customServer = FakeAudioServerAddon(server, defaultDurationMs = 30_000L, defaultMaxPlays = 3)
        server.setAttemptState(
            server.currentAttemptState().copy(
                audioPolicy = AudioPolicyView(maxPlays = 3, seekable = false),
            ),
        )
        engine.refresh()
        runCurrent()

        val playback3 = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 30_000L)
        val controller3 = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = asset3,
            partId = partId,
            fileName = "custom-max-3.mp3",
            engine = engine,
            gateway = customServer.virtualTimeGateway(engine),
            playbackEngine = playback3,
            scope = backgroundScope,
        )
        runCurrent()
        assertEquals(3, controller3.uiState.value.effectiveMaxPlays)
        assertEquals(3, controller3.uiState.value.playsLeft)

        customServer.enqueueFixture(AudioEndpoint.OPEN_PLAY, "openAudioPlay", 409, "audio-plays-exhausted")
        controller3.onPrimaryAction()
        runCurrent()
        assertEquals(AudioPlayerPhase.Exhausted, controller3.uiState.value.phase)
        assertEquals(0, controller3.uiState.value.playsLeft)
        assertEquals(3, controller3.uiState.value.effectiveMaxPlays)

        controller3.close()
        controller.close()
        engine.close()
    }

    @Test
    fun retrySameRequestId() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val generatedIds = AtomicInteger(0)
        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "retry-idempotent.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
            requestIdGenerator = { "req-${generatedIds.incrementAndGet()}" },
        )
        runCurrent()

        // Drop response AFTER server already opened play #1.
        audioServer.enqueueNetworkDropAfterMutation(AudioEndpoint.OPEN_PLAY)
        controller.onPrimaryAction()
        runCurrent()

        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Idle)
        assertTrue(controller.uiState.value.hasPlayError)
        assertFalse(playback.isPlaying())

        // Retry tap must send the exact same requestId and consume only 1 play total.
        controller.onPrimaryAction()
        runCurrent()

        val openCalls = audioServer.calls(AudioEndpoint.OPEN_PLAY)
        assertEquals(2, openCalls.size)
        assertEquals("req-1", openCalls[0].requestId)
        assertEquals("req-1", openCalls[1].requestId)
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Playing)
        assertEquals(1, controller.uiState.value.playsUsed)
        assertEquals(1, controller.uiState.value.playsLeft)

        controller.close()
        engine.close()
    }

    @Test
    fun doubleTapSingleRequest() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val openGate = audioServer.gateNext(AudioEndpoint.OPEN_PLAY)
        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "double-tap.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Opening)
        advanceTimeBy(150L)
        controller.onPrimaryAction()
        advanceTimeBy(150L)
        controller.onPrimaryAction()
        runCurrent()

        openGate.complete(Unit)
        runCurrent()

        assertEquals(1, audioServer.calls(AudioEndpoint.OPEN_PLAY).size)
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Playing)

        controller.close()
        engine.close()
    }

    @Test
    fun reopenPlayingShowsResume() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000003")
        val existingPlayId = UUID.fromString("00000000-0000-7000-8000-000000000005")
        val (attempt, partId) = loadAudioAttempt(
            assetId = assetId,
            initialPlays = listOf(
                AttemptAudioPlayView(
                    assetId = assetId,
                    partId = UUID.fromString("00000000-0000-7000-8000-000000000006"),
                    durationMs = 155_000L,
                    playsUsed = 1,
                    playsLeft = 1,
                    state = AttemptAudioPlayView.State.PLAYING,
                    playId = existingPlayId,
                    positionMs = 70_000L,
                ),
            ),
        )
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 155_000L, defaultMaxPlays = 2)
        audioServer.seedPlayRecord(
            assetId = assetId,
            playId = existingPlayId,
            playsUsed = 1,
            durationMs = 155_000L,
            positionMs = 70_000L,
            state = AttemptAudioPlayResponse.State.PLAYING,
        )
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 155_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "reopen.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        // Must NOT autoplay on reopen; must show Paused ("Phát lại") at 01:10.
        assertFalse(playback.isPlaying())
        assertEquals(0, audioServer.calls(AudioEndpoint.OPEN_PLAY).size)
        assertEquals(0, audioServer.calls(AudioEndpoint.RESUME).size)
        val paused = controller.uiState.value.phase as AudioPlayerPhase.Paused
        assertEquals(existingPlayId, paused.playId)
        assertEquals(70_000L, paused.serverPositionMs)
        assertEquals(1, controller.uiState.value.playsLeft)

        controller.onPrimaryAction()
        runCurrent()

        assertEquals(0, audioServer.calls(AudioEndpoint.OPEN_PLAY).size)
        val resumeCall = audioServer.calls(AudioEndpoint.RESUME).single()
        assertEquals(existingPlayId, resumeCall.playId)
        assertTrue(playback.isPlaying())
        assertEquals(70_000L, playback.lastStartPositionMs)
        assertEquals(1, controller.uiState.value.playsLeft)

        controller.close()
        engine.close()
    }

    @Test
    fun inProgressResumes() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val existingPlayId = UUID.fromString("00000000-0000-7000-8000-000000000001")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 30_000L, defaultMaxPlays = 2)
        audioServer.seedPlayRecord(
            assetId = assetId,
            playId = existingPlayId,
            playsUsed = 1,
            durationMs = 30_000L,
            positionMs = 10_000L,
            state = AttemptAudioPlayResponse.State.PLAYING,
        )
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 30_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "in-progress.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        runCurrent()

        assertEquals(1, audioServer.calls(AudioEndpoint.OPEN_PLAY).size)
        val resumeCall = audioServer.calls(AudioEndpoint.RESUME).single()
        assertEquals(existingPlayId, resumeCall.playId)
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Playing)
        assertFalse(controller.uiState.value.hasPlayError)
        assertEquals(10_000L, playback.lastStartPositionMs)

        controller.close()
        engine.close()
    }

    @Test
    fun seekRejected() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "seek-rejected.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        runCurrent()
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Playing)

        audioServer.enqueueFixture(AudioEndpoint.PROGRESS, "updateAudioProgress", 409, "audio-seek-rejected")
        advanceTimeBy(10_000L)
        runCurrent()

        assertEquals(10_000L, playback.lastAuthorizedSeekMs)
        assertTrue("Playback must keep playing after seek correction", playback.isPlaying())
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Playing)
        assertEquals(10_000L, controller.uiState.value.positionMs)

        controller.close()
        engine.close()
    }

    @Test
    fun progressCadence() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "cadence-35s.wav",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        runCurrent()

        val callTimesMs = mutableListOf<Long>()
        advanceTimeBy(10_000L)
        runCurrent()
        callTimesMs += testScheduler.currentTime

        advanceTimeBy(10_000L)
        runCurrent()
        callTimesMs += testScheduler.currentTime

        advanceTimeBy(10_000L)
        runCurrent()
        callTimesMs += testScheduler.currentTime

        // Advance remaining 5s (total 35s) and verify no 4th progress call before completion.
        advanceTimeBy(5_000L)
        runCurrent()

        val progressCalls = audioServer.calls(AudioEndpoint.PROGRESS)
        assertEquals(3, progressCalls.size)
        val positions = progressCalls.map { it.positionMs!! }
        assertEquals(listOf(10_000L, 20_000L, 30_000L), positions)
        assertTrue(positions[0] < positions[1] && positions[1] < positions[2])
        assertTrue(positions.all { it <= 35_000L })
        assertEquals(10_000L, callTimesMs[1] - callTimesMs[0])
        assertEquals(10_000L, callTimesMs[2] - callTimesMs[1])

        // Boundary check: if media player reports a backward position (< 30_000L) or > duration, controller clamps safely.
        playback.setManualPositionMs(25_000L)
        advanceTimeBy(5_000L)
        runCurrent()
        val fourthPos = audioServer.calls(AudioEndpoint.PROGRESS).last().positionMs!!
        assertEquals("Progress must never go backward below server-confirmed 30_000L", 30_000L, fourthPos)

        playback.setManualPositionMs(40_000L)
        advanceTimeBy(10_000L)
        runCurrent()
        val fifthPos = audioServer.calls(AudioEndpoint.PROGRESS).last().positionMs!!
        assertEquals("Progress must never exceed durationMs 35_000L", 35_000L, fifthPos)

        controller.close()
        engine.close()
    }

    @Test
    fun staleReloads() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "stale.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        runCurrent()
        assertTrue(playback.isPlaying())

        val getsBefore = server.callCount(AttemptOperation.GET_STATE)
        // Server state now reports 2 plays used (0 left, ENDED).
        server.setAttemptState(
            server.currentAttemptState().copy(
                audioPlays = listOf(
                    AttemptAudioPlayView(
                        assetId = assetId,
                        partId = partId,
                        durationMs = 35_000L,
                        playsUsed = 2,
                        playsLeft = 0,
                        state = AttemptAudioPlayView.State.ENDED,
                        endReason = AttemptAudioPlayView.EndReason.FINISHED,
                        playId = UUID.randomUUID(),
                        positionMs = 35_000L,
                    ),
                ),
            ),
        )
        audioServer.enqueueFixture(AudioEndpoint.PROGRESS, "updateAudioProgress", 409, "audio-play-stale")
        advanceTimeBy(10_000L)
        runCurrent()

        assertFalse(playback.isPlaying())
        assertTrue("Expected engine.refresh() GET_STATE call", server.callCount(AttemptOperation.GET_STATE) > getsBefore)
        assertEquals(AudioPlayerPhase.Exhausted, controller.uiState.value.phase)
        assertEquals(0, controller.uiState.value.playsLeft)

        controller.close()
        engine.close()
    }

    @Test
    fun sessionRevoked() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "revoked.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        runCurrent()
        assertTrue(playback.isPlaying())

        audioServer.enqueueFixture(AudioEndpoint.PROGRESS, "updateAudioProgress", 409, "attempt-session-revoked")
        advanceTimeBy(10_000L)
        runCurrent()

        assertFalse("Audio must stop immediately on ATTEMPT_SESSION_REVOKED", playback.isPlaying())
        assertEquals(AudioPlayerPhase.Locked(AudioLockReason.Evicted), controller.uiState.value.phase)
        assertEquals(EditSessionState.Evicted, engine.state.value.session)

        controller.close()
        engine.close()
    }

    @Test
    fun engineSessionLost() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "session-lost.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        runCurrent()
        assertTrue(playback.isPlaying())

        // Web takes over session; engine reports SessionLost directly (e.g. via heartbeat/save eviction).
        server.simulateTakeoverByOtherDevice()
        engine.reportSessionRevoked()
        runCurrent()

        assertFalse("Audio must stop within 1s when engine emits SessionLost", playback.isPlaying())
        assertEquals(AudioPlayerPhase.Locked(AudioLockReason.Evicted), controller.uiState.value.phase)
        assertEquals(EditSessionState.Evicted, engine.state.value.session)

        controller.close()
        engine.close()
    }

    @Test
    fun leaseExpiredResume() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "lease-45s.wav",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        runCurrent()
        val playId = (controller.uiState.value.phase as AudioPlayerPhase.Playing).playId

        advanceTimeBy(10_000L)
        runCurrent()
        controller.onAppBackgrounded()
        runCurrent()
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Paused)

        // Advance server clock exactly 45 seconds so lease expires (server requires now < leaseUntil).
        server.serverNow = server.serverNow.plusSeconds(45L)
        val gateway = audioServer.virtualTimeGateway(engine)
        val rawFetch = gateway.fetchAudio(assetId, playId)
        assertTrue("Raw fetch after 45s lease expiry must be rejected (404)", rawFetch is AudioGatewayResult.Failure)

        // Pressing Resume calls POST .../resume to renew 45s lease, then downloads audio 200 OK without consuming a play.
        audioServer.resetCalls()
        controller.onPrimaryAction()
        runCurrent()

        val calls = audioServer.calls()
        assertEquals(listOf(AudioEndpoint.RESUME, AudioEndpoint.FETCH_AUDIO), calls.map { it.endpoint })
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Playing)
        assertEquals(1, controller.uiState.value.playsLeft)
        assertEquals(10_000L, playback.lastStartPositionMs)

        controller.close()
        engine.close()
    }

    @Test
    fun interruptionLeavesEngineUntouched() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val itemId = attempt.firstLeafItem().itemId!!
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        engine.answer(itemId, SingleChoiceAnswer("o1"))
        engine.position(partId, itemId)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "call-interrupt.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        runCurrent()
        assertTrue(playback.isPlaying())

        val clockBefore = engine.state.value.clock
        val answerBefore = engine.state.value.answers[itemId]?.value
        val positionBefore = engine.state.value.position

        // Incoming call / other app takes audio focus.
        playback.triggerFocusLost()
        runCurrent()

        assertFalse(playback.isPlaying())
        val pausedPhase = controller.uiState.value.phase as AudioPlayerPhase.Paused
        assertEquals(PauseReason.Interrupted, pausedPhase.reason)
        assertEquals(clockBefore.target, engine.state.value.clock.target)
        assertTrue(engine.state.value.clock.remainingMs > 0L)
        assertEquals(answerBefore, engine.state.value.answers[itemId]?.value)
        assertEquals(positionBefore, engine.state.value.position)

        // If app also went to background during the call, returning triggers AC-407 refresh once and keeps Paused.
        val getsBeforeReturn = server.callCount(AttemptOperation.GET_STATE)
        val hbsBeforeReturn = server.callCount(AttemptOperation.HEARTBEAT)
        engine.setForeground(false)
        controller.onAppBackgrounded()
        runCurrent()

        engine.setForeground(true)
        runCurrent()

        assertEquals(hbsBeforeReturn + 1, server.callCount(AttemptOperation.HEARTBEAT))
        assertEquals(getsBeforeReturn + 1, server.callCount(AttemptOperation.GET_STATE))
        assertFalse("Must never auto-play when returning from call/background", playback.isPlaying())
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Paused)

        controller.close()
        engine.close()
    }

    @Test
    fun offlineThenAutoResume() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val connectivity = MutableConnectivityMonitor(initialOnline = true)
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot, connectivity = connectivity)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "offline.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        runCurrent()
        val playId = (controller.uiState.value.phase as AudioPlayerPhase.Playing).playId
        advanceTimeBy(10_000L)
        runCurrent()

        connectivity.setOnline(false)
        runCurrent()

        assertFalse(playback.isPlaying())
        assertEquals(AudioPlayerPhase.WaitingNetwork(playId), controller.uiState.value.phase)
        assertFalse(controller.uiState.value.isOnline)

        // Reconnect: must auto-resume without requiring user tap.
        audioServer.resetCalls()
        connectivity.setOnline(true)
        runCurrent()

        assertEquals(1, audioServer.calls(AudioEndpoint.RESUME).size)
        assertEquals(playId, audioServer.calls(AudioEndpoint.RESUME).single().playId)
        assertEquals(1, audioServer.calls(AudioEndpoint.FETCH_AUDIO).size)
        assertTrue(playback.isPlaying())
        assertTrue(controller.uiState.value.phase is AudioPlayerPhase.Playing)
        assertEquals(1, controller.uiState.value.playsLeft)

        controller.close()
        engine.close()
    }

    @Test
    fun timeUp() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(
            assetId = assetId,
            initialPlays = emptyList(),
            timing = AttemptStateResponse.Timing.PER_PART,
            partRemainingSeconds = 12L,
        )
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 35_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "time-up.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        runCurrent()
        assertTrue(playback.isPlaying())

        // Advance virtual clock to 00:00 of the part.
        advanceTimeBy(12_000L)
        runCurrent()

        assertFalse("Audio must stop immediately when part clock hits 00:00", playback.isPlaying())
        assertEquals(AudioPlayerPhase.Locked(AudioLockReason.TimeUp), controller.uiState.value.phase)

        // Even after engine's 500ms reload completes, TimeUp reason must remain locked as TimeUp.
        advanceTimeBy(600L)
        runCurrent()
        assertEquals(AudioPlayerPhase.Locked(AudioLockReason.TimeUp), controller.uiState.value.phase)

        controller.close()
        engine.close()
    }

    @Test
    fun stopsBeforeFinishPart() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 35_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val timeline = CopyOnWriteArrayList<String>()
        server.onRequest(AttemptOperation.FINISH_PART) {
            timeline.add("FINISH_PART_REQUEST")
        }

        val playback = FakeAudioPlaybackEngine(
            testScope = this,
            mediaDurationMs = 35_000L,
            onStopHook = { timeline.add("AUDIO_STOPPED") },
        )
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "finish-part.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        runCurrent()
        assertTrue(playback.isPlaying())

        engine.finishPart(partId)
        runCurrent()

        assertFalse(playback.isPlaying())
        assertEquals(AudioPlayerPhase.Locked(AudioLockReason.PartClosed), controller.uiState.value.phase)
        assertEquals(listOf("AUDIO_STOPPED", "FINISH_PART_REQUEST"), timeline.toList())

        controller.close()
        engine.close()
    }

    @Test
    fun partClosedOnResume() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000003")
        val playId = UUID.fromString("00000000-0000-7000-8000-000000000005")
        val (attempt, partId) = loadAudioAttempt(
            assetId = assetId,
            initialPlays = listOf(
                AttemptAudioPlayView(
                    assetId = assetId,
                    partId = UUID.fromString("00000000-0000-7000-8000-000000000006"),
                    durationMs = 30_000L,
                    playsUsed = 1,
                    playsLeft = 1,
                    state = AttemptAudioPlayView.State.PLAYING,
                    playId = playId,
                    positionMs = 10_000L,
                ),
            ),
        )
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 30_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 30_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "part-closed.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        val getsBefore = server.callCount(AttemptOperation.GET_STATE)
        audioServer.enqueueFixture(AudioEndpoint.RESUME, "resumeAudioPlay", 409, "part-closed")
        controller.onPrimaryAction()
        runCurrent()

        assertEquals(AudioPlayerPhase.Locked(AudioLockReason.PartClosed), controller.uiState.value.phase)
        assertTrue(server.callCount(AttemptOperation.GET_STATE) > getsBefore)

        // Also test end returning endReason = CLOSED, Acquiring, Submitted, and Disabled lock reasons.
        val asset2 = UUID.fromString("00000000-0000-7000-8000-000000000077")
        val playback2 = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 30_000L)
        val controller2 = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = asset2,
            partId = partId,
            fileName = "end-closed.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback2,
            scope = backgroundScope,
        )
        runCurrent()
        controller2.onPrimaryAction()
        runCurrent()

        val getsBeforeEndClosed = server.callCount(AttemptOperation.GET_STATE)
        audioServer.enqueueFixture(AudioEndpoint.END, "endAudioPlay", 200, "closed")
        playback2.triggerCompleted()
        runCurrent()
        assertEquals(AudioPlayerPhase.Locked(AudioLockReason.PartClosed), controller2.uiState.value.phase)
        assertTrue(server.callCount(AttemptOperation.GET_STATE) > getsBeforeEndClosed)

        // External Disabled lock reason.
        val asset3 = UUID.fromString("00000000-0000-7000-8000-000000000078")
        val controller3 = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = asset3,
            partId = partId,
            fileName = "disabled.mp3",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = FakeAudioPlaybackEngine(testScope = this),
            scope = backgroundScope,
            externalDisabled = true,
        )
        runCurrent()
        assertEquals(AudioPlayerPhase.Locked(AudioLockReason.Disabled), controller3.uiState.value.phase)

        // Submitting / Submitted lock reason.
        controller3.setExternalDisabled(false)
        runCurrent()
        engine.submit()
        runCurrent()
        assertEquals(AudioPlayerPhase.Locked(AudioLockReason.Submitted), controller3.uiState.value.phase)

        // Acquiring lock reason (session claim gated) on an isolated attempt server.
        val acquiringServer = FakeAttemptServer()
        try {
            acquiringServer.loadStateFixture("audio-playing") { base ->
                base.copy(
                    audioPolicy = AudioPolicyView(maxPlays = 2, seekable = false),
                    audioPlays = emptyList(),
                ).withFutureDeadlines(serverNow = base.serverNow)
            }
            val acquiringAudioServer = FakeAudioServerAddon(acquiringServer, defaultDurationMs = 30_000L, defaultMaxPlays = 2)
            val claimGate = acquiringServer.gateNext(AttemptOperation.CLAIM_SESSION)
            val (acquiringEngine, _) = newEngineHarness(acquiringServer, createTempStoreRoot("thi360-acq-"))
            runCurrent()
            assertEquals(EditSessionState.Acquiring, acquiringEngine.state.value.session)
            val acquiringController = AttemptAudioController(
                attemptId = acquiringEngine.attemptId,
                assetId = asset3,
                partId = null,
                fileName = "acquiring.mp3",
                engine = acquiringEngine,
                gateway = acquiringAudioServer.virtualTimeGateway(acquiringEngine),
                playbackEngine = FakeAudioPlaybackEngine(testScope = this),
                scope = backgroundScope,
            )
            runCurrent()
            assertEquals(AudioPlayerPhase.Locked(AudioLockReason.Acquiring), acquiringController.uiState.value.phase)
            acquiringAudioServer.resetCalls()
            acquiringController.onPrimaryAction()
            runCurrent()
            assertEquals("Zero audio calls permitted while session is Acquiring", 0, acquiringAudioServer.calls().size)
            claimGate.complete(Unit)
            runCurrent()
            assertEquals(AudioPlayerPhase.Idle(2), acquiringController.uiState.value.phase)

            acquiringController.close()
            acquiringEngine.close()
        } finally {
            runCatching { acquiringServer.shutdown() }
        }
        controller3.close()
        controller2.close()
        controller.close()
        engine.close()
    }

    @Test
    fun overlappingProgressAndEndSendsEndOnce() = runTest {
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, partId) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 10_000L, defaultMaxPlays = 2)
        val (engine, _) = newEngineHarness(server, storeRoot)
        runCurrent()

        val playback = FakeAudioPlaybackEngine(testScope = this, mediaDurationMs = 10_000L)
        val controller = AttemptAudioController(
            attemptId = attempt.attemptId,
            assetId = assetId,
            partId = partId,
            fileName = "overlap.wav",
            engine = engine,
            gateway = audioServer.virtualTimeGateway(engine),
            playbackEngine = playback,
            scope = backgroundScope,
        )
        runCurrent()

        controller.onPrimaryAction()
        runCurrent()

        // Gate progress right at the 10s tick while completion fires simultaneously.
        val progressGate = audioServer.gateNext(AudioEndpoint.PROGRESS)
        advanceTimeBy(10_000L)
        playback.triggerCompleted()
        playback.triggerCompleted()
        progressGate.complete(Unit)
        runCurrent()

        assertEquals("end must be sent strictly once even on overlapping progress + completion", 1, audioServer.calls(AudioEndpoint.END).size)
        assertEquals(AudioPlayerPhase.Idle(playsLeft = 1), controller.uiState.value.phase)

        controller.close()
        engine.close()
    }

    @Test
    fun unauthorizedRefreshesOnceAndReusesRequestId() = runBlocking {
        server.start()
        val expiredToken = "expired-audio-access-token"
        val refreshedToken = "refreshed-audio-access-token"
        val refreshCount = AtomicInteger(0)
        val recordedRequestIds = CopyOnWriteArrayList<String>()

        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, _) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 30_000L, defaultMaxPlays = 2)

        val refreshBody = ContractFixtureLoader.readAuthFixture("refresh-200.json")
            .replace("fixture-access-token", refreshedToken)
        val unauthorizedBody = ContractFixtureIndex.find("downloadAttemptAsset", 401, "unauthenticated")
            .file.readText(Charsets.UTF_8)
        val openPlayBody = ContractFixtureIndex.find("openAudioPlay", 200, null)
            .file.readText(Charsets.UTF_8)

        server.contractServer.respondDynamic("POST", "/api/v1/auth/mobile/refresh") {
            refreshCount.incrementAndGet()
            ContractFakeServer.jsonResponse(200, refreshBody)
        }

        val playsPath = "/api/v1/me/attempts/${attempt.attemptId}/audio/$assetId/plays"
        server.contractServer.respondDynamic("POST", playsPath) { req ->
            val bodyText = req.body.readUtf8()
            val reqId = vn.unicom.thi360.core.network.generated.infrastructure.Serializer.kotlinxSerializationJson
                .decodeFromString(
                    vn.unicom.thi360.core.network.generated.model.StartAudioPlayRequest.serializer(),
                    bodyText,
                ).requestId
            recordedRequestIds.add(reqId)
            assertNotNull("X-Attempt-Session must be present", req.getHeader("X-Attempt-Session"))
            if (req.getHeader("Authorization") == "Bearer $refreshedToken") {
                ContractFakeServer.jsonResponse(200, openPlayBody)
            } else {
                ContractFakeServer.jsonResponse(401, unauthorizedBody)
            }
        }

        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = expiredToken,
                refreshToken = "valid-audio-refresh-token",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
                accountId = "account-A",
            ),
        )
        val noOpLogger = object : AppLogger {
            override fun debug(tag: String, message: String) = Unit
            override fun warn(tag: String, message: String) = Unit
        }
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            logger = noOpLogger,
            resolveAccountIdOnAuth = false,
        )
        val sessionId = UUID.fromString("55555555-5555-4555-8555-555555555555")
        val gateway = OpenApiAttemptAudioGateway(
            attemptId = attempt.attemptId,
            service = stack.authenticatedService(AttemptsApi::class.java),
            activeSessionIdProvider = { sessionId },
            ioDispatcher = Dispatchers.IO,
        )

        val result = gateway.openPlay(assetId = assetId, requestId = "stable-req-401")
        assertTrue("Expected Success after single 401 refresh, got $result", result is vn.unicom.thi360.feature.attachments.model.AudioGatewayResult.Success)
        assertEquals(1, refreshCount.get())
        assertEquals(listOf("stable-req-401", "stable-req-401"), recordedRequestIds.toList())
        assertEquals(refreshedToken, tokenStore.read()?.accessToken)
        // Touch audioServer so unused warning doesn't fire while exercising HTTP addon setup.
        assertNotNull(audioServer)
    }

    @Test
    fun demoLogsDuringAudioCallsNeverLeakSecretsOrSessionHeader() = runBlocking {
        server.start()
        val secretToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhdWRpby1kZW1vIn0.secretAudioSig123"
        val secretSessionId = UUID.fromString("88888888-7777-4666-8555-444444444444")
        val assetId = UUID.fromString("00000000-0000-7000-8000-000000000008")
        val (attempt, _) = loadAudioAttempt(assetId = assetId, initialPlays = emptyList())
        val audioServer = FakeAudioServerAddon(server, defaultDurationMs = 30_000L, defaultMaxPlays = 2)
        server.activeSessionId = secretSessionId

        val env = if (BuildConfig.FLAVOR == "demo") Environment.DEMO else AppEnvironment.environment
        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = secretToken,
                refreshToken = "refresh-secret-audio-token",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
                accountId = "account-demo",
            ),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = env,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            resolveAccountIdOnAuth = false,
        )
        val gateway = OpenApiAttemptAudioGateway(
            attemptId = attempt.attemptId,
            service = stack.authenticatedService(AttemptsApi::class.java),
            activeSessionIdProvider = { secretSessionId },
            ioDispatcher = Dispatchers.IO,
        )

        val opened = gateway.openPlay(assetId, "req-demo-1")
        assertTrue(opened is vn.unicom.thi360.feature.attachments.model.AudioGatewayResult.Success)
        val playId = (opened as vn.unicom.thi360.feature.attachments.model.AudioGatewayResult.Success).value.playId!!
        gateway.fetchAudio(assetId, playId)
        audioServer.enqueueFixture(AudioEndpoint.PROGRESS, "updateAudioProgress", 409, "attempt-session-revoked")
        gateway.progress(assetId, playId, 5_000L)

        val logs = ShadowLog.getLogs()
        val httpLogs = logs.filter { it.tag == "Thi360Http" }
        assertTrue(httpLogs.any { it.msg.contains("-> 409") })
        if (env == Environment.DEMO) {
            assertTrue(httpLogs.none { it.msg.contains("-> 200") })
        }
        val allText = logs.mapNotNull { it.msg }
        for (line in allText) {
            assertFalse("Leaked access token in log: $line", line.contains(secretToken))
            assertFalse("Leaked session id in log: $line", line.contains(secretSessionId.toString()))
        }
    }
}
