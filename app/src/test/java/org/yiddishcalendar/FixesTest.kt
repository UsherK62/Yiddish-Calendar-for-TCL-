package org.yiddishcalendar

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Host (Robolectric) tests for the 0.2.2 fixes. They do not replace testing on the TCL Flip 2. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w240dp-h296dp-port-xhdpi")
class FixesTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val jan21 = LocalDate.of(2027, 1, 21)

    @Before fun prefs() {
        ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("asked_perms", true).putBoolean("nightfall", false).commit()
    }

    private fun luach() = Luach(ctx, Prefs(ctx))
    private fun main(date: LocalDate = jan21): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TEST_DATE, date.toEpochDay())).setup().get()
    private fun key(a: MainActivity, code: Int) {
        val t = android.os.SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0)); a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun text(a: android.app.Activity, id: Int) = a.findViewById<TextView>(id).text.toString()

    // ---- 1. holiday labels always visible ----
    @Test fun shortHolidayLabelsFitAndCoverTheYear() {
        val l = luach()
        assertEquals("ט״ו בש׳", l.cellLabel(LocalDate.of(2027, 1, 23)))
        assertEquals("פסח", l.cellLabel(LocalDate.of(2027, 4, 22)))
        assertEquals("חוה״מ", l.cellLabel(LocalDate.of(2027, 4, 25)))
        assertEquals("סוכות", l.cellLabel(LocalDate.of(2026, 9, 26)))
        assertEquals("ר״ה", l.cellLabel(LocalDate.of(2026, 9, 12)))
        assertEquals("יו״כ", l.cellLabel(LocalDate.of(2026, 9, 21)))
        assertEquals("ר״ח", l.cellLabel(LocalDate.of(2027, 2, 7)))
        assertEquals("חנוכה", l.cellLabel(LocalDate.of(2026, 12, 6)))
        assertNull(l.cellLabel(jan21))
        // Every label in a whole year is short enough for a ~25dp cell (<= 6 letters).
        var d = LocalDate.of(2026, 9, 1)
        while (d < LocalDate.of(2027, 9, 1)) {
            l.cellLabel(d)?.let { assertTrue("$d $it", it.count { c -> c in '\u05D0'..'\u05EA' } <= 6) }
            d = d.plusDays(1)
        }
    }

    // ---- 3. Gregorian mode really switches the cells and header ----
    @Test fun cellTextFollowsMode() {
        val l = luach()
        assertEquals(CellText("יג", "21", null), CellText.of(l, jan21, true))
        assertEquals(CellText("21", "יג", null), CellText.of(l, jan21, false))
    }

    @Test fun headerPrimaryMonthIsBigAndSidesStay() {
        val l = luach()
        val h = HeaderText.of(l, LocalDate.of(2027, 1, 9), 30, hebrewPages = true, rtl = true)
        assertEquals("שבט תשפ״ז", h.start); assertTrue(h.startIsMain)
        val g = HeaderText.of(l, LocalDate.of(2027, 1, 1), 31, hebrewPages = false, rtl = true)
        assertEquals("January 2027", g.end); assertFalse(g.startIsMain) // Gregorian (left) is the main one
        assertTrue(g.start.contains("שבט"))
    }

    @Test fun gregorianMonthPagesUseCalendarMonths() {
        val p = MonthPages(false, jan21)
        val off = p.offsetOf(jan21)
        assertEquals(LocalDate.of(2027, 1, 1) to 31, p.range(off))
        assertEquals(LocalDate.of(2027, 2, 1) to 28, p.range(off + 1))
        assertEquals(LocalDate.of(2027, 2, 21), p.addMonths(jan21, 1))
        val h = MonthPages(true, jan21)
        assertEquals(LocalDate.of(2027, 1, 9), h.range(h.offsetOf(jan21)).first) // 1 Shevat 5787
    }

    @Test fun poundKeySwitchesToGregorianOnTheSameDate() {
        val a = main()
        assertEquals("שבט תשפ״ז", text(a, R.id.headerTitle))
        key(a, KeyEvent.KEYCODE_POUND)
        // 0.2.6: # also turns right-to-left off, so the Gregorian month is the start/main title
        assertEquals("January 2027", text(a, R.id.headerTitle))
        assertTrue(a.findViewById<TextView>(R.id.headerTitle).textSize > a.findViewById<TextView>(R.id.headerRight).textSize)
        assertTrue(text(a, R.id.statusSub).contains("Thu Jan 21")) // same selected day
        key(a, KeyEvent.KEYCODE_POUND)
        assertEquals("שבט תשפ״ז", text(a, R.id.headerTitle))
        assertTrue(text(a, R.id.statusSub).contains("Thu Jan 21"))
    }

    // ---- 4. soft-key labels pinned over the keys ----
    @Test fun softKeyLabelsSitOverTheirKeys() {
        val a = main()
        val left = a.findViewById<TextView>(R.id.skLeft)
        val right = a.findViewById<TextView>(R.id.skRight)
        val center = a.findViewById<TextView>(R.id.skCenter)
        for (t in listOf(left, right, center)) assertEquals(View.TEXT_ALIGNMENT_GRAVITY, t.textAlignment)
        assertEquals(Gravity.LEFT, left.gravity and Gravity.HORIZONTAL_GRAVITY_MASK)
        assertEquals(Gravity.RIGHT, right.gravity and Gravity.HORIZONTAL_GRAVITY_MASK)
        assertEquals(Gravity.CENTER_HORIZONTAL, center.gravity and Gravity.HORIZONTAL_GRAVITY_MASK)
        assertEquals(View.LAYOUT_DIRECTION_LTR, a.findViewById<View>(R.id.softBar).layoutDirection)
        // The right label's text is drawn at the far right edge of the bar.
        val layout = right.layout
        assertTrue(layout.getLineRight(0) >= right.width - right.paddingRight - 2)
    }

    // ---- RTL text everywhere ----
    @Test fun mixedLineIsIsolatedRightToLeft() {
        val l = luach()
        val t = l.dayTitle(LocalDate.of(2027, 1, 23))
        assertTrue(t.startsWith("שבת פרשת בשלח \u200F·\u200F "))   // first strong char is Hebrew -> RTL line
        assertTrue(t.contains("\u2068Tu B'Shvat\u2069"))
        assertTrue(l.join(listOf("Succos", "ר״ח")).startsWith("Succos \u200E·\u200E "))  // English first -> LTR line
        assertEquals(listOf("שבת פרשת בשלח", "Tu B'Shvat", "שירה"), l.dayTitleParts(LocalDate.of(2027, 1, 23)))
    }

    @Test fun eachLineAlignsByItsOwnLanguage() {
        val a = main()
        val title = a.findViewById<TextView>(R.id.statusTitle) // "Thursday"
        val sub = a.findViewById<TextView>(R.id.statusSub)     // "יג שבט תשפ״ז · Thu Jan 21"
        for (t in listOf(title, sub)) {
            assertEquals(View.TEXT_DIRECTION_FIRST_STRONG, t.textDirection)
            assertEquals(View.TEXT_ALIGNMENT_TEXT_START, t.textAlignment)
        }
        // English line starts at the left edge, Hebrew-first line ends at the right edge.
        assertTrue(title.layout.getLineLeft(0) <= 1f)
        assertTrue(sub.layout.getLineRight(0) >= sub.width - sub.paddingLeft - sub.paddingRight - 1f)
        assertTrue(sub.layout.getParagraphDirection(0) == android.text.Layout.DIR_RIGHT_TO_LEFT)
        assertTrue(title.layout.getParagraphDirection(0) == android.text.Layout.DIR_LEFT_TO_RIGHT)
    }

    @Test fun settingsSwitchComesAfterTheWords() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        val rows = a.findViewById<android.view.ViewGroup>(R.id.rows)
        var toggles = 0
        for (i in 0 until rows.childCount) {
            val card = rows.getChildAt(i) as? android.widget.LinearLayout ?: continue
            for (j in 0 until card.childCount) {
                val row = card.getChildAt(j) as? android.widget.LinearLayout ?: continue
                if (row.childCount < 2) continue
                if (row.getChildAt(1).visibility != View.VISIBLE) continue
                toggles++
                assertTrue("switch must be right of the label", row.getChildAt(1).left >= row.getChildAt(0).right)
            }
        }
        assertEquals(6, toggles) // Dark theme, Right-to-left, Hebrew months, In Israel, Date at nightfall, Show parsha
    }
}
