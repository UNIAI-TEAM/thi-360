package vn.unicom.thi360.feature.attachments.playback

import android.media.MediaDataSource

class InMemoryMediaDataSource(
    bytes: ByteArray,
) : MediaDataSource() {
    private val lock = Any()
    private var data: ByteArray? = bytes
    @Volatile
    var isClosed: Boolean = false
        private set

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0L || size < 0) return -1
        if (size == 0) return 0
        synchronized(lock) {
            val current = data ?: return -1
            if (position >= current.size) return -1
            val remaining = (current.size - position).toInt()
            val toRead = minOf(size, remaining)
            System.arraycopy(current, position.toInt(), buffer, offset, toRead)
            return toRead
        }
    }

    override fun getSize(): Long = synchronized(lock) {
        data?.size?.toLong() ?: -1L
    }

    override fun close() {
        synchronized(lock) {
            data?.fill(0)
            data = null
            isClosed = true
        }
    }
}
