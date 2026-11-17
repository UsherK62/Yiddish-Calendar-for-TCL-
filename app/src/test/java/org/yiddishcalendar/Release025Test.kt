package org.yiddishcalendar

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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.LocalDate

/** 0.2.5: Hebrew/Gregorian toggle in Settings, options menu anchored bottom-left above the Menu key. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = "w240dp-h296dp-port-xhdpi")
class Release025Test {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun prefs() {
        ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("asked_perms", true).putBoolean("nightfall", false).commit()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun views(v: View): List<View> = if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else listOf(v)
    private fun texts(v: View) = views(v).filterIsInstance<TextView>().filter { it.isShown }.map { it.text.toString() }
    private fun press(dispatch: (KeyEvent) -> Boolean, code: Int) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(40))
        val t = SystemClock.uptimeMillis()
        dispatch(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0)); dispatch(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0)); idle()
    }
    private fun tap(root: View, v: View) {
        val loc = IntArray(2); v.getLocationInWindow(loc)
        val x = loc[0] + v.width / 2f; val y = loc[1] + v.height / 2f; val t = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        root.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0))
        ShadowSystemClock.advanceBy(Duration.ofMillis(60)); idle()
    }
    private fun mainCtl() = Robolectric.buildActivity(MainActivity::class.java,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TEST_DATE, LocalDate.of(2027, 1, 21).toEpochDay())).setup()
    private fun monthsRow(a: SettingsActivity) =
        views(a.window.decorView).first { it.isFocusable && it is ViewGroup && texts(it).contains(Ui.tr(Prefs(ctx).hebrewText, "Hebrew months")) } as ViewGroup

    // ---- 1. Settings toggle = # key ----
    @Test fun hebrewMonthsToggleWorksByOkAndTapAndMatchesPref() {
        assertTrue(Prefs(ctx).hebrewMonths)
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        val row = monthsRow(s)
        assertTrue("note under the label", texts(row).contains("Off: Gregorian months"))
        assertTrue("switch after the words (right)", row.getChildAt(1).isShown && row.getChildAt(1).left >= row.getChildAt(0).right)
        assertTrue("no arrows", texts(s.window.decorView).none { t -> t.any { it in "<>‹›" } })
        row.requestFocus(); press(s::dispatchKeyEvent, KeyEvent.KEYCODE_DPAD_CENTER)
        assertFalse("OK -> Gregorian", Prefs(ctx).hebrewMonths)
        // 0.2.6: flipping months also flips RTL, so Settings rebuilds; tap the new instance.
        val s2 = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        tap(s2.window.decorView, monthsRow(s2))
        assertTrue("tap -> Hebrew again", Prefs(ctx).hebrewMonths)
    }

    @Test fun toggleAndPoundKeyStayInSync() {
        val ctl = mainCtl(); val m = ctl.get()
        val hebHeader = m.findViewById<TextView>(R.id.headerTitle).text.toString()
        // Settings toggle -> back on the month screen it is Gregorian
        ctl.pause()
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        monthsRow(s).performClick(); idle()
        assertFalse(Prefs(ctx).hebrewMonths)
        ctl.resume(); idle()
        val gregHeader = m.findViewById<TextView>(R.id.headerTitle).text.toString()
        assertNotEquals(hebHeader, gregHeader)
        // # key on the month screen -> Settings shows the switch ON again
        press(m::dispatchKeyEvent, KeyEvent.KEYCODE_POUND)
        assertTrue(Prefs(ctx).hebrewMonths)
        assertEquals(hebHeader, m.findViewById<TextView>(R.id.headerTitle).text.toString())
        val s2 = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        assertEquals("ON", monthsRow(s2).getChildAt(1).contentDescription)
    }

    @Test fun settingsHasNoNewKeypadShortcuts() {
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        for (k in listOf(KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_6)) {
            press(s::dispatchKeyEvent, k)
            assertTrue(KeyEvent.keyCodeToString(k), Prefs(ctx).hebrewMonths)
        }
    }

    @Test fun yiddishLabelInYiddishMode() {
        Prefs(ctx).hebrewText = 2
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        val t = texts(s.window.decorView)
        assertTrue(t.toString(), t.contains("אידישע חדשים") && t.contains("אויס: ענגלישע חדשים"))
    }

    // ---- 3. options menu bottom-left above the Menu key ----
    private fun checkAnchored(rtl: Boolean) {
        ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit().putBoolean("rtl_grid", rtl).commit()
        val m = mainCtl().get()
        press(m::dispatchKeyEvent, KeyEvent.KEYCODE_SOFT_LEFT)
        val d = ShadowDialog.getLatestDialog() as KeyDialog; idle(); idle()
        val root = d.window!!.decorView
        val card = root.findViewById<ViewGroup>(R.id.card)
        val bar = root.findViewById<View>(R.id.softBar)
        val dp = ctx.resources.displayMetrics.density
        val c = IntArray(2).also { card.getLocationInWindow(it) }; val b = IntArray(2).also { bar.getLocationInWindow(it) }
        assertTrue("rtl=$rtl left edge ${c[0]}", c[0] <= (4 * dp).toInt())
        val gap = b[1] - (c[1] + card.height)
        assertTrue("rtl=$rtl right above the bar (gap $gap)", gap in 0..(4 * dp).toInt())
        assertTrue("narrower than the screen", card.width < root.width / 2 + (60 * dp).toInt())
        val body = root.findViewById<View>(R.id.dlgBody); val scroll = root.findViewById<View>(R.id.dlgScroll)
        assertEquals("no extra space", body.height, scroll.height)
        assertEquals(card.paddingTop + scroll.height + card.paddingBottom, card.height)
        // the bar shows Select in the middle and Back on the right
        assertEquals("Select", root.findViewById<TextView>(R.id.skCenter).text.toString())
        assertEquals("Back", root.findViewById<TextView>(R.id.skRight).text.toString())
        assertEquals(listOf("Today", "Go to date", "Add event", "Settings", "Help"),
            texts(body).filter { it.length > 1 })
        d.dismiss()
    }
    @Test fun menuOpensBottomLeftAboveMenuKey() { checkAnchored(true); checkAnchored(false) }

    @Test fun anchoredMenuWorksByKeysNumbersTouchAndBack() {
        // number key
        var m = mainCtl().get(); press(m::dispatchKeyEvent, KeyEvent.KEYCODE_SOFT_LEFT)
        var d = ShadowDialog.getLatestDialog() as KeyDialog
        press(d::dispatchKeyEvent, KeyEvent.KEYCODE_3)
        assertEquals(EditEventActivity::class.java.name, shadowOf(m).nextStartedActivity.component!!.className)
        // D-pad down x3 + OK -> Settings
        m = mainCtl().get(); press(m::dispatchKeyEvent, KeyEvent.KEYCODE_SOFT_LEFT)
        d = ShadowDialog.getLatestDialog() as KeyDialog
        // (Robolectric's dialog dispatch does not run the view-root focus search, so walk it with focusSearch)
        var f: View = d.window!!.decorView.findFocus()!!
        assertTrue("first item focused", texts(f).contains("Today"))
        repeat(3) { f = f.focusSearch(View.FOCUS_DOWN)!! }
        assertTrue(texts(f).contains("Settings")); f.requestFocus()
        press(d::dispatchKeyEvent, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(SettingsActivity::class.java.name, shadowOf(m).nextStartedActivity.component!!.className)
        // touch -> Help
        m = mainCtl().get(); press(m::dispatchKeyEvent, KeyEvent.KEYCODE_SOFT_LEFT)
        d = ShadowDialog.getLatestDialog() as KeyDialog
        val help = views(d.window!!.decorView).filterIsInstance<TextView>().first { it.text.toString() == "Help" }
        tap(d.window!!.decorView, help)
        assertEquals(HelpActivity::class.java.name, shadowOf(m).nextStartedActivity.component!!.className)
        // Back closes, hardware BACK too
        for (k in listOf(KeyEvent.KEYCODE_SOFT_RIGHT, KeyEvent.KEYCODE_BACK)) {
            m = mainCtl().get(); press(m::dispatchKeyEvent, KeyEvent.KEYCODE_SOFT_LEFT)
            d = ShadowDialog.getLatestDialog() as KeyDialog
            press(d::dispatchKeyEvent, k); assertFalse(d.isShowing); assertFalse(m.isFinishing)
        }
    }
}
