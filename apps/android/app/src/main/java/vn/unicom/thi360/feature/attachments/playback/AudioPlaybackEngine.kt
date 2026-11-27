package vn.unicom.thi360.feature.attachments.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface AudioPlaybackCallbacks {
    fun onCompleted()
    fun onError()
    fun onFocusLost()
    fun onBecomingNoisy()
    fun onHeadsetPause()
}

sealed interface PlaybackStartResult {
    data class Started(val durationMs: Long) : PlaybackStartResult
    data object Failed : PlaybackStartResult
}

interface AudioPlaybackEngine {
    suspend fun start(
        audioBytes: ByteArray,
        startPositionMs: Long,
        callbacks: AudioPlaybackCallbacks,
    ): PlaybackStartResult

    fun seekToAuthorized(positionMs: Long)

    fun currentPositionMs(): Long

    fun durationMs(): Long

    fun isPlaying(): Boolean

    fun stopAndRelease()
}

class AndroidMediaPlayerEngine(
    private val focusCoordinator: AudioFocusCoordinator,
    private val mediaSessionCoordinator: AudioMediaSessionCoordinator,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
    private val clientId: String = UUID.randomUUID().toString(),
) : AudioPlaybackEngine {
    private val lock = Any()
    private var mediaPlayer: MediaPlayer? = null
    private var dataSource: InMemoryMediaDataSource? = null
    private var watchdogJob: Job? = null
    private var cachedDurationMs: Long = 0L
    private var anchorPositionMs: Long = 0L
    private var anchorElapsedMs: Long = 0L
    private val playingFlag = AtomicBoolean(false)

    constructor(
        context: Context,
        scope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        focusCoordinator = AudioFocusOwner.getInstance(context),
        mediaSessionCoordinator = AudioMediaSessionBridge.getInstance(context),
        scope = scope,
        ioDispatcher = ioDispatcher,
    )

    override suspend fun start(
        audioBytes: ByteArray,
        startPositionMs: Long,
        callbacks: AudioPlaybackCallbacks,
    ): PlaybackStartResult {
        stopAndRelease()

        val focusGranted = focusCoordinator.acquire(
            clientId = clientId,
            onFocusLost = {
                if (playingFlag.compareAndSet(true, false)) {
                    stopAndRelease()
                    callbacks.onFocusLost()
                }
            },
            onBecomingNoisy = {
                if (playingFlag.compareAndSet(true, false)) {
                    stopAndRelease()
                    callbacks.onBecomingNoisy()
                }
            },
        )
        if (!focusGranted) {
            return PlaybackStartResult.Failed
        }

        val source = InMemoryMediaDataSource(audioBytes)
        val player = MediaPlayer()
        return try {
            val preparedDuration = withContext(ioDispatcher) {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                player.setDataSource(source)
                player.prepare()
                val rawDuration = player.duration.coerceAtLeast(0).toLong()
                val clampedStart = if (rawDuration > 0L) {
                    startPositionMs.coerceIn(0L, rawDuration)
                } else {
                    startPositionMs.coerceAtLeast(0L)
                }
                if (clampedStart > 0L) {
                    player.seekTo(clampedStart.toInt())
                }
                enforceNormalSpeed(player)
                rawDuration
            }

            player.setOnCompletionListener {
                if (playingFlag.compareAndSet(true, false)) {
                    stopAndRelease()
                    callbacks.onCompleted()
                }
            }
            player.setOnErrorListener { _, _, _ ->
                if (playingFlag.compareAndSet(true, false)) {
                    stopAndRelease()
                    callbacks.onError()
                }
                true
            }

            synchronized(lock) {
                mediaPlayer = player
                dataSource = source
                cachedDurationMs = preparedDuration
                val clampedStart = if (preparedDuration > 0L) {
                    startPositionMs.coerceIn(0L, preparedDuration)
                } else {
                    startPositionMs.coerceAtLeast(0L)
                }
                anchorPositionMs = clampedStart
                anchorElapsedMs = elapsedRealtimeMs()
                player.start()
                playingFlag.set(true)
            }

            mediaSessionCoordinator.attach(
                clientId = clientId,
                positionMs = anchorPositionMs,
                onHeadsetPause = {
                    if (playingFlag.compareAndSet(true, false)) {
                        stopAndRelease()
                        callbacks.onHeadsetPause()
                    }
                },
                onSeekAttempted = {
                    snapBackToExpectedPosition()
                },
            )
            startWatchdogLocked()
            PlaybackStartResult.Started(durationMs = preparedDuration)
        } catch (cancelled: CancellationException) {
            releasePreparedResources(player, source)
            focusCoordinator.release(clientId)
            mediaSessionCoordinator.detach(clientId)
            throw cancelled
        } catch (_: Throwable) {
            releasePreparedResources(player, source)
            focusCoordinator.release(clientId)
            mediaSessionCoordinator.detach(clientId)
            PlaybackStartResult.Failed
        }
    }

    override fun seekToAuthorized(positionMs: Long) {
        synchronized(lock) {
            val player = mediaPlayer ?: return
            val clamped = if (cachedDurationMs > 0L) {
                positionMs.coerceIn(0L, cachedDurationMs)
            } else {
                positionMs.coerceAtLeast(0L)
            }
            anchorPositionMs = clamped
            anchorElapsedMs = elapsedRealtimeMs()
            runCatching { player.seekTo(clamped.toInt()) }
            mediaSessionCoordinator.updatePosition(clientId, clamped)
        }
    }

    override fun currentPositionMs(): Long = synchronized(lock) {
        val player = mediaPlayer ?: return anchorPositionMs
        runCatching { player.currentPosition.coerceAtLeast(0).toLong() }
            .getOrDefault(anchorPositionMs)
    }

    override fun durationMs(): Long = synchronized(lock) { cachedDurationMs }

    override fun isPlaying(): Boolean = playingFlag.get()

    override fun stopAndRelease() {
        playingFlag.set(false)
        val playerToRelease: MediaPlayer?
        val sourceToClose: InMemoryMediaDataSource?
        synchronized(lock) {
            watchdogJob?.cancel()
            watchdogJob = null
            playerToRelease = mediaPlayer
            sourceToClose = dataSource
            mediaPlayer = null
            dataSource = null
        }
        releasePreparedResources(playerToRelease, sourceToClose)
        mediaSessionCoordinator.detach(clientId)
        focusCoordinator.release(clientId)
    }

    private fun startWatchdogLocked() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive && playingFlag.get()) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!playingFlag.get()) break
                verifyPlaybackIntegrity()
            }
        }
    }

    private fun verifyPlaybackIntegrity() {
        synchronized(lock) {
            val player = mediaPlayer ?: return
            if (!playingFlag.get()) return
            enforceNormalSpeed(player)
            val expected = expectedPositionLocked()
            val actual = runCatching { player.currentPosition.coerceAtLeast(0).toLong() }.getOrNull() ?: return
            if (kotlin.math.abs(actual - expected) > SEEK_DRIFT_TOLERANCE_MS) {
                runCatching { player.seekTo(expected.toInt()) }
            }
            mediaSessionCoordinator.updatePosition(clientId, expected)
        }
    }

    private fun snapBackToExpectedPosition() {
        synchronized(lock) {
            val player = mediaPlayer ?: return
            if (!playingFlag.get()) return
            val expected = expectedPositionLocked()
            runCatching { player.seekTo(expected.toInt()) }
            mediaSessionCoordinator.updatePosition(clientId, expected)
        }
    }

    private fun expectedPositionLocked(): Long {
        val elapsed = (elapsedRealtimeMs() - anchorElapsedMs).coerceAtLeast(0L)
        val raw = anchorPositionMs + elapsed
        return if (cachedDurationMs > 0L) raw.coerceIn(0L, cachedDurationMs) else raw
    }

    private fun enforceNormalSpeed(player: MediaPlayer) {
        runCatching {
            val params = player.playbackParams
            if (params.speed != 1.0f) {
                player.playbackParams = params.setSpeed(1.0f)
            }
        }
    }

    private fun releasePreparedResources(player: MediaPlayer?, source: InMemoryMediaDataSource?) {
        if (player != null) {
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        if (source != null) {
            runCatching { source.close() }
        }
    }

    companion object {
        private const val WATCHDOG_INTERVAL_MS = 250L
        private const val SEEK_DRIFT_TOLERANCE_MS = 1_500L
    }
}
