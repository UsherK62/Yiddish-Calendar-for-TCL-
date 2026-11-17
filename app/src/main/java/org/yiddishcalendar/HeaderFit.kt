package org.yiddishcalendar

import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month-bar titles that always show the year and never ellipsize, even on a 240 dp
 * screen with a large system font. The Gregorian title uses the full month name
 * ("December 2026") when it fits, otherwise the short name ("Dec 2026"). The
 * Hebrew title is only sized down if it would otherwise be cut off.
 */
data class FittedHeader(
    val start: String,
    val end: String,
    val startSizeSp: Float,
    val endSizeSp: Float,
)

object HeaderFit {
    const val MIN_MAIN_SP = 14f
    const val MIN_OTHER_SP = 10f
    const val ABS_MIN_SP = 9f

    fun gregorian(month: YearMonth, long: Boolean): String {
        val style = if (long) TextStyle.FULL else TextStyle.SHORT
        return month.month.getDisplayName(style, Locale.US) + " " + month.year
    }

    /**
     * Pick texts and sizes so [start] and [end] both fit in [availablePx] with a [gapPx] between them.
     * [gregorian] + [gregorianOnStart] name the single-month Gregorian title that may drop to a short form.
     */
    fun layout(
        startText: String,
        endText: String,
        startIsMain: Boolean,
        gregorian: YearMonth?,
        gregorianOnStart: Boolean?,
        startSizeSp: Float,
        endSizeSp: Float,
        availablePx: Float,
        gapPx: Float,
        startPaint: Paint,
        endPaint: Paint,
        scaledDensity: Float,
    ): FittedHeader {
        var start = when {
            gregorian != null && gregorianOnStart == true -> gregorian(gregorian, true)
            else -> startText
        }
        var end = when {
            gregorian != null && gregorianOnStart == false -> gregorian(gregorian, true)
            else -> endText
        }
        var startSp = startSizeSp
        var endSp = endSizeSp
        val scaled = if (scaledDensity <= 0f) 1f else scaledDensity

        fun width(paint: Paint, sp: Float, text: String): Float {
            paint.textSize = sp * scaled
            return paint.measureText(text)
        }
        fun fits(): Boolean {
            if (availablePx <= 0f) return true
            return width(startPaint, startSp, start) + gapPx + width(endPaint, endSp, end) <= availablePx + 0.5f
        }

        if (!fits() && gregorian != null && gregorianOnStart != null) {
            if (gregorianOnStart) start = gregorian(gregorian, false)
            else end = gregorian(gregorian, false)
        }
        if (!fits()) {
            var otherSp = if (startIsMain) endSp else startSp
            while (!fits() && otherSp > MIN_OTHER_SP) {
                otherSp = (otherSp - 0.5f).coerceAtLeast(MIN_OTHER_SP)
                if (startIsMain) endSp = otherSp else startSp = otherSp
            }
            var mainSp = if (startIsMain) startSp else endSp
            while (!fits() && mainSp > MIN_MAIN_SP) {
                mainSp = (mainSp - 0.5f).coerceAtLeast(MIN_MAIN_SP)
                if (startIsMain) startSp = mainSp else endSp = mainSp
            }
            while (!fits() && (startSp > ABS_MIN_SP || endSp > ABS_MIN_SP)) {
                if (endSp > ABS_MIN_SP) endSp = (endSp - 0.5f).coerceAtLeast(ABS_MIN_SP)
                if (!fits() && startSp > ABS_MIN_SP) startSp = (startSp - 0.5f).coerceAtLeast(ABS_MIN_SP)
            }
        }
        return FittedHeader(start, end, startSp, endSp)
    }

    /** Apply [layout] to the two header labels. Posts itself once if the bar has not been measured yet. */
    fun apply(
        title: TextView,
        right: TextView,
        header: HeaderText,
        gregorian: YearMonth?,
        gregorianOnStart: Boolean?,
        gapPx: Float,
    ) {
        title.ellipsize = null
        right.ellipsize = null
        title.maxLines = 1
        right.maxLines = 1
        val parent = title.parent as? View ?: return
        val avail = (parent.width - parent.paddingLeft - parent.paddingRight).toFloat()
        if (avail <= 0f) {
            title.text = if (gregorian != null && gregorianOnStart == true) gregorian(gregorian, true) else header.start
            right.text = if (gregorian != null && gregorianOnStart == false) gregorian(gregorian, true) else header.end
            if (parent.getTag(R.id.header) != true) {
                parent.setTag(R.id.header, true)
                parent.post {
                    parent.setTag(R.id.header, false)
                    if (parent.width > 0) apply(title, right, header, gregorian, gregorianOnStart, gapPx)
                }
            }
            return
        }
        val fitted = layout(
            startText = header.start,
            endText = header.end,
            startIsMain = header.startIsMain,
            gregorian = gregorian,
            gregorianOnStart = gregorianOnStart,
            startSizeSp = pxToSp(title.textSize, title),
            endSizeSp = pxToSp(right.textSize, right),
            availablePx = avail,
            gapPx = gapPx,
            startPaint = Paint(title.paint),
            endPaint = Paint(right.paint),
            scaledDensity = title.resources.displayMetrics.scaledDensity,
        )
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, fitted.startSizeSp)
        right.setTextSize(TypedValue.COMPLEX_UNIT_SP, fitted.endSizeSp)
        title.text = fitted.start
        right.text = fitted.end
    }

    private fun pxToSp(px: Float, view: View): Float {
        val scaled = view.resources.displayMetrics.scaledDensity
        return if (scaled <= 0f) px else px / scaled
    }
}
