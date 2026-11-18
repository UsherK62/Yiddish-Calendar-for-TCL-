package org.yiddishcalendar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat

/**
 * One month-grid day, drawn directly so every piece has a fixed, measured place in a tiny
 * (~30x29dp) cell. TextViews could not do this: the Hebrew font has ~0.47em of empty space above
 * the letters, so stacked TextViews either clipped or collided.
 *  - [main]: big number of the primary calendar (Hebrew letters, or the Gregorian day).
 *  - [small]: the other calendar's number, in the top corner on the reading side.
 *  - [label]: short Yom Tov / ר״ח label at the bottom. It is shrunk, then abbreviated if needed,
 *    and never allowed to overlap either number.
 * Everything stays inside [inset] (default 4dp), so the 2dp selection ring never touches content.
 */
class DayCellView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var main = ""; var small = ""; var label: String? = null
    var mainColor = 0; var smallColor = 0; var labelColor = 0; var dotColor = 0
    var eventDot = false
    /** Six-row pages have shorter cells: everything one step smaller. */
    var compact = false
    var rtl = true
    /** Selection ring, drawn in onDraw so a tap ripple cannot hide it. Cells are never focusable. */
    var selectedRing = false
    var ringColor = 0
    /** 0–1 overlay used by the one-shot "Today" glow. */
    var glowAlpha = 0f
        private set
    /** How many times [pulseHighlight] has been started on this view (for tests). */
    var highlightPulses = 0
        private set
    private var glowAnim: ValueAnimator? = null

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
    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val r = Rect()
    /** Last layout used to draw the cell. Tests read the ink boxes here. */
    var lastLayout: DayCellLayout.Result? = null
        private set

    /** Place the three pieces and return the ink boxes. Safe to call from tests without drawing. */
    fun layoutNow(): DayCellLayout.Result {
        val placed = DayCellLayout.place(
            width = width,
            height = height,
            compact = compact,
            rtl = rtl,
            mainText = main,
            smallText = small,
            labelText = label,
            density = density,
            scaled = scaled,
            pMain = pMain,
            pSmall = pSmall,
            pLabel = pLabel,
            scratch = r,
        )
        lastLayout = placed
        pMain.textSize = placed.mainSizePx
        pSmall.textSize = placed.smallSizePx
        pLabel.textSize = placed.labelSizePx
        return placed
    }

    /** A short single glow (fade in and out) on this cell, used when the user jumps to Today. */
    fun pulseHighlight() {
        highlightPulses++
        glowAnim?.cancel()
        if (!Motion.enabled(context)) { glowAlpha = 0f; invalidate(); return }
        glowAnim = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = Motion.duration(context, Motion.TODAY_PULSE_MS)
            addUpdateListener {
                glowAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaled


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val placed = layoutNow()
        val inset = placed.inset
        val left = inset
        val top = inset
        val right = width - inset

        placed.labelText?.let { l ->
            pLabel.color = labelColor
            canvas.drawText(l, placed.labelX, placed.labelY, pLabel)
        }
        if (placed.smallText.isNotEmpty()) {
            pSmall.color = smallColor
            canvas.drawText(placed.smallText, placed.smallX, placed.smallY, pSmall)
        }
        if (placed.mainText.isNotEmpty()) {
            pMain.color = mainColor
            canvas.drawText(placed.mainText, placed.mainX, placed.mainY, pMain)
        }

        if (eventDot) {
            pDot.color = dotColor
            val rr = dp(2f)
            val cx = if (rtl) left + rr else right - rr
            canvas.drawCircle(cx, top + rr, rr, pDot)
        }

        // A soft fill glow (Today pulse), under the crisp 2dp ring.
        if (glowAlpha > 0f && width > 0 && height > 0) {
            val color = if (ringColor != 0) ringColor else mainColor
            pGlow.color = color
            pGlow.alpha = (0.40f * glowAlpha * 255).toInt().coerceIn(0, 255)
            val radius = dp(6f)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, pGlow)
        }

        // Ring last, on top of the ripple and the glow, same 2dp rounded stroke the tile used to have.
        if (selectedRing && ringColor != 0 && width > 0 && height > 0) {
            pRing.color = ringColor
            pRing.strokeWidth = dp(2f)
            val half = pRing.strokeWidth / 2f
            val radius = dp(6f)
            canvas.drawRoundRect(half, half, width - half, height - half, radius, radius, pRing)
        }
    }
}
