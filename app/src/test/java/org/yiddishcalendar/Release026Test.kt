package org.yiddishcalendar

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.kizitonwose.calendar.view.CalendarView
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

/**
 * 0.2.6: month pages scroll vertically; turning Hebrew months on/off also turns right-to-left
 * on/off, and the Right-to-left setting still works as a manual override afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w240dp-h296dp-port-xhdpi")
class Release026Test {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun prefs() {
        ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("asked_perms", true).putBoolean("nightfall", false).commit()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun press(dispatch: (KeyEvent) -> Boolean, code: Int) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(40))
        val t = SystemClock.uptimeMillis()
        dispatch(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        dispatch(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        idle()
    }
    private fun main(): MainActivity = Robolectric.buildActivity(
        MainActivity::class.java,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TEST_DATE, LocalDate.of(2027, 1, 21).toEpochDay()),
    ).setup().get().also { idle() }

    private fun settleScroll() {
        repeat(25) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(40))
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun views(v: View): List<View> =
        if (v is android.view.ViewGroup) listOf(v) + (0 until v.childCount).flatMap { views(v.getChildAt(it)) }
        else listOf(v)

    private fun texts(v: View) = views(v).filterIsInstance<TextView>().filter { it.isShown }.map { it.text.toString() }

    private fun rtlRow(a: SettingsActivity) =
        views(a.window.decorView).first {
            it.isFocusable && it is android.view.ViewGroup && texts(it).contains(Ui.tr(Prefs(ctx).hebrewText, "Right-to-left"))
        } as android.view.ViewGroup

    private fun monthsRow(a: SettingsActivity) =
        views(a.window.decorView).first {
            it.isFocusable && it is android.view.ViewGroup && texts(it).contains(Ui.tr(Prefs(ctx).hebrewText, "Hebrew months"))
        } as android.view.ViewGroup

    // ---- version ----
    @Test fun versionIs026OrLater() {
        assertTrue(BuildConfig.VERSION_CODE >= 11)
        assertTrue(BuildConfig.VERSION_NAME >= "0.2.6")
    }

    // ---- 1. RTL follows Hebrew months, then the RTL row is a manual override ----
    @Test fun applyHebrewMonthsTurnsRtlOnAndOff() {
        val p = Prefs(ctx)
        assertTrue(p.hebrewMonths)
        assertTrue(p.rtlGrid)
        p.applyHebrewMonths(false)
        assertFalse(p.hebrewMonths)
        assertFalse("Gregorian months turn right-to-left off", p.rtlGrid)
        p.applyHebrewMonths(true)
        assertTrue(p.hebrewMonths)
        assertTrue("Hebrew months turn right-to-left on", p.rtlGrid)
    }

    @Test fun rtlCanBeOverriddenAfterMonthsChange() {
        val p = Prefs(ctx)
        p.applyHebrewMonths(false)
        assertFalse(p.rtlGrid)
        p.rtlGrid = true
        assertFalse("override does not change the months setting", p.hebrewMonths)
        assertTrue("Right-to-left can be turned back on by itself", p.rtlGrid)
        p.applyHebrewMonths(true)
        assertTrue(p.hebrewMonths)
        assertTrue("switching months again follows the new months choice", p.rtlGrid)
        p.rtlGrid = false
        assertTrue(p.hebrewMonths)
        assertFalse(p.rtlGrid)
        p.applyHebrewMonths(false)
        assertFalse(p.hebrewMonths)
        assertFalse(p.rtlGrid)
    }

    @Test fun settingsHebrewMonthsToggleAlsoFlipsRtlAndOverrideStillWorks() {
        assertTrue(Prefs(ctx).rtlGrid)
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        monthsRow(s).requestFocus()
        press(s::dispatchKeyEvent, KeyEvent.KEYCODE_DPAD_CENTER)
        assertFalse(Prefs(ctx).hebrewMonths)
        assertFalse("Settings toggle turns right-to-left off too", Prefs(ctx).rtlGrid)

        val s2 = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        assertEquals("OFF", rtlRow(s2).getChildAt(1).contentDescription)
        assertEquals(View.LAYOUT_DIRECTION_LTR, s2.window.decorView.layoutDirection)
        rtlRow(s2).requestFocus()
        press(s2::dispatchKeyEvent, KeyEvent.KEYCODE_DPAD_CENTER)
        assertFalse("months stay Gregorian", Prefs(ctx).hebrewMonths)
        assertTrue("Right-to-left is a manual override afterwards", Prefs(ctx).rtlGrid)

        val s3 = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        assertEquals("ON", rtlRow(s3).getChildAt(1).contentDescription)
        assertEquals("OFF", monthsRow(s3).getChildAt(1).contentDescription)
        assertEquals(View.LAYOUT_DIRECTION_RTL, s3.window.decorView.layoutDirection)
    }

    @Test fun poundKeyAlsoFlipsRtlAndOverrideStillWorks() {
        val a = main()
        assertEquals(View.LAYOUT_DIRECTION_RTL, a.window.decorView.layoutDirection)
        press(a::dispatchKeyEvent, KeyEvent.KEYCODE_POUND)
        assertFalse(Prefs(ctx).hebrewMonths)
        assertFalse(Prefs(ctx).rtlGrid)
        assertEquals("grid follows at once", View.LAYOUT_DIRECTION_LTR, a.window.decorView.layoutDirection)
        assertEquals(View.LAYOUT_DIRECTION_LTR, a.findViewById<View>(R.id.calendar).layoutDirection)
        assertEquals(View.LAYOUT_DIRECTION_LTR, a.findViewById<View>(R.id.weekdays).layoutDirection)

        press(a::dispatchKeyEvent, KeyEvent.KEYCODE_POUND)
        assertTrue(Prefs(ctx).hebrewMonths)
        assertTrue(Prefs(ctx).rtlGrid)
        assertEquals(View.LAYOUT_DIRECTION_RTL, a.window.decorView.layoutDirection)

        // Manual override: flip only Right-to-left in Settings, then come back
        val paused = Robolectric.buildActivity(MainActivity::class.java,
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TEST_DATE, LocalDate.of(2027, 1, 21).toEpochDay())).setup()
        paused.pause()
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        assertTrue(Prefs(ctx).hebrewMonths)
        rtlRow(s).performClick(); idle()
        assertTrue(Prefs(ctx).hebrewMonths)
        assertFalse(Prefs(ctx).rtlGrid)
        paused.resume(); idle()
        assertTrue("months stay Hebrew after a RTL-only change", Prefs(ctx).hebrewMonths)
        assertFalse(Prefs(ctx).rtlGrid)
        assertEquals(View.LAYOUT_DIRECTION_LTR, paused.get().window.decorView.layoutDirection)
    }

    // ---- 2. Vertical month paging ----
    @Test fun monthGridIsVerticalAndPaged() {
        val a = main()
        val cv = a.findViewById<CalendarView>(R.id.calendar)
        assertEquals(RecyclerView.VERTICAL, cv.orientation)
        assertFalse("0.2.7 snaps pages itself, so the library pager is off", cv.scrollPaged)
        assertFalse("no sideways paging", cv.canScrollHorizontally(1))
        assertFalse(cv.canScrollHorizontally(-1))
        assertTrue("can page to a later month", cv.canScrollVertically(1))
        assertTrue("can page to an earlier month", cv.canScrollVertically(-1))
    }

    @Test fun monthChangeScrollsVerticallyNotHorizontally() {
        for (rtl in listOf(true, false)) {
            ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("rtl_grid", rtl).putBoolean("hebrew_months", true).commit()
            val a = main()
            val cv = a.findViewById<CalendarView>(R.id.calendar)
            assertEquals(RecyclerView.VERTICAL, cv.orientation)
            val y0 = cv.computeVerticalScrollOffset()
            val header0 = a.findViewById<TextView>(R.id.headerTitle).text.toString()
            // In both directions, key 3 jumps a year (instant). Key 4/6 is one month (animated).
            // Use 4 in RTL and 6 in LTR so we always go to the next month, matching swipe-up.
            press(a::dispatchKeyEvent, if (rtl) KeyEvent.KEYCODE_4 else KeyEvent.KEYCODE_6)
            settleScroll()
            assertNotEquals("rtl=$rtl header changes", header0, a.findViewById<TextView>(R.id.headerTitle).text.toString())
            assertEquals("rtl=$rtl no horizontal scroll", 0, cv.computeHorizontalScrollOffset())
            val y1 = cv.computeVerticalScrollOffset()
            assertTrue("rtl=$rtl next month is further down (y0=$y0 y1=$y1)", y1 > y0)
            press(a::dispatchKeyEvent, if (rtl) KeyEvent.KEYCODE_6 else KeyEvent.KEYCODE_4)
            settleScroll()
            assertEquals("rtl=$rtl previous month comes back up", y0, cv.computeVerticalScrollOffset())
            assertEquals(0, cv.computeHorizontalScrollOffset())
        }
    }

    @Test fun dpadWeekCrossingMonthAlsoScrollsVertically() {
        // 21 Jan 2027 is a Thursday; go down a few weeks until the month changes.
        val a = main()
        val cv = a.findViewById<CalendarView>(R.id.calendar)
        val y0 = cv.computeVerticalScrollOffset()
        val header0 = a.findViewById<TextView>(R.id.headerTitle).text.toString()
        var n = 0
        while (a.findViewById<TextView>(R.id.headerTitle).text.toString() == header0 && n < 8) {
            press(a::dispatchKeyEvent, KeyEvent.KEYCODE_DPAD_DOWN)
            n++
        }
        settleScroll()
        assertNotEquals("crossed into the next month", header0, a.findViewById<TextView>(R.id.headerTitle).text.toString())
        assertEquals(0, cv.computeHorizontalScrollOffset())
        assertTrue(cv.computeVerticalScrollOffset() > y0)
    }
}
