package vn.unicom.thi360.feature.attachments.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFocusRequest.Builder as FocusRequestBuilder
import android.media.AudioManager
import androidx.core.content.ContextCompat

interface AudioFocusCoordinator {
    fun acquire(
        clientId: String,
        onFocusLost: () -> Unit,
        onBecomingNoisy: () -> Unit,
    ): Boolean

    fun release(clientId: String)
}

class AudioFocusOwner private constructor(
    context: Context,
) : AudioFocusCoordinator {
    private data class ClientCallbacks(
        val onFocusLost: () -> Unit,
        val onBecomingNoisy: () -> Unit,
    )

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val lock = Any()
    private val activeClients = LinkedHashMap<String, ClientCallbacks>()
    private var focusHeld = false
    private var noisyReceiverRegistered = false
    private var activeFocusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> dispatchFocusLost()
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                dispatchBecomingNoisy()
            }
        }
    }

    override fun acquire(
        clientId: String,
        onFocusLost: () -> Unit,
        onBecomingNoisy: () -> Unit,
    ): Boolean {
        synchronized(lock) {
            val callbacks = ClientCallbacks(
                onFocusLost = onFocusLost,
                onBecomingNoisy = onBecomingNoisy,
            )
            // Already holding app-wide focus: register the new player without re-requesting focus
            // so concurrent in-app players never trigger focus loss on one another (R-136, AC-730).
            if (focusHeld) {
                activeClients[clientId] = callbacks
                return true
            }

            val focusRequest = FocusRequestBuilder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()

            val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!granted) {
                return false
            }

            activeFocusRequest = focusRequest
            focusHeld = true
            activeClients[clientId] = callbacks
            registerNoisyReceiverLocked()
            return true
        }
    }

    override fun release(clientId: String) {
        synchronized(lock) {
            activeClients.remove(clientId)
            if (activeClients.isEmpty()) {
                abandonFocusAndReceiverLocked()
            }
        }
    }

    internal val noisyBroadcastReceiver: BroadcastReceiver
        get() = noisyReceiver

    internal fun isNoisyReceiverRegistered(): Boolean = synchronized(lock) { noisyReceiverRegistered }

    internal fun activeClientCount(): Int = synchronized(lock) { activeClients.size }

    private fun dispatchFocusLost() {
        val clients = synchronized(lock) {
            val snapshot = activeClients.values.toList()
            activeClients.clear()
            abandonFocusAndReceiverLocked()
            snapshot
        }
        for (client in clients) {
            runCatching { client.onFocusLost() }
        }
    }

    private fun dispatchBecomingNoisy() {
        val clients = synchronized(lock) {
            val snapshot = activeClients.values.toList()
            activeClients.clear()
            abandonFocusAndReceiverLocked()
            snapshot
        }
        for (client in clients) {
            runCatching { client.onBecomingNoisy() }
        }
    }

    private fun registerNoisyReceiverLocked() {
        if (noisyReceiverRegistered) return
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyReceiverRegistered = true
        }
    }

    private fun abandonFocusAndReceiverLocked() {
        if (noisyReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(noisyReceiver) }
            noisyReceiverRegistered = false
        }
        activeFocusRequest?.let { req ->
            runCatching { audioManager.abandonAudioFocusRequest(req) }
        }
        activeFocusRequest = null
        focusHeld = false
    }

    companion object {
        @Volatile
        private var instance: AudioFocusOwner? = null

        fun getInstance(context: Context): AudioFocusOwner =
            instance ?: synchronized(this) {
                instance ?: AudioFocusOwner(context).also { instance = it }
            }
    }
}
