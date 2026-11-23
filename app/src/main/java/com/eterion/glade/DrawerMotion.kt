package com.eterion.glade

object DrawerMotion {
    const val OPEN_THRESHOLD = 0.5f

    fun shouldOpen(progress: Float, velocityY: Float, flingThreshold: Float): Boolean = when {
        velocityY < -flingThreshold -> true
        velocityY > flingThreshold -> false
        else -> progress >= OPEN_THRESHOLD
    }

    fun surfaceAlpha(progress: Float): Float = (progress / OPEN_THRESHOLD).coerceIn(0f, 1f)

    fun contentAlpha(progress: Float): Float {
        val fraction = ((progress - OPEN_THRESHOLD) / (1f - OPEN_THRESHOLD)).coerceIn(0f, 1f)
        return fraction * fraction * (3f - 2f * fraction)
    }
}
