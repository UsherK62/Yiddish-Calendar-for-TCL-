package org.yiddishcalendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import kotlin.math.max
import kotlin.math.min

/**
 * One month-grid day, drawn directly so every piece has a fixed, measured place in a tiny
 * (~30x29dp) cell. TextViews could not do this: the Hebrew font has ~0.47em of empty space above
 * the letters, so stacked TextViews either clipped or collided.
 *  - [main]: big number of the primary calendar (Hebrew letters, or the Gregorian day).
 *  - [small]: the other calendar's number, in the top corner on the reading side.
 *  - [label]: short Yom Tov / ר״ח label at the bottom, shrunk until it fits the width.
 * Everything stays inside [inset] (default 4dp), so the 2dp selection ring never touches content.
 */
class DayCellView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var main = ""; var small = ""; var label: String? = null
    var mainColor = 0; var smallColor = 0; var labelColor = 0; var dotColor = 0
    var eventDot = false
    /** Six-row pages have shorter cells: everything one step smaller. */
    var compact = false
    var rtl = true

    private val density = resources.displayMetrics.density
    private val scaled = resources.displayMetrics.scaledDensity
    private val hebrewBold: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.noto_sans_hebrew_bold) }.getOrNull()
        ?: Typeface.DEFAULT_BOLD
    private val hebrewRegular: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.noto_sans_hebrew_regular) }.getOrNull()
        ?: Typeface.DEFAULT
    private val pMain = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = hebrewBold; textAlign = Paint.Align.LEFT }
    private val pSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = hebrewRegular; textAlign = Paint.Align.LEFT }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = hebrewBold; textAlign = Paint.Align.LEFT }
    private val pDot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = Rect()

    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaled

    /** Ink bounds of [s] (relative to its baseline origin). */
    private fun ink(p: Paint, s: String): Rect = Rect().also { p.getTextBounds(s, 0, s.length, it) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = dp(if (compact) 3.5f else 4f)
        val left = inset; val top = inset
        val right = width - inset; val bottom = height - inset
        val w = right - left

        // Bottom label (only when there is one), auto-shrunk to the cell width.
        var zoneBottom = bottom
        label?.takeIf { it.isNotEmpty() }?.let { l ->
            var size = sp(if (compact) 8f else 9f)
            pLabel.textSize = size
            while (pLabel.measureText(l) > w && size > sp(5.5f)) { size -= sp(0.5f); pLabel.textSize = size }
            pLabel.color = labelColor
            val b = ink(pLabel, l)
            val x = left + (w - pLabel.measureText(l)) / 2f
            val baseline = bottom - max(0, b.bottom).toFloat()
            canvas.drawText(l, x, baseline, pLabel)
            zoneBottom = baseline + b.top - dp(1.5f) // top of the label ink, minus a gap
        }

        // Small corner number (top, reading side = right in RTL).
        pSmall.textSize = sp(if (compact) 8f else 9f)
        pSmall.color = smallColor
        val sb = ink(pSmall, small.ifEmpty { "0" })
        val sw = pSmall.measureText(small)
        val sx = if (rtl) right - sw else left
        val sBase = top - sb.top
        if (small.isNotEmpty()) canvas.drawText(small, sx, sBase, pSmall)

        // Main number: centred in the zone above the label. If it would touch the corner number:
        //  A) move it just below the corner number when it still fits above the label (stays centred), else
        //  B) put it beside the corner number on the other side, shrinking it a little if needed.
        pMain.textSize = sp(if (compact) 13f else 15f)
        pMain.color = mainColor
        val smallBottom = if (small.isEmpty()) top else sBase + sb.bottom + dp(1.5f)
        val smallEdge = if (small.isEmpty()) (if (rtl) right else left) else if (rtl) sx - dp(1.5f) else sx + sw + dp(1.5f)
        fun fitsHeight(b: Rect) = b.height() <= zoneBottom - top
        var mb = ink(pMain, main)
        while (!fitsHeight(mb) && pMain.textSize > sp(9f)) { pMain.textSize -= sp(0.5f); mb = ink(pMain, main) }
        var inkTop = top + (zoneBottom - top - mb.height()) / 2f
        var inkLeft = left + (w - mb.width()) / 2f
        val touches = small.isNotEmpty() && inkTop < smallBottom &&
            (if (rtl) inkLeft + mb.width() > smallEdge else inkLeft < smallEdge)
        if (touches) {
            if (smallBottom + mb.height() <= zoneBottom) {
                inkTop = smallBottom + (zoneBottom - smallBottom - mb.height()) / 2f
            } else {
                val room = if (rtl) smallEdge - left else right - smallEdge
                while (mb.width() > room && pMain.textSize > sp(10f)) { pMain.textSize -= sp(0.5f); mb = ink(pMain, main) }
                inkTop = top + (zoneBottom - top - mb.height()) / 2f
                inkLeft = if (rtl) left + (room - mb.width()) / 2f else smallEdge + (room - mb.width()) / 2f
            }
        }
        canvas.drawText(main, inkLeft - mb.left, inkTop - mb.top, pMain)

        if (eventDot) {
            pDot.color = dotColor
            val rr = dp(2f)
            val cx = if (rtl) left + rr else right - rr
            canvas.drawCircle(cx, top + rr, rr, pDot)
        }
    }
}
