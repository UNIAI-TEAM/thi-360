package vn.unicom.thi360.feature.attachments.playback

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.content.IntentCompat

interface AudioMediaSessionCoordinator {
    fun attach(
        clientId: String,
        positionMs: Long,
        onHeadsetPause: () -> Unit,
        onSeekAttempted: () -> Unit,
    )

    fun updatePosition(clientId: String, positionMs: Long)

    fun detach(clientId: String)
}

class AudioMediaSessionBridge private constructor(
    context: Context,
) : AudioMediaSessionCoordinator {
    private data class SessionClient(
        val onHeadsetPause: () -> Unit,
        val onSeekAttempted: () -> Unit,
    )

    private val appContext = context.applicationContext
    private val lock = Any()
    private val activeClients = LinkedHashMap<String, SessionClient>()
    private var mediaSession: MediaSession? = null
    private var lastPlaybackState: PlaybackState? = null

    val sessionToken: MediaSession.Token?
        get() = synchronized(lock) { mediaSession?.sessionToken }

    val currentPlaybackState: PlaybackState?
        get() = synchronized(lock) { lastPlaybackState }

    override fun attach(
        clientId: String,
        positionMs: Long,
        onHeadsetPause: () -> Unit,
        onSeekAttempted: () -> Unit,
    ) {
        synchronized(lock) {
            val session = ensureSessionLocked()
            activeClients[clientId] = SessionClient(
                onHeadsetPause = onHeadsetPause,
                onSeekAttempted = onSeekAttempted,
            )
            val state = buildPlaybackState(isPlaying = true, positionMs = positionMs)
            lastPlaybackState = state
            session.setPlaybackState(state)
            session.isActive = true
        }
    }

    override fun updatePosition(clientId: String, positionMs: Long) {
        synchronized(lock) {
            if (!activeClients.containsKey(clientId)) return
            val session = mediaSession ?: return
            val state = buildPlaybackState(isPlaying = true, positionMs = positionMs)
            lastPlaybackState = state
            session.setPlaybackState(state)
        }
    }

    override fun detach(clientId: String) {
        synchronized(lock) {
            activeClients.remove(clientId)
            if (activeClients.isEmpty()) {
                val session = mediaSession ?: return
                val state = buildPlaybackState(isPlaying = false, positionMs = lastPlaybackState?.position ?: 0L)
                lastPlaybackState = state
                session.setPlaybackState(state)
                session.isActive = false
            }
        }
    }

    internal fun releaseForTests() {
        synchronized(lock) {
            activeClients.clear()
            runCatching {
                mediaSession?.isActive = false
                mediaSession?.release()
            }
            mediaSession = null
            lastPlaybackState = null
        }
    }

    private fun ensureSessionLocked(): MediaSession {
        mediaSession?.let { return it }
        return MediaSession(appContext, SESSION_TAG).apply {
            setCallback(sessionCallback, Handler(Looper.getMainLooper()))
            val initial = buildPlaybackState(isPlaying = false, positionMs = 0L)
            lastPlaybackState = initial
            setPlaybackState(initial)
            mediaSession = this
        }
    }

    private val sessionCallback = object : MediaSession.Callback() {
        override fun onPause() {
            dispatchHeadsetPause()
        }

        override fun onStop() {
            dispatchHeadsetPause()
        }

        override fun onPlay() {
            // Never auto-resume from system media controls; learner must tap Resume in the UI (R-134, R-136).
        }

        override fun onSeekTo(pos: Long) {
            dispatchSeekRejected()
        }

        override fun onSetPlaybackSpeed(speed: Float) = Unit

        override fun onFastForward() {
            dispatchSeekRejected()
        }

        override fun onRewind() {
            dispatchSeekRejected()
        }

        override fun onSkipToNext() = Unit

        override fun onSkipToPrevious() = Unit

        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            val keyEvent = IntentCompat.getParcelableExtra(
                mediaButtonIntent,
                Intent.EXTRA_KEY_EVENT,
                KeyEvent::class.java,
            ) ?: return true

            if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_STOP,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_HEADSETHOOK,
                    -> dispatchHeadsetPause()

                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_REWIND,
                    KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
                    KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                    -> dispatchSeekRejected()

                    else -> Unit
                }
            }
            return true
        }
    }

    private fun dispatchHeadsetPause() {
        val clients = synchronized(lock) {
            val snapshot = activeClients.values.toList()
            activeClients.clear()
            mediaSession?.let { session ->
                val state = buildPlaybackState(isPlaying = false, positionMs = lastPlaybackState?.position ?: 0L)
                lastPlaybackState = state
                session.setPlaybackState(state)
                session.isActive = false
            }
            snapshot
        }
        for (client in clients) {
            runCatching { client.onHeadsetPause() }
        }
    }

    private fun dispatchSeekRejected() {
        val clients = synchronized(lock) { activeClients.values.toList() }
        for (client in clients) {
            runCatching { client.onSeekAttempted() }
        }
    }

    companion object {
        private const val SESSION_TAG = "Thi360AttemptAudio"

        // Only pause / play-pause actions are exposed; seek, skip, and speed controls are deliberately omitted (R-134).
        const val ALLOWED_ACTIONS: Long = PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PAUSE

        fun buildPlaybackState(isPlaying: Boolean, positionMs: Long): PlaybackState =
            PlaybackState.Builder()
                .setActions(ALLOWED_ACTIONS)
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs.coerceAtLeast(0L),
                    if (isPlaying) 1.0f else 0.0f,
                )
                .build()

        @Volatile
        private var instance: AudioMediaSessionBridge? = null

        fun getInstance(context: Context): AudioMediaSessionBridge =
            instance ?: synchronized(this) {
                instance ?: AudioMediaSessionBridge(context).also { instance = it }
            }
    }
}
