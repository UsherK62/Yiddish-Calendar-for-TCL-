package org.yiddishcalendar

/**
 * Where the vertical month pager should land after a drag or fling.
 * Each gesture moves at most one month from the page we were already on (or already
 * settling toward). The grid is then snapped so one whole month is aligned.
 */
object MonthSnap {
    /** Short, snappy settle (decelerate). Used when motion is on. */
    const val DURATION_MS = 220L
    const val ALIGN_EPSILON_PX = 1

    fun isAligned(firstTop: Int, epsilon: Int = ALIGN_EPSILON_PX): Boolean =
        kotlin.math.abs(firstTop) <= epsilon

    /** Visual nearest page: if the first visible page is more than half off-screen, it's the next one. */
    fun nearest(firstVisible: Int, firstTop: Int, pageHeight: Int): Int =
        if (pageHeight > 0 && firstTop < -pageHeight / 2) firstVisible + 1 else firstVisible

    /**
     * Target page after a vertical gesture.
     * [current] is the page we were on when the gesture began (or already settling toward).
     * [vy] is Y velocity in px/s: negative = finger flicked up (next month), positive = down (previous).
     */
    fun targetOffset(
        current: Int,
        firstVisible: Int,
        firstTop: Int,
        pageHeight: Int,
        vy: Float,
        minFling: Int,
        lastOffset: Int,
    ): Int {
        val nearest = nearest(firstVisible, firstTop, pageHeight)
        val flicked = when {
            vy < -minFling -> current + 1
            vy > minFling -> current - 1
            else -> null
        }
        val raw = flicked ?: nearest
        return raw.coerceIn(current - 1, current + 1).coerceIn(0, lastOffset)
    }
}
