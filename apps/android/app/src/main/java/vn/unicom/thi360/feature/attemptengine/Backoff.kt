package vn.unicom.thi360.feature.attemptengine

class Backoff(
    private val stepsMs: LongArray = DEFAULT_STEPS_MS,
) {
    private var attemptIndex = 0

    fun nextDelayMs(): Long {
        val delay = stepsMs[attemptIndex.coerceAtMost(stepsMs.lastIndex)]
        if (attemptIndex < stepsMs.lastIndex) {
            attemptIndex++
        }
        return delay
    }

    fun reset() {
        attemptIndex = 0
    }

    companion object {
        val DEFAULT_STEPS_MS = longArrayOf(
            1_000L,
            2_000L,
            4_000L,
            8_000L,
            16_000L,
            30_000L,
        )
    }
}
