package org.yiddishcalendar

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.LocalDate

/**
 * 0.2.8: holding a D-pad key is smooth (no stacked glides or mid-snap months);
 * holiday labels never overlap the day numbers; screens do not explain keys.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = "w240dp-h296dp-port-xhdpi")
class Release028Test {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun prefs() {
        Motion.overrideEnabled = null
        ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("asked_perms", true).putBoolean("nightfall", false)
            .putBoolean("rtl_grid", true).putBoolean("hebrew_months", true)
            .commit()
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
    private fun main(date: LocalDate = LocalDate.of(2027, 4, 22), hebrew: Boolean? = true, rtl: Boolean? = true): MainActivity {
        val p = Prefs(ctx)
        if (hebrew != null) p.applyHebrewMonths(hebrew)
        if (rtl != null) p.rtlGrid = rtl
        return Robolectric.buildActivity(
            MainActivity::class.java,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TEST_DATE, date.toEpochDay()),
        ).setup().get().also { idle(); settle() }
    }
    private fun cal(a: MainActivity) = a.findViewById<CalendarView>(R.id.calendar)
    private fun aligned(cv: CalendarView): Boolean {
        val lm = cv.layoutManager as LinearLayoutManager
        val first = lm.findFirstVisibleItemPosition()
        if (first == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return false
        val child = lm.findViewByPosition(first) ?: return false
        return MonthSnap.isAligned(child.top)
    }
    private fun down(a: MainActivity, code: Int, repeat: Int) {
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, repeat))
        idle()
    }
    private fun up(a: MainActivity, code: Int) {
        val t = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        idle(); settle()
    }
    private fun walk(v: View): List<View> = buildList {
        add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) addAll(walk(v.getChildAt(i)))
    }
    private fun cells(a: MainActivity) = walk(a.findViewById(android.R.id.content)).filterIsInstance<DayCellView>()
    private fun selectedRings(a: MainActivity) = cells(a).count { it.selectedRing && it.isShown }
    private fun floatingRingShown(a: MainActivity) =
        walk(a.findViewById(android.R.id.content)).filterIsInstance<SelectionRingView>().any { it.visibility == View.VISIBLE && it.isShown }

    private fun assertCellsClean(a: MainActivity, where: String) {
        val seen = cells(a).filter { it.width > 0 && it.height > 0 && it.isShown }
        assertTrue("$where: expected laid-out day cells", seen.isNotEmpty())
        for (c in seen) {
            val r = c.layoutNow()
            assertFalse(
                "$where: overlap main=${r.mainText} small=${r.smallText} label=${r.labelText} " +
                    "main=${r.main} small=${r.small} label=${r.label} ${c.width}x${c.height}",
                r.overlaps(),
            )
            assertTrue(
                "$where: out of inset main=${r.mainText} label=${r.labelText} ${r.main} ${r.label} inset=${r.inset}",
                r.inside(r.inset, r.inset, c.width - r.inset, c.height - r.inset),
            )
            r.labelText?.let { assertFalse("$where mid-word ellipsis $it", it.contains("…") || it.contains("...")) }
        }
    }

    @Test fun versionIs028() {
        assertEquals(13, BuildConfig.VERSION_CODE)
        assertEquals("0.2.8", BuildConfig.VERSION_NAME)
    }

    @Test fun holdingLeftMovesWithoutStackedGlides() {
        Motion.overrideEnabled = true
        val start = LocalDate.of(2027, 4, 10)
        val a = main(start, hebrew = false)
        down(a, KeyEvent.KEYCODE_DPAD_LEFT, 0)
        repeat(8) { down(a, KeyEvent.KEYCODE_DPAD_LEFT, it + 1) }
        assertEquals("RTL: left is the next day", start.plusDays(9), a.selectedDate)
        assertFalse("glide ring must not stay up during a hold", floatingRingShown(a))
        assertEquals("only the current day has a cell ring during a hold", 1, selectedRings(a))
        assertTrue(aligned(cal(a)))
        up(a, KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(start.plusDays(9), a.selectedDate)
        assertEquals(1, selectedRings(a))
        assertFalse(floatingRingShown(a))
        assertTrue(aligned(cal(a)))
    }

    @Test fun holdingRightAcrossAMonthJumpsCleanly() {
        Motion.overrideEnabled = true
        val start = LocalDate.of(2027, 4, 1)
        val a = main(start, hebrew = false)
        down(a, KeyEvent.KEYCODE_DPAD_RIGHT, 0)
        repeat(20) { down(a, KeyEvent.KEYCODE_DPAD_RIGHT, it + 1) }
        up(a, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(start.minusDays(21), a.selectedDate)
        assertTrue("page must be on exactly one month after a hold that crossed a month", aligned(cal(a)))
        assertEquals(1, selectedRings(a))
        assertFalse(floatingRingShown(a))
    }

    @Test fun holdingUpAndDownStayAligned() {
        Motion.overrideEnabled = true
        val start = LocalDate.of(2027, 4, 22)
        val a = main(start, hebrew = false)
        down(a, KeyEvent.KEYCODE_DPAD_DOWN, 0)
        repeat(5) { down(a, KeyEvent.KEYCODE_DPAD_DOWN, it + 1) }
        up(a, KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(start.plusDays(42), a.selectedDate)
        assertTrue(aligned(cal(a)))
        down(a, KeyEvent.KEYCODE_DPAD_UP, 0)
        repeat(5) { down(a, KeyEvent.KEYCODE_DPAD_UP, it + 1) }
        up(a, KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(start, a.selectedDate)
        assertTrue(aligned(cal(a)))
        assertEquals(1, selectedRings(a))
    }

    @Test fun holdingFourAndSixChangesMonthInstantly() {
        Motion.overrideEnabled = true
        val start = LocalDate.of(2027, 4, 15)
        val a = main(start, hebrew = false)
        down(a, KeyEvent.KEYCODE_4, 0)
        repeat(3) { down(a, KeyEvent.KEYCODE_4, it + 1) }
        up(a, KeyEvent.KEYCODE_4)
        assertEquals(start.plusMonths(4), a.selectedDate)
        assertTrue(aligned(cal(a)))
        down(a, KeyEvent.KEYCODE_6, 0)
        repeat(3) { down(a, KeyEvent.KEYCODE_6, it + 1) }
        up(a, KeyEvent.KEYCODE_6)
        assertEquals(start, a.selectedDate)
        assertTrue(aligned(cal(a)))
    }

    @Test fun keysTwoAndEightAreNotMapped() {
        val start = LocalDate.of(2027, 4, 22)
        val a = main(start)
        down(a, KeyEvent.KEYCODE_2, 0); up(a, KeyEvent.KEYCODE_2)
        down(a, KeyEvent.KEYCODE_8, 0); up(a, KeyEvent.KEYCODE_8)
        repeat(3) { down(a, KeyEvent.KEYCODE_2, it + 1) }
        up(a, KeyEvent.KEYCODE_2)
        assertEquals(start, a.selectedDate)
    }

    @Test fun singlePressStillLandsOnTheNextDay() {
        Motion.overrideEnabled = true
        val start = LocalDate.of(2027, 4, 15)
        val a = main(start, hebrew = false)
        down(a, KeyEvent.KEYCODE_DPAD_LEFT, 0)
        up(a, KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(start.plusDays(1), a.selectedDate)
        assertTrue(aligned(cal(a)))
        assertEquals(1, selectedRings(a))
        assertFalse(floatingRingShown(a))
    }

    @Test fun nissan2027LabelsDoNotOverlapInEitherMonthMode() {
        for (hebrew in listOf(true, false)) {
            for (rtl in listOf(true, false)) {
                val a = main(LocalDate.of(2027, 4, 22), hebrew = hebrew, rtl = rtl)
                assertCellsClean(a, "Nissan/April 2027 hebrew=$hebrew rtl=$rtl")
                val labels = cells(a).mapNotNull { it.layoutNow().labelText }
                assertTrue("hebrew=$hebrew rtl=$rtl should show Pesach-week labels, got $labels",
                    labels.any { it.contains("פסח") || it.contains("ע״פ") || it.contains("חו") || it.contains("ח״מ") })
            }
        }
    }

    @Test fun worstCaseLabelAndNumeralBoundsNeverOverlap() {
        val density = ctx.resources.displayMetrics.density
        // Tight 6-row Flip 2 tile (~31×25.5 dp) and a slightly taller 5-row tile.
        val sizes = listOf(
            (31 * density).toInt() to (25 * density).toInt(),
            (34 * density).toInt() to (32 * density).toInt(),
        )
        val labels = listOf(
            "חוה״מ", "ערב פסח", "ע״פ", "פסח", "שבועות", "חנוכה", "סוכות",
            "ט״ו באב", "ט׳ באב", "ט״ו בש׳", "תענית", "פורים", "שושן",
            "עיו״כ", "שמח״ת", "ר״ח", "ר״ה",
        )
        val mains = listOf("כז", "כ״ז", "ט״ו", "י״ד", "טו", "יד", "יט", "כ", "27", "15")
        val smalls = listOf("27", "15", "כז", "כ״ז", "ט״ו", "")
        val v = DayCellView(ctx)
        v.mainColor = 0xFF000000.toInt()
        v.smallColor = 0xFF000000.toInt()
        v.labelColor = 0xFF000000.toInt()
        for ((w, h) in sizes) {
            v.layout(0, 0, w, h)
            for (compact in listOf(true, false)) {
                for (rtl in listOf(true, false)) {
                    v.compact = compact
                    v.rtl = rtl
                    v.selectedRing = true
                    for (main in mains) for (small in smalls) for (label in labels) {
                        v.main = main; v.small = small; v.label = label
                        val r = v.layoutNow()
                        assertFalse(
                            "overlap $main/$small/$label compact=$compact rtl=$rtl ${w}x$h → ${r.labelText} ${r.main} ${r.label}",
                            r.overlaps(),
                        )
                        assertTrue(
                            "out of box $main/$label compact=$compact rtl=$rtl ${w}x$h ${r.main} ${r.label}",
                            r.inside(r.inset, r.inset, w - r.inset, h - r.inset),
                        )
                        r.labelText?.let {
                            assertFalse("ellipsis $it", it.contains("…") || it.contains("..."))
                            assertFalse("must not keep a mid-word stump of the original", it.endsWith("…") || it.endsWith("..."))
                        }
                    }
                }
            }
        }
    }

    @Test fun longLabelsAbbreviateInsteadOfEllipsizing() {
        val paint = Paint()
        paint.textSize = 16f
        // Narrower than "חוה״מ" at the minimum size, so it must shorten.
        val min = 5.5f * ctx.resources.displayMetrics.scaledDensity
        val start = 8f * ctx.resources.displayMetrics.scaledDensity
        paint.textSize = min
        val tooNarrow = paint.measureText("חה״מ") - 1f
        val (text, _) = DayCellLayout.fitLabel("חוה״מ", paint, tooNarrow.coerceAtLeast(1f), start, min)
        assertTrue("got $text", text == "ח״מ" || text == "חה״מ")
        assertFalse(text.contains("…") || text.contains("..."))
        val (shavuos, _) = DayCellLayout.fitLabel("שבועות", paint, paint.apply { textSize = min }.measureText("שב") , start, min)
        assertTrue("got $shavuos", shavuos == "שב׳" || shavuos == "שבוע׳" || shavuos == "שבועות")
        assertFalse(shavuos.contains("…"))
    }

    @Test fun screensDoNotExplainKeys() {
        val event = Robolectric.buildActivity(EditEventActivity::class.java).setup().get()
        val eventHint = event.findViewById<TextView>(R.id.hint)
        assertEquals(View.GONE, eventHint.visibility)
        val eventTexts = walk(event.window.decorView).mapNotNull { (it as? TextView)?.text?.toString() }
        assertFalse(eventTexts.toString(), eventTexts.any { it.contains("OK to choose") || it.contains("Type with the keypad") })

        val day = Robolectric.buildActivity(
            DayActivity::class.java,
            Intent(ctx, DayActivity::class.java).putExtra(DayActivity.EXTRA_DATE, LocalDate.of(2027, 4, 22).toEpochDay()),
        ).setup().get()
        val dayTexts = walk(day.window.decorView).mapNotNull { (it as? TextView)?.text?.toString() }
        assertTrue(dayTexts.toString(), dayTexts.any { it == "No events." || it.contains("No events.") })
        assertFalse(dayTexts.toString(), dayTexts.any { it.contains("Press Menu, then Add event") })

        val about = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        val aboutTexts = walk(about.window.decorView).mapNotNull { (it as? TextView)?.text?.toString() }.joinToString("\n")
        assertFalse(aboutTexts, aboutTexts.contains("press the left soft key"))
        assertFalse(aboutTexts, aboutTexts.contains("Right soft key, or Menu"))
        assertFalse(aboutTexts, aboutTexts.contains("press 0 for English"))
    }
}
