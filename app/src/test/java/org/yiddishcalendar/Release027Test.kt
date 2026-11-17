package org.yiddishcalendar

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Paint
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.core.app.ApplicationProvider
import com.kizitonwose.calendar.view.CalendarView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth

/**
 * 0.2.7: the Gregorian month title always shows the year and never ellipsizes;
 * the vertical month pager always settles on one whole month.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w240dp-h320dp-port-mdpi")
class Release027Test {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun prefs() {
        Motion.overrideEnabled = null
        ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("asked_perms", true).putBoolean("nightfall", false).commit()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @After fun reset() { Motion.overrideEnabled = null }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun settle() {
        repeat(30) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(40))
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
    private fun main(date: LocalDate = LocalDate.of(2027, 1, 21), hebrew: Boolean? = null): MainActivity {
        if (hebrew != null) {
            Prefs(ctx).applyHebrewMonths(hebrew)
        }
        return Robolectric.buildActivity(
            MainActivity::class.java,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TEST_DATE, date.toEpochDay()),
        ).setup().get().also { idle(); settle() }
    }
    private fun header(a: MainActivity) = a.findViewById<TextView>(R.id.headerTitle)
    private fun right(a: MainActivity) = a.findViewById<TextView>(R.id.headerRight)
    private fun cal(a: MainActivity) = a.findViewById<CalendarView>(R.id.calendar)

    private fun aligned(cv: CalendarView): Boolean {
        val lm = cv.layoutManager as LinearLayoutManager
        val first = lm.findFirstVisibleItemPosition()
        if (first == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return false
        val child = lm.findViewByPosition(first) ?: return false
        return MonthSnap.isAligned(child.top)
    }

    private fun ellipsis(tv: TextView): Int {
        val layout = tv.layout ?: return -1
        return if (layout.lineCount == 0) 0 else layout.getEllipsisCount(0)
    }

    private fun hasYear(s: CharSequence) = Regex("20\\d{2}").containsMatchIn(s)

    private fun swipeV(root: View, v: View, dyPx: Float, ms: Long = 50L, settleAfter: Boolean = true) {
        val loc = IntArray(2); v.getLocationInWindow(loc)
        val x = loc[0] + v.width / 2f; val y = loc[1] + v.height / 2f
        val t = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        val steps = 8
        for (i in 1..steps) {
            root.dispatchTouchEvent(MotionEvent.obtain(t, t + i * (ms / steps), MotionEvent.ACTION_MOVE, x, y + dyPx * i / steps, 0))
        }
        root.dispatchTouchEvent(MotionEvent.obtain(t, t + ms, MotionEvent.ACTION_UP, x, y + dyPx, 0))
        if (settleAfter) settle() else {
            ShadowSystemClock.advanceBy(Duration.ofMillis(10))
            idle()
        }
    }

    private fun press(a: MainActivity, code: Int) {
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        idle()
        settle()
    }

    // ---- Title fitting (unit, no activity) ----
    @Test fun gregorianTitleAlwaysIncludesTheYear() {
        for (m in 1..12) {
            val ym = YearMonth.of(2026, m)
            val full = HeaderFit.gregorian(ym, true)
            val short = HeaderFit.gregorian(ym, false)
            assertTrue(full, full.endsWith("2026") && " " in full)
            assertTrue(short, short.endsWith("2026") && " " in short)
            assertTrue("$full should be longer or equal", full.length >= short.length)
            val fullName = full.substringBefore(" ")
            val shortName = short.substringBefore(" ")
            if (fullName != shortName) assertFalse(short.startsWith(fullName))
            if (m == 12) {
                assertEquals("December 2026", full)
                assertEquals("Dec 2026", short)
            }
            if (m == 5) {
                assertEquals("May 2026", full)
                assertEquals("May 2026", short)
            }
        }
    }

    @Test fun layoutPrefersFullNameWhenItFits() {
        val p = Paint()
        val ym = YearMonth.of(2026, 12)
        val full = HeaderFit.gregorian(ym, true)
        p.textSize = 19f
        val heb = "כסלו–טבת תשפ״ז"
        val avail = p.measureText(full) + 8f + p.measureText(heb) + 4f
        val r = HeaderFit.layout(
            startText = "December 2026", endText = heb, startIsMain = true,
            gregorian = ym, gregorianOnStart = true,
            startSizeSp = 19f, endSizeSp = 13f,
            availablePx = avail, gapPx = 8f,
            startPaint = Paint(), endPaint = Paint(), scaledDensity = 1f,
        )
        assertEquals("December 2026", r.start)
        assertEquals(heb, r.end)
        assertTrue(r.startSizeSp >= 14f)
    }

    @Test fun layoutFallsBackToShortNameWhenNarrow() {
        val ym = YearMonth.of(2026, 12)
        val p = Paint()
        p.textSize = 19f
        val short = HeaderFit.gregorian(ym, false)
        val heb = "כסלו–טבת תשפ״ז"
        // Enough for "Dec 2026" + Hebrew, not enough for "December 2026".
        val avail = p.measureText(short) + 8f + Paint().apply { textSize = 13f }.measureText(heb) + 2f
        val r = HeaderFit.layout(
            startText = "December 2026", endText = heb, startIsMain = true,
            gregorian = ym, gregorianOnStart = true,
            startSizeSp = 19f, endSizeSp = 13f,
            availablePx = avail, gapPx = 8f,
            startPaint = Paint(), endPaint = Paint(), scaledDensity = 1f,
        )
        assertEquals("Dec 2026", r.start)
        assertEquals(heb, r.end)
        assertFalse(r.start.contains("…") || r.end.contains("…"))
    }

    @Test fun layoutShrinksRatherThanEllipsizingAtLargeFont() {
        val ym = YearMonth.of(2026, 9)
        val heb = "אלול–תשרי תשפ״ו"
        val r = HeaderFit.layout(
            startText = "September 2026", endText = heb, startIsMain = true,
            gregorian = ym, gregorianOnStart = true,
            startSizeSp = 19f, endSizeSp = 13f,
            availablePx = 160f, gapPx = 8f,
            startPaint = Paint(), endPaint = Paint(), scaledDensity = 1.5f,
        )
        assertTrue(r.start == "September 2026" || r.start == "Sep 2026")
        assertTrue(hasYear(r.start))
        assertEquals(heb, r.end)
        val p1 = Paint().apply { textSize = r.startSizeSp * 1.5f }
        val p2 = Paint().apply { textSize = r.endSizeSp * 1.5f }
        assertTrue(p1.measureText(r.start) + 8f + p2.measureText(r.end) <= 160.5f)
    }

    // ---- Title on the 240 dp month screen ----
    @Test fun gregorianHeaderShowsYearOn240() {
        Prefs(ctx).applyHebrewMonths(false)
        for (month in listOf(1, 9, 12)) {
            val a = main(LocalDate.of(2026, month, 15), hebrew = false)
            val t = header(a)
            val r = right(a)
            assertTrue("month $month title=${t.text}", hasYear(t.text))
            assertTrue(t.text.contains(HeaderFit.gregorian(YearMonth.of(2026, month), true))
                || t.text.contains(HeaderFit.gregorian(YearMonth.of(2026, month), false)))
            assertEquals("no ellipsis on title $month", 0, ellipsis(t))
            assertEquals("no ellipsis on Hebrew side $month", 0, ellipsis(r))
            assertTrue("Hebrew side still there $month: ${r.text}", r.text.isNotEmpty())
        }
    }

    @Test fun titlesNeverEllipsizeWithLargeSystemFont() {
        val metrics = ctx.resources.displayMetrics
        val cfg = Configuration(ctx.resources.configuration)
        cfg.fontScale = 1.5f
        @Suppress("DEPRECATION")
        ctx.resources.updateConfiguration(cfg, metrics)
        val a = main(LocalDate.of(2026, 12, 15), hebrew = false)
        val t = header(a)
        val r = right(a)
        assertTrue(t.text == "December 2026" || t.text == "Dec 2026")
        assertTrue(hasYear(t.text))
        assertEquals(0, ellipsis(t))
        assertEquals(0, ellipsis(r))
        assertTrue(r.text.isNotEmpty())
        assertFalse(t.text.contains("…") || r.text.contains("…") || t.text.contains('\u2026') || r.text.contains('\u2026'))
    }

    @Test fun hebrewTitleOnTheRightStillShowsAndFits() {
        val a = main(LocalDate.of(2027, 1, 21), hebrew = true)
        // Default RTL Hebrew months: start/title is the Hebrew month, end is the Gregorian range.
        val t = header(a)
        val r = right(a)
        assertTrue("Hebrew title: ${t.text}", t.text.contains("שבט") || t.text.contains("טבת"))
        assertTrue("Gregorian side still has a year: ${r.text}", hasYear(r.text))
        assertEquals(0, ellipsis(t))
        assertEquals(0, ellipsis(r))
    }

    // ---- Month snap math ----
    @Test fun snapMovesAtMostOneMonthAndPrefersAFling() {
        assertEquals(5, MonthSnap.nearest(5, -10, 200))
        assertEquals(6, MonthSnap.nearest(5, -101, 200))
        // Fast flick up from page 10, even if visually still on 10.
        assertEquals(11, MonthSnap.targetOffset(10, 10, 0, 200, vy = -4000f, minFling = 100, lastOffset = 240))
        // Fast flick down.
        assertEquals(9, MonthSnap.targetOffset(10, 10, 0, 200, vy = 4000f, minFling = 100, lastOffset = 240))
        // Drag past half without a fling: snap to the next page, but only one away.
        assertEquals(11, MonthSnap.targetOffset(10, 10, -120, 200, vy = 0f, minFling = 100, lastOffset = 240))
        // A drag that somehow shows page 13 still only moves one from current.
        assertEquals(11, MonthSnap.targetOffset(10, 13, 0, 200, vy = 0f, minFling = 100, lastOffset = 240))
        // Edges.
        assertEquals(0, MonthSnap.targetOffset(0, 0, 0, 200, vy = 4000f, minFling = 100, lastOffset = 240))
        assertEquals(240, MonthSnap.targetOffset(240, 240, 0, 200, vy = -4000f, minFling = 100, lastOffset = 240))
        assertTrue(MonthSnap.isAligned(0))
        assertTrue(MonthSnap.isAligned(1))
        assertFalse(MonthSnap.isAligned(20))
        assertTrue(MonthSnap.DURATION_MS in 180..250)
    }

    // ---- Pager on the screen ----
    @Test fun fastFlingSequenceEndsAlignedToAMonth() {
        val a = main(hebrew = true)
        val cv = cal(a)
        val y0 = cv.computeVerticalScrollOffset()
        val h0 = header(a).text.toString()
        val root = a.window.decorView
        // Three quick flicks up, starting the next before the previous settle would have finished.
        repeat(3) { swipeV(root, cv, -cv.height.toFloat(), 40, settleAfter = false) }
        settle()
        assertTrue("grid must sit on one whole month", aligned(cv))
        assertEquals(0, cv.computeHorizontalScrollOffset())
        assertNotEquals(h0, header(a).text.toString())
        assertTrue(cv.computeVerticalScrollOffset() > y0)
    }

    @Test fun interruptedDragThenFlingStillAligns() {
        val a = main(hebrew = true)
        val cv = cal(a)
        val root = a.window.decorView
        // Drag partway, lift with a fast flick, then immediately start another flick.
        swipeV(root, cv, -cv.height * 0.4f, 80, settleAfter = false)
        swipeV(root, cv, -cv.height.toFloat(), 40, settleAfter = false)
        settle()
        assertTrue(aligned(cv))
        assertEquals(0, cv.computeHorizontalScrollOffset())
    }

    @Test fun removeAnimationsStillSettlesInstantlyAndAligned() {
        Motion.overrideEnabled = false
        val a = main(hebrew = true)
        val cv = cal(a)
        val h0 = header(a).text.toString()
        swipeV(a.window.decorView, cv, -cv.height.toFloat(), 40)
        assertTrue(aligned(cv))
        assertNotEquals(h0, header(a).text.toString())
        Motion.overrideEnabled = true
        val h1 = header(a).text.toString()
        press(a, KeyEvent.KEYCODE_4) // RTL: 4 = next month
        settle()
        assertTrue(aligned(cv))
        assertNotEquals(h1, header(a).text.toString())
        Motion.overrideEnabled = false
    }

    @Test fun keysFourAndSixAndDpadStillLandOnAWholeMonth() {
        val a = main(hebrew = true)
        val cv = cal(a)
        val y0 = cv.computeVerticalScrollOffset()
        press(a, KeyEvent.KEYCODE_4)
        assertTrue(aligned(cv))
        assertTrue(cv.computeVerticalScrollOffset() > y0)
        assertEquals(0, cv.computeHorizontalScrollOffset())
        val y1 = cv.computeVerticalScrollOffset()
        press(a, KeyEvent.KEYCODE_6)
        assertTrue(aligned(cv))
        assertEquals(y0, cv.computeVerticalScrollOffset())
        // A week-down that stays on the page must also stay aligned.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(aligned(cv))
        assertEquals(y0, cv.computeVerticalScrollOffset())
        // Crossing a month with D-pad also lands aligned (and further down).
        var n = 0
        val h0 = header(a).text.toString()
        while (header(a).text.toString() == h0 && n < 8) {
            press(a, KeyEvent.KEYCODE_DPAD_DOWN)
            n++
        }
        assertTrue(aligned(cv))
        assertTrue(cv.computeVerticalScrollOffset() >= y1 || cv.computeVerticalScrollOffset() > y0)
    }
}
