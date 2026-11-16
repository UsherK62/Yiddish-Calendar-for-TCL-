package org.yiddishcalendar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.LocalDate

/** 0.2.4 touch support: real taps (MotionEvents through the window), D-pad unchanged. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w240dp-h296dp-port-xhdpi")
class TouchTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun prefs() {
        ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("asked_perms", true).putBoolean("nightfall", false).commit()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun main(): MainActivity = Robolectric.buildActivity(MainActivity::class.java,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TEST_DATE, LocalDate.of(2027, 1, 21).toEpochDay())).setup().get()
    private fun started(a: Activity) = shadowOf(a).nextStartedActivity?.component?.className

    private fun views(v: View): List<View> = if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else listOf(v)
    private fun byText(root: View, t: String) = views(root).filterIsInstance<TextView>().first { it.text.toString() == t }

    /** A finger tap at the centre of [v], sent through [root] (the window's decor view). */
    private fun tap(root: View, v: View) {
        // scroll it into view first, like a finger would
        var p = v.parent
        while (p != null && p !is android.widget.ScrollView) p = p.parent
        (p as? android.widget.ScrollView)?.let { sv ->
            val r = android.graphics.Rect(); v.getDrawingRect(r); sv.offsetDescendantRectToMyCoords(v, r)
            sv.scrollTo(0, (r.top - sv.height / 3).coerceAtLeast(0)); idle()
        }
        val loc = IntArray(2); v.getLocationInWindow(loc)
        val x = loc[0] + v.width / 2f; val y = loc[1] + v.height / 2f
        val t = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        root.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0))
        ShadowSystemClock.advanceBy(Duration.ofMillis(60)); idle()
    }
    private fun swipe(root: View, v: View, dxPx: Float) {
        val loc = IntArray(2); v.getLocationInWindow(loc)
        val x = loc[0] + v.width / 2f; val y = loc[1] + v.height / 2f
        val t = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        for (i in 1..5) root.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 10L, MotionEvent.ACTION_MOVE, x + dxPx * i / 5, y, 0))
        root.dispatchTouchEvent(MotionEvent.obtain(t, t + 70, MotionEvent.ACTION_UP, x + dxPx, y, 0))
        ShadowSystemClock.advanceBy(Duration.ofMillis(80)); idle()
    }
    private fun dialog() = ShadowDialog.getLatestDialog() as KeyDialog

    @Test fun everySettingsRowWorksByTap() {
        val ctl = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        var a = ctl.get(); var root = a.window.decorView
        // toggle rows
        val parsha = Prefs(ctx).showParsha
        tap(root, byText(root, "Show parsha")); assertEquals(!parsha, Prefs(ctx).showParsha)
        val israel = Prefs(ctx).inIsrael
        tap(root, byText(root, "In Israel")); assertEquals(!israel, Prefs(ctx).inIsrael)
        // pick-list row -> pop-up -> tap an item
        tap(root, byText(root, SettingsActivity.LANGUAGE_LABEL))
        val d = dialog(); assertTrue(d.isShowing)
        tap(d.window!!.decorView, byText(d.window!!.decorView, "Yiddish · אידיש"))
        assertFalse(d.isShowing); assertEquals(2, Prefs(ctx).hebrewText)
        // arrow row (page)
        a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); root = a.window.decorView
        tap(root, byText(root, Ui.tr(2, "About")))
        assertEquals(AboutActivity::class.java.name, started(a))
    }

    @Test fun popUpBarLabelsAreTappable() {
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); val root = a.window.decorView
        tap(root, byText(root, SettingsActivity.LANGUAGE_LABEL))
        val d = dialog(); val dr = d.window!!.decorView
        tap(dr, dr.findViewById(R.id.skRight)); assertFalse("Back closes", d.isShowing)
        assertEquals(0, Prefs(ctx).hebrewText)
    }

    @Test fun softKeyLabelsRespondToTaps() {
        var a = main(); var root = a.window.decorView
        tap(root, a.findViewById(R.id.skRight)); assertEquals("Add", EditEventActivity::class.java.name, started(a))
        a = main(); root = a.window.decorView
        tap(root, a.findViewById(R.id.skCenter)); assertEquals("Open", DayActivity::class.java.name, started(a))
        a = main(); root = a.window.decorView
        tap(root, a.findViewById(R.id.skLeft))
        val d = dialog(); assertTrue("Menu", d.isShowing)
        tap(d.window!!.decorView, byText(d.window!!.decorView, "Add event"))
        assertEquals(EditEventActivity::class.java.name, started(a))
        // other screens: right label = Back
        val day = Robolectric.buildActivity(DayActivity::class.java,
            Intent(ctx, DayActivity::class.java).putExtra(DayActivity.EXTRA_DATE, LocalDate.of(2027, 1, 23).toEpochDay())).setup().get()
        tap(day.window.decorView, day.findViewById(R.id.skRight)); assertTrue(day.isFinishing)
        val about = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        tap(about.window.decorView, about.findViewById(R.id.skLeft)); assertEquals(KeyTestActivity::class.java.name, started(about))
    }

    @Test fun dayCellTapSelectsThenOpens() {
        val a = main(); val root = a.window.decorView
        val before = a.findViewById<TextView>(R.id.statusTitle).text.toString()
        val cells = views(root).filterIsInstance<DayCellView>().filter { it.isShown && it.mainColor == a.palette.text }
        val target = cells.first { !it.contentDescription.toString().contains(Luach(ctx, Prefs(ctx)).dayTitle(LocalDate.of(2027, 1, 21))) }
        tap(root, target)
        assertNull("first tap only selects", started(a))
        assertNotEquals(before, a.findViewById<TextView>(R.id.statusTitle).text.toString())
        tap(root, target)
        assertEquals("second tap opens", DayActivity::class.java.name, started(a))
        assertFalse(target.isFocusable)
    }

    private fun swipeV(root: View, v: View, dyPx: Float) {
        val loc = IntArray(2); v.getLocationInWindow(loc)
        val x = loc[0] + v.width / 2f; val y = loc[1] + v.height / 2f
        val t = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        for (i in 1..5) root.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 10L, MotionEvent.ACTION_MOVE, x, y + dyPx * i / 5, 0))
        root.dispatchTouchEvent(MotionEvent.obtain(t, t + 70, MotionEvent.ACTION_UP, x, y + dyPx, 0))
        ShadowSystemClock.advanceBy(Duration.ofMillis(80)); idle()
    }
    private fun header(a: Activity) = a.findViewById<TextView>(R.id.headerTitle).text.toString()
    private fun keyMonth(next: Boolean): String {
        // the month keys are unchanged: in the right-to-left grid 4 = next month, 6 = previous month
        val b = main(); val t = SystemClock.uptimeMillis()
        b.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, if (next) KeyEvent.KEYCODE_4 else KeyEvent.KEYCODE_6, 0)); idle()
        return header(b)
    }

    @Test fun swipeUpIsNextMonthAndDownIsPrevious() {
        val up = main(); val cal = up.findViewById<View>(R.id.calendar)
        val h0 = header(up)
        val fixed = listOf(R.id.header, R.id.weekdays, R.id.statusTitle, R.id.softBar).map { up.findViewById<View>(it) }
        val before = fixed.map { v -> IntArray(2).also { v.getLocationInWindow(it) }.toList() }
        swipeV(up.window.decorView, cal, -up.dp(80f).toFloat())
        assertNotEquals(h0, header(up))
        assertEquals("swipe up = next month", keyMonth(next = true), header(up))
        assertEquals("only the grid moves", before, fixed.map { v -> IntArray(2).also { v.getLocationInWindow(it) }.toList() })
        assertNull("a swipe never opens a day", started(up))
        val down = main()
        swipeV(down.window.decorView, down.findViewById(R.id.calendar), down.dp(80f).toFloat())
        assertEquals("swipe down = previous month", keyMonth(next = false), header(down))
    }

    @Test fun sidewaysSwipeDoesNothing() {
        for (dx in listOf(80f, -80f)) {
            val a = main(); val h0 = header(a)
            val status0 = a.findViewById<TextView>(R.id.statusTitle).text.toString()
            swipe(a.window.decorView, a.findViewById(R.id.calendar), a.dp(dx).toFloat())
            assertEquals(h0, header(a))
            assertEquals(status0, a.findViewById<TextView>(R.id.statusTitle).text.toString())
            assertNull(started(a))
        }
    }

    @Test fun editEventRowsOpenPopUpsByTap() {
        val a = Robolectric.buildActivity(EditEventActivity::class.java).setup().get(); val root = a.window.decorView
        val time = byText(root, Ui.tr(0, "All day"))
        tap(root, time)
        val d = dialog(); assertTrue(d.isShowing)
        val dr = d.window!!.decorView
        tap(dr, byText(dr, "9 AM"))          // hour pop-up -> minutes pop-up
        val m = dialog(); assertNotSame(d, m)
        tap(m.window!!.decorView, byText(m.window!!.decorView, "9:30 AM"))
        assertEquals("9:30 AM", time.text.toString())
    }
}
