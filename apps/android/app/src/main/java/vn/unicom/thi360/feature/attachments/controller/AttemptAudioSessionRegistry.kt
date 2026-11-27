package vn.unicom.thi360.feature.attachments.controller

import java.io.Closeable
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList
import vn.unicom.thi360.core.session.SessionCleanup

interface AudioLogoutCleanable {
    fun stopAndPurgeForLogout()
}

object AttemptAudioSessionRegistry {
    private val lock = Any()
    private val activeCleanables = CopyOnWriteArrayList<AudioLogoutCleanable>()
    private val registeredCleanups = Collections.newSetFromMap(IdentityHashMap<SessionCleanup, Boolean>())

    fun register(
        cleanable: AudioLogoutCleanable,
        sessionCleanup: SessionCleanup? = null,
    ): Closeable {
        activeCleanables.add(cleanable)
        if (sessionCleanup != null) {
            ensureCleanupHooked(sessionCleanup)
        }
        return Closeable {
            activeCleanables.remove(cleanable)
        }
    }

    fun ensureCleanupHooked(sessionCleanup: SessionCleanup) {
        synchronized(lock) {
            if (registeredCleanups.add(sessionCleanup)) {
                sessionCleanup.register {
                    purgeAllActive()
                }
            }
        }
    }

    fun purgeAllActive() {
        val snapshot = activeCleanables.toList()
        for (item in snapshot) {
            runCatching { item.stopAndPurgeForLogout() }
        }
    }

    internal fun activeCount(): Int = activeCleanables.size
}
