package org.yiddishcalendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * A floating 2dp selection ring used to glide the highlight from one day cell to the next.
 * Hidden when idle, so it never steals taps or replaces the cell's own crisp ring.
 */
class SelectionRingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var ringColor: Int = 0
        set(v) { field = v; invalidate() }

    private val density = resources.displayMetrics.density
    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (ringColor == 0 || width == 0 || height == 0) return
        pRing.color = ringColor
        pRing.strokeWidth = 2f * density
        val half = pRing.strokeWidth / 2f
        val radius = 6f * density
        canvas.drawRoundRect(half, half, width - half, height - half, radius, radius, pRing)
    }

    fun place(left: Int, top: Int, width: Int, height: Int) {
        val lp = (layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(width, height)
        lp.width = width
        lp.height = height
        layoutParams = lp
        x = left.toFloat()
        y = top.toFloat()
        visibility = VISIBLE
        invalidate()
    }

    private var glideGen = 0

    fun glideTo(left: Int, top: Int, width: Int, height: Int, ms: Long, end: () -> Unit) {
        val lp = (layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(width, height)
        lp.width = width
        lp.height = height
        layoutParams = lp
        visibility = VISIBLE
        val gen = ++glideGen
        animate().cancel()
        animate().x(left.toFloat()).y(top.toFloat())
            .setDuration(ms)
            .withEndAction { if (gen == glideGen) end() }
            .start()
    }

    /** Cancel a running glide and drop its end callback, so a stale day cannot get the ring. */
    fun cancelGlide() {
        glideGen++
        animate().cancel()
    }

    fun hide() {
        cancelGlide()
        visibility = GONE
    }
}
