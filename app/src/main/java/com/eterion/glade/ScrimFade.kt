package com.eterion.glade

/** A reversible fade, driven only while the drawable needs animation frames. */
internal class ScrimFade {
    private var from = 0f
    private var target = 0f
    private var started = 0L

    fun update(visible: Boolean, now: Long): Float {
        val next = if (visible) 1f else 0f
        if (next != target) {
            from = value(now)
            target = next
            started = now
        }
        return value(now)
    }

    private fun value(now: Long): Float {
        val fraction = ((now - started) / 180f).coerceIn(0f, 1f)
        val eased = fraction * fraction * (3f - 2f * fraction)
        return from + (target - from) * eased
    }

    fun isAnimating(now: Long) = from != target && now - started < 180

    fun reset() { from = 0f; target = 0f; started = 0L }
}
