package org.yiddishcalendar

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Places the three pieces of a day cell so they never overlap: the small corner number,
 * the big day number, and the holiday label at the bottom. The label is shrunk first, then
 * shortened with a fixed abbreviation if it still does not fit. Used by [DayCellView] and tests.
 */
object DayCellLayout {
    const val MIN_LABEL_SP = 5.5f
    const val MIN_MAIN_SP = 8f
    const val GAP_DP = 1.5f

    /** Shorter forms, longest first. Never a mid-word ellipsis. */
    private val SHORTEN = mapOf(
        "חוה״מ" to listOf("חה״מ", "ח״מ"),
        "שבועות" to listOf("שבוע׳", "שב׳"),
        "חנוכה" to listOf("חנו׳", "חנ׳"),
        "סוכות" to listOf("סוכ׳", "סכ׳"),
        "ט״ו באב" to listOf("ט״ו אב", "ט״ו"),
        "ט׳ באב" to listOf("ט׳ אב", "ט״ב"),
        "ט״ו בש׳" to listOf("ט״ו ש׳", "ט״ו"),
        "תענית" to listOf("תענ׳", "ת״ע"),
        "פורים" to listOf("פור׳"),
        "שושן" to listOf("שוש׳"),
        "ערב פסח" to listOf("ע״פ"),
        "עיו״כ" to listOf("עיו״כ"),
        "שמח״ת" to listOf("שמח״ת", "שמ״ת"),
    )

    data class Result(
        val mainText: String,
        val smallText: String,
        val labelText: String?,
        val main: RectF,
        val small: RectF,
        val label: RectF,
        val mainSizePx: Float,
        val smallSizePx: Float,
        val labelSizePx: Float,
        val mainX: Float = 0f,
        val mainY: Float = 0f,
        val smallX: Float = 0f,
        val smallY: Float = 0f,
        val labelX: Float = 0f,
        val labelY: Float = 0f,
        val inset: Float = 0f,
    ) {
        /** True if any two drawn ink boxes overlap (with a tiny float slack). */
        fun overlaps(slack: Float = 0.5f): Boolean {
            fun hit(a: RectF, b: RectF): Boolean {
                if (a.width() <= 0f || a.height() <= 0f || b.width() <= 0f || b.height() <= 0f) return false
                return a.left < b.right - slack && a.right > b.left + slack &&
                    a.top < b.bottom - slack && a.bottom > b.top + slack
            }
            return hit(main, small) || hit(main, label) || hit(small, label)
        }

        fun inside(left: Float, top: Float, right: Float, bottom: Float, slack: Float = 0.75f): Boolean {
            fun ok(r: RectF): Boolean {
                if (r.width() <= 0f || r.height() <= 0f) return true
                return r.left >= left - slack && r.top >= top - slack &&
                    r.right <= right + slack && r.bottom <= bottom + slack
            }
            return ok(main) && ok(small) && ok(label)
        }
    }

    /**
     * Choose a label that fits [maxWidthPx] at a size between [startSizePx] and [minSizePx].
     * Prefers the full name at a smaller size; only shortens if the minimum size is still too wide.
     */
    fun fitLabel(text: String, paint: Paint, maxWidthPx: Float, startSizePx: Float, minSizePx: Float): Pair<String, Float> {
        if (text.isEmpty() || maxWidthPx <= 0f) return text to startSizePx
        fun fits(s: String, size: Float): Boolean {
            paint.textSize = size
            return paint.measureText(s) <= maxWidthPx + 0.25f
        }
        fun shrink(s: String): Float? {
            var size = startSizePx
            if (fits(s, size)) return size
            while (size > minSizePx + 0.05f) {
                size = (size - 0.5f).coerceAtLeast(minSizePx)
                if (fits(s, size)) return size
            }
            return if (fits(s, minSizePx)) minSizePx else null
        }
        shrink(text)?.let { return text to it }
        for (alt in SHORTEN[text].orEmpty()) {
            shrink(alt)?.let { return alt to it }
        }
        val last = SHORTEN[text]?.lastOrNull() ?: text
        return last to minSizePx
    }

    fun place(
        width: Int,
        height: Int,
        compact: Boolean,
        rtl: Boolean,
        mainText: String,
        smallText: String,
        labelText: String?,
        density: Float,
        scaled: Float,
        pMain: Paint,
        pSmall: Paint,
        pLabel: Paint,
        scratch: Rect = Rect(),
    ): Result {
        val inset = (if (compact) 3.5f else 4f) * density
        val gap = GAP_DP * density
        val left = inset
        val top = inset
        val right = width - inset
        val bottom = height - inset
        val innerW = (right - left).coerceAtLeast(1f)

        fun ink(p: Paint, s: String): Rect {
            if (s.isEmpty()) {
                scratch.set(0, 0, 0, 0)
                return scratch
            }
            p.getTextBounds(s, 0, s.length, scratch)
            return scratch
        }
        fun box(x: Float, y: Float, w: Float, b: Rect): RectF =
            RectF(x + b.left, y + b.top, x + b.left + b.width(), y + b.top + b.height())

        // --- small corner number (top, reading side) ---
        val smallSize = (if (compact) 8f else 9f) * scaled
        val smallDrawn = smallText
        val smallRect = RectF()
        var smallBase = top
        var smallX = left
        if (smallDrawn.isNotEmpty()) {
            pSmall.textSize = smallSize
            val sb = ink(pSmall, smallDrawn)
            val sw = pSmall.measureText(smallDrawn)
            smallX = if (rtl) right - sw else left
            smallBase = top - sb.top
            smallRect.set(box(smallX, smallBase, sw, sb))
        }

        // --- holiday label at the bottom, shrunk then abbreviated ---
        val prefLabel = (if (compact) 8f else 9f) * scaled
        val minLabel = MIN_LABEL_SP * scaled
        var labelDrawn: String? = null
        var labelSize = prefLabel
        val labelRect = RectF()
        var labelX = left
        var labelBase = bottom
        val rawLabel = labelText?.takeIf { it.isNotEmpty() }
        if (rawLabel != null) {
            val fitted = fitLabel(rawLabel, pLabel, innerW, prefLabel, minLabel)
            labelDrawn = fitted.first
            labelSize = fitted.second
            pLabel.textSize = labelSize
            val lb = ink(pLabel, labelDrawn)
            val lw = pLabel.measureText(labelDrawn)
            labelX = left + (innerW - lw) / 2f
            labelBase = bottom - maxOf(0, lb.bottom).toFloat()
            labelRect.set(box(labelX, labelBase, lw, lb))
        }

        // Leave enough vertical room for the main number: shrink the label if it steals the cell.
        val prefMain = (if (compact) 13f else 15f) * scaled
        val minMain = MIN_MAIN_SP * scaled
        fun mainMinHeight(): Float {
            pMain.textSize = minMain
            val s = if (mainText.isEmpty()) "א" else mainText
            return ink(pMain, s).height().toFloat()
        }
        if (labelDrawn != null) {
            val need = mainMinHeight() + (if (smallDrawn.isNotEmpty()) smallRect.height() + gap else 0f) + gap
            val room = labelRect.top - top
            if (room < need && labelSize > minLabel) {
                var size = labelSize
                while (size > minLabel + 0.05f && labelRect.top - top < need) {
                    size = (size - 0.5f).coerceAtLeast(minLabel)
                    pLabel.textSize = size
                    val lb = ink(pLabel, labelDrawn!!)
                    val lw = pLabel.measureText(labelDrawn!!)
                    labelX = left + (innerW - lw) / 2f
                    labelBase = bottom - maxOf(0, lb.bottom).toFloat()
                    labelRect.set(box(labelX, labelBase, lw, lb))
                    labelSize = size
                }
            }
        }

        // --- main number in the zone above the label, not overlapping the corner number ---
        var mainSize = prefMain
        val zoneBottom = if (labelDrawn != null) labelRect.top - gap else bottom
        val zoneTop = top
        val zoneH = (zoneBottom - zoneTop).coerceAtLeast(0f)
        pMain.textSize = mainSize
        var mb = Rect().also { ink(pMain, mainText.ifEmpty { "0" }); it.set(scratch) }
        fun refitMain() {
            pMain.textSize = mainSize
            ink(pMain, mainText.ifEmpty { "0" })
            mb.set(scratch)
        }
        while (mb.height() > zoneH && mainSize > minMain + 0.05f) {
            mainSize = (mainSize - 0.5f).coerceAtLeast(minMain)
            refitMain()
        }
        var inkTop = zoneTop + (zoneH - mb.height()) / 2f
        var inkLeft = left + (innerW - mb.width()) / 2f
        val smallBottom = if (smallDrawn.isEmpty()) zoneTop else smallRect.bottom + gap
        val smallEdge = when {
            smallDrawn.isEmpty() -> if (rtl) right else left
            rtl -> smallRect.left - gap
            else -> smallRect.right + gap
        }
        fun touchesSmall(): Boolean {
            if (smallDrawn.isEmpty()) return false
            val mainR = RectF(inkLeft, inkTop, inkLeft + mb.width(), inkTop + mb.height())
            return mainR.top < smallRect.bottom + gap &&
                (if (rtl) mainR.right > smallEdge else mainR.left < smallEdge)
        }
        if (touchesSmall()) {
            val roomH = zoneBottom - smallBottom
            if (roomH >= mb.height()) {
                inkTop = smallBottom + (roomH - mb.height()) / 2f
            } else {
                // Prefer stacking under the corner number, shrinking if needed, so the
                // main figure is never squeezed a pixel past the inset beside the small number.
                while (mb.height() > roomH && mainSize > minMain + 0.05f) {
                    mainSize = (mainSize - 0.5f).coerceAtLeast(minMain)
                    refitMain()
                }
                if (mb.height() <= roomH) {
                    inkTop = smallBottom + (roomH - mb.height()) / 2f
                    inkLeft = left + (innerW - mb.width()) / 2f
                } else {
                    val room = if (rtl) smallEdge - left else right - smallEdge
                    while (mb.width() > room && mainSize > minMain + 0.05f) {
                        mainSize = (mainSize - 0.5f).coerceAtLeast(minMain)
                        refitMain()
                    }
                    if (mb.width() <= room) {
                        inkTop = zoneTop + (zoneH - mb.height()) / 2f
                        inkLeft = if (rtl) left + (room - mb.width()) / 2f else smallEdge + (room - mb.width()) / 2f
                        if (inkTop + mb.height() > zoneBottom) {
                            inkTop = (zoneBottom - mb.height()).coerceAtLeast(zoneTop)
                        }
                    } else {
                        inkLeft = left + (innerW - mb.width()) / 2f
                        inkTop = (zoneBottom - mb.height()).coerceAtLeast(zoneTop)
                    }
                }
            }
        }
        // Last guard: never let the main ink sit on the label or leave the inner box.
        var mainRect = RectF(inkLeft, inkTop, inkLeft + mb.width(), inkTop + mb.height())
        if (labelDrawn != null && mainRect.bottom > labelRect.top - 0.25f) {
            inkTop = (labelRect.top - gap - mb.height()).coerceAtLeast(zoneTop)
            mainRect = RectF(inkLeft, inkTop, inkLeft + mb.width(), inkTop + mb.height())
            while (mainRect.bottom > labelRect.top - 0.25f && mainSize > minMain + 0.05f) {
                mainSize = (mainSize - 0.5f).coerceAtLeast(minMain)
                refitMain()
                inkTop = (labelRect.top - gap - mb.height()).coerceAtLeast(zoneTop)
                inkLeft = left + (innerW - mb.width()) / 2f
                mainRect = RectF(inkLeft, inkTop, inkLeft + mb.width(), inkTop + mb.height())
            }
        }
        var mainX = inkLeft - mb.left
        var mainY = inkTop - mb.top
        mainRect = box(mainX, mainY, mb.width().toFloat(), mb)

        fun shiftInto(r: RectF, x: Float, y: Float): Pair<Float, Float> {
            var nx = x
            var ny = y
            if (r.width() <= innerW) {
                if (r.left < left) {
                    val d = left - r.left
                    r.offset(d, 0f); nx += d
                } else if (r.right > right) {
                    val d = right - r.right
                    r.offset(d, 0f); nx += d
                }
            } else {
                val d = left + (innerW - r.width()) / 2f - r.left
                r.offset(d, 0f); nx += d
            }
            val innerH = (bottom - top).coerceAtLeast(1f)
            if (r.height() <= innerH) {
                if (r.top < top) {
                    val d = top - r.top
                    r.offset(0f, d); ny += d
                } else if (r.bottom > bottom) {
                    val d = bottom - r.bottom
                    r.offset(0f, d); ny += d
                }
            }
            return nx to ny
        }
        val (sx, sy) = shiftInto(smallRect, smallX, smallBase)
        smallX = sx; smallBase = sy
        val (lx, ly) = shiftInto(labelRect, labelX, labelBase)
        labelX = lx; labelBase = ly
        val (mx, my) = shiftInto(mainRect, mainX, mainY)
        mainX = mx; mainY = my
        // If clamping pushed the main number onto the label or the corner number, park it in the
        // remaining gap and shrink until the ink boxes are separate.
        fun hit(a: RectF, b: RectF): Boolean {
            if (a.width() <= 0f || a.height() <= 0f || b.width() <= 0f || b.height() <= 0f) return false
            return a.left < b.right - 0.5f && a.right > b.left + 0.5f &&
                a.top < b.bottom - 0.5f && a.bottom > b.top + 0.5f
        }
        while ((hit(mainRect, labelRect) || hit(mainRect, smallRect)) && mainSize > minMain + 0.05f) {
            mainSize = (mainSize - 0.5f).coerceAtLeast(minMain)
            refitMain()
            val cap = if (labelDrawn != null) labelRect.top - gap else bottom
            val floor = if (smallDrawn.isNotEmpty()) (smallRect.bottom + gap).coerceAtMost(cap) else top
            inkTop = if (mb.height() <= cap - floor) floor + (cap - floor - mb.height()) / 2f
                else (cap - mb.height()).coerceAtLeast(top)
            inkLeft = left + (innerW - mb.width()) / 2f
            mainX = inkLeft - mb.left
            mainY = inkTop - mb.top
            mainRect.set(box(mainX, mainY, mb.width().toFloat(), mb))
            val shifted = shiftInto(mainRect, mainX, mainY)
            mainX = shifted.first; mainY = shifted.second
        }

        return Result(
            mainText = mainText,
            smallText = smallDrawn,
            labelText = labelDrawn,
            main = mainRect,
            small = smallRect,
            label = labelRect,
            mainSizePx = mainSize,
            smallSizePx = if (smallDrawn.isEmpty()) 0f else smallSize,
            labelSizePx = if (labelDrawn == null) 0f else labelSize,
            mainX = mainX,
            mainY = mainY,
            smallX = smallX,
            smallY = smallBase,
            labelX = labelX,
            labelY = labelBase,
            inset = inset,
        )
    }
}
