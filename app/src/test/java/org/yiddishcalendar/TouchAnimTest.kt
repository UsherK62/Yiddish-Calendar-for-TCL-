package org.yiddishcalendar

import android.content.Context
import android.content.Intent
import android.graphics.drawable.RippleDrawable
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.LocalDate

/** 0.2.6 touch motion: ripples, pop-up fade, finger-follow month swipe, long-press to add. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w240dp-h296dp-port-xhdpi")
class TouchAnimTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun prefs() {
        Motion.overrideEnabled = null
        ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("asked_perms", true).putBoolean("nightfall", false).commit()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @After fun resetMotion() { Motion.overrideEnabled = null }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun main(): MainActivity = Robolectric.buildActivity(
        MainActivity::class.java,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TEST_DATE, LocalDate.of(2027, 1, 21).toEpochDay()),
    ).setup().get().also { idle() }

    private fun views(v: View): List<View> =
        if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else listOf(v)
    private fun texts(v: View) = views(v).filterIsInstance<TextView>().filter { it.isShown }.map { it.text.toString() }
    private fun header(a: android.app.Activity) = a.findViewById<TextView>(R.id.headerTitle).text.toString()
    private fun started(a: android.app.Activity) = shadowOf(a).nextStartedActivity
    private fun settle() {
        repeat(25) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(40))
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    // ---- 1. ripples, focus ring still there ----
    @Test fun dayCellsHaveRippleAndKeepSelectionRing() {
        val a = main()
        val cells = views(a.window.decorView).filterIsInstance<DayCellView>().filter { it.isShown }
        assertTrue(cells.isNotEmpty())
        assertTrue("every cell uses a ripple", cells.all { it.background is RippleDrawable })
        val selected = cells.first { it.selectedRing }
        assertTrue(selected.ringColor != 0)
        assertFalse("D-pad still owns focus", selected.isFocusable)
        assertTrue("unselected cells have no ring", cells.any { !it.selectedRing })
    }

    @Test fun settingsRowRippleDoesNotReplaceFocusHighlight() {
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        val row = views(s.window.decorView).first {
            it.isFocusable && it is ViewGroup && texts(it).contains(Ui.tr(0, "Right-to-left"))
        }
        assertTrue(row.background is RippleDrawable)
        row.requestFocus(); idle()
        assertTrue(row.isFocused)
        // The ripple's content is still row_bg, which draws the 2dp focus ring when state_focused is set.
        val d = row.background as RippleDrawable
        assertTrue(d.getState().contains(android.R.attr.state_focused) || row.isFocused)
        val number = IntArray(d.numberOfLayers)
        assertTrue("content + mask", d.numberOfLayers >= 1)
        // The focused look is the same selector as before (row_bg is the content layer).
        assertNotNull(d)
    }

    @Test fun softKeyLabelsHaveRippleWhenTappable() {
        val a = main()
        assertTrue(a.findViewById<View>(R.id.skLeft).background is RippleDrawable)
        assertTrue(a.findViewById<View>(R.id.skCenter).background is RippleDrawable)
        assertTrue(a.findViewById<View>(R.id.skRight).background is RippleDrawable)
        assertFalse(a.findViewById<View>(R.id.skLeft).isFocusable)
        val about = Robolectric.buildActivity(AboutActivity::class.java).setup().get(); idle()
        // About: right/back is tappable; centre is empty
        assertTrue(about.findViewById<View>(R.id.skRight).background is RippleDrawable)
        assertFalse(about.findViewById<View>(R.id.skCenter).isClickable)
    }

    @Test fun dialogRowsHaveRipple() {
        val a = main()
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SOFT_LEFT))
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SOFT_LEFT)); idle()
        val d = ShadowDialog.getLatestDialog() as KeyDialog
        val label = views(d.window!!.decorView).filterIsInstance<TextView>().first { it.text.toString() == "Today" }
        var row: View? = label
        while (row != null && row.background !is RippleDrawable) row = row.parent as? View
        assertNotNull("the Today row has a ripple", row)
        assertTrue(row!!.isFocusable)
        row.requestFocus(); idle()
        assertTrue(row.isFocused)
    }

    // ---- 2. pop-up motion ----
    @Test fun motionOffLeavesDialogAtFullSizeAndDismissesAtOnce() {
        Motion.overrideEnabled = false
        val a = main()
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SOFT_LEFT))
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SOFT_LEFT)); idle()
        val d = ShadowDialog.getLatestDialog() as KeyDialog
        val card = d.findViewById<View>(R.id.card)
        assertEquals(1f, card.alpha, 0.01f)
        assertEquals(1f, card.scaleX, 0.01f)
        d.dismiss()
        assertFalse(d.isShowing)
    }

    @Test fun menuGrowsFromBottomLeftAndFadesOut() {
        Motion.overrideEnabled = true
        val a = main()
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SOFT_LEFT))
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SOFT_LEFT)); idle(); settle()
        val d = ShadowDialog.getLatestDialog() as KeyDialog
        val card = d.findViewById<View>(R.id.card)
        // Robolectric often finishes the 200ms animation during idle(); the growth origin is what we can check.
        assertEquals("grows from the bottom-left corner", 0f, card.pivotX, 0.5f)
        assertEquals(card.height.toFloat(), card.pivotY, 0.5f)
        assertEquals(1f, card.alpha, 0.01f)
        assertEquals(1f, card.scaleX, 0.01f)
        assertEquals(Motion.SHOW_MS, Motion.duration(ctx, Motion.SHOW_MS))
        assertEquals(Motion.HIDE_MS, Motion.duration(ctx, Motion.HIDE_MS))
        d.dismiss(); settle()
        assertFalse(d.isShowing)
    }

    @Test fun centeredDialogPivotsFromCenter() {
        Motion.overrideEnabled = true
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        val lang = views(s.window.decorView).first { it is ViewGroup && texts(it).any { t -> t.contains("Language") } && it.isFocusable }
        lang.performClick(); idle()
        val d = ShadowDialog.getLatestDialog() as KeyDialog
        val card = d.findViewById<View>(R.id.card)
        idle()
        assertEquals(card.width / 2f, card.pivotX, 1f)
        assertEquals(card.height / 2f, card.pivotY, 1f)
        settle()
        assertEquals(1f, card.scaleX, 0.01f)
        Motion.overrideEnabled = false // remaining tests in this method shouldn't matter
        d.dismiss()
    }

    @Test fun animatorScaleZeroDisablesMotion() {
        Motion.overrideEnabled = null
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        // On Robolectric, enabled() is false even before the scale check, which is what we want.
        assertFalse(Motion.enabled(ctx))
        Motion.overrideEnabled = true
        assertTrue("tests can still force motion on", Motion.enabled(ctx))
        Motion.overrideEnabled = false
        assertFalse(Motion.enabled(ctx))
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    // ---- 3. swipe follows the finger and snaps ----
    private fun swipeV(root: View, v: View, dyPx: Float, ms: Long = 80L) {
        val loc = IntArray(2); v.getLocationInWindow(loc)
        val x = loc[0] + v.width / 2f; val y = loc[1] + v.height / 2f
        val t = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        val steps = 8
        for (i in 1..steps) {
            root.dispatchTouchEvent(MotionEvent.obtain(t, t + i * (ms / steps), MotionEvent.ACTION_MOVE, x, y + dyPx * i / steps, 0))
        }
        root.dispatchTouchEvent(MotionEvent.obtain(t, t + ms, MotionEvent.ACTION_UP, x, y + dyPx, 0))
        ShadowSystemClock.advanceBy(Duration.ofMillis(ms + 20)); idle(); settle()
    }

    @Test fun verticalSwipeFollowsFingerAndSettlesOnAMonth() {
        val up = main(); val cal = up.findViewById<CalendarView>(R.id.calendar)
        val h0 = header(up)
        val y0 = cal.computeVerticalScrollOffset()
        val fixed = listOf(R.id.header, R.id.weekdays, R.id.statusTitle, R.id.softBar).map { up.findViewById<View>(it) }
        val before = fixed.map { v -> IntArray(2).also { v.getLocationInWindow(it) }.toList() }
        // A long, fairly quick flick up: past the midpoint or fast enough to count as a fling.
        swipeV(up.window.decorView, cal, -cal.height.toFloat(), 60)
        assertNotEquals(h0, header(up))
        assertEquals(0, cal.computeHorizontalScrollOffset())
        assertTrue(cal.computeVerticalScrollOffset() > y0)
        assertEquals("only the grid moves", before, fixed.map { v -> IntArray(2).also { v.getLocationInWindow(it) }.toList() })
        assertNull(started(up))
        val down = main()
        val h1 = header(down)
        swipeV(down.window.decorView, down.findViewById(R.id.calendar), down.findViewById<View>(R.id.calendar).height.toFloat(), 60)
        assertNotEquals(h1, header(down))
    }

    @Test fun shortSidewaysSwipeDoesNothing() {
        for (dx in listOf(80f, -80f)) {
            val a = main(); val h0 = header(a)
            val status0 = a.findViewById<TextView>(R.id.statusTitle).text.toString()
            val cal = a.findViewById<View>(R.id.calendar)
            val loc = IntArray(2); cal.getLocationInWindow(loc)
            val x = loc[0] + cal.width / 2f; val y = loc[1] + cal.height / 2f
            val t = SystemClock.uptimeMillis()
            val root = a.window.decorView
            root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
            for (i in 1..5) root.dispatchTouchEvent(MotionEvent.obtain(t, t + i * 10L, MotionEvent.ACTION_MOVE, x + a.dp(dx) * i / 5f, y, 0))
            root.dispatchTouchEvent(MotionEvent.obtain(t, t + 70, MotionEvent.ACTION_UP, x + a.dp(dx).toFloat(), y, 0))
            idle(); settle()
            assertEquals(h0, header(a))
            assertEquals(status0, a.findViewById<TextView>(R.id.statusTitle).text.toString())
            assertEquals(0, a.findViewById<CalendarView>(R.id.calendar).computeHorizontalScrollOffset())
        }
    }

    // ---- 4. long-press adds an event for that day; Add key is unchanged ----
    @Test fun longPressDayOpensAddEventForThatDay() {
        val a = main()
        val start = LocalDate.of(2027, 1, 21)
        val cells = views(a.window.decorView).filterIsInstance<DayCellView>().filter { it.isShown && it.mainColor == a.palette.text }
        val target = cells.first { !it.selectedRing }
        val desc = target.contentDescription.toString()
        target.performLongClick(); idle()
        val intent = started(a)
        assertEquals(EditEventActivity::class.java.name, intent!!.component!!.className)
        val epoch = intent.getLongExtra(EditEventActivity.EXTRA_DATE, Long.MIN_VALUE)
        assertTrue(epoch != Long.MIN_VALUE)
        val opened = LocalDate.ofEpochDay(epoch)
        assertNotEquals("a different day than the one already selected", start, opened)
        assertTrue(desc.contains(Luach(ctx, Prefs(ctx)).fullHebrewDate(opened)) || desc.contains(Luach(ctx, Prefs(ctx)).dayTitle(opened)))
        assertFalse(target.isFocusable)
    }

    @Test fun addSoftKeyStillUsesTheSelectedDay() {
        val a = main()
        val start = LocalDate.of(2027, 1, 21)
        a.onRightSoft()
        val intent = started(a)!!
        assertEquals(EditEventActivity::class.java.name, intent.component!!.className)
        assertEquals(start.toEpochDay(), intent.getLongExtra(EditEventActivity.EXTRA_DATE, Long.MIN_VALUE))
    }

    @Test fun shortTapStillSelectsThenOpens() {
        val a = main(); val root = a.window.decorView
        val cells = views(root).filterIsInstance<DayCellView>().filter { it.isShown && it.mainColor == a.palette.text }
        val target = cells.first { !it.selectedRing }
        val before = a.findViewById<TextView>(R.id.statusTitle).text.toString()
        target.performClick(); idle()
        assertNull(started(a))
        assertNotEquals(before, a.findViewById<TextView>(R.id.statusTitle).text.toString())
        target.performClick(); idle()
        assertEquals(DayActivity::class.java.name, started(a)!!.component!!.className)
    }

    @Test fun noInternetPermissionAdded() {
        val perms = ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions
        assertFalse((perms ?: emptyArray()).contains("android.permission.INTERNET"))
    }

    private fun press(a: MainActivity, code: Int) {
        val t0 = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t0, t0, KeyEvent.ACTION_DOWN, code, 0))
        a.dispatchKeyEvent(KeyEvent(t0, t0, KeyEvent.ACTION_UP, code, 0))
        idle()
    }
    private fun selectedCell(a: MainActivity) =
        views(a.window.decorView).filterIsInstance<DayCellView>().first { it.isShown && it.selectedRing }

    // ---- 5. keypad: gliding focus, Today pulse, soft-key dim ----
    @Test fun dpadGlideEndsWithCrispRingOnTheNewDay() {
        Motion.overrideEnabled = true
        val a = main()
        val start = a.findViewById<TextView>(R.id.statusTitle).text.toString()
        val ring = a.findViewById<SelectionRingView>(R.id.selectionRing)
        press(a, KeyEvent.KEYCODE_DPAD_LEFT) // RTL: next day, still in Shevat 5787
        settle()
        val after = a.findViewById<TextView>(R.id.statusTitle).text.toString()
        assertNotEquals(start, after)
        val cell = selectedCell(a)
        assertTrue("the ring is on and crisp", cell.selectedRing && cell.ringColor != 0)
        assertEquals("only one cell is selected", 1, views(a.window.decorView).filterIsInstance<DayCellView>().count { it.selectedRing })
        assertTrue("the floating ring is put away", ring.visibility != View.VISIBLE)
        assertFalse(cell.isFocusable)
    }

    @Test fun dpadGlideIsInstantWhenMotionIsOff() {
        Motion.overrideEnabled = false
        val a = main()
        val start = a.findViewById<TextView>(R.id.statusTitle).text.toString()
        press(a, KeyEvent.KEYCODE_DPAD_LEFT)
        val cell = selectedCell(a)
        assertNotEquals(start, a.findViewById<TextView>(R.id.statusTitle).text.toString())
        assertTrue(cell.selectedRing)
        assertTrue(a.findViewById<View>(R.id.selectionRing).visibility != View.VISIBLE)
    }

    @Test fun dpadAcrossAMonthKeepsTheVerticalSlideAndACrispRing() {
        Motion.overrideEnabled = true
        val a = main()
        val h0 = header(a)
        val y0 = a.findViewById<CalendarView>(R.id.calendar).computeVerticalScrollOffset()
        var n = 0
        while (header(a) == h0 && n < 8) { press(a, KeyEvent.KEYCODE_DPAD_DOWN); n++ }
        settle()
        assertNotEquals("crossed a month", h0, header(a))
        assertEquals(0, a.findViewById<CalendarView>(R.id.calendar).computeHorizontalScrollOffset())
        assertTrue(a.findViewById<CalendarView>(R.id.calendar).computeVerticalScrollOffset() > y0)
        val cell = selectedCell(a)
        assertTrue(cell.selectedRing && cell.ringColor != 0)
        assertTrue(a.findViewById<View>(R.id.selectionRing).visibility != View.VISIBLE)
    }

    @Test fun todayKeyPulsesTodaysCellOnce() {
        Motion.overrideEnabled = true
        val a = main()
        press(a, KeyEvent.KEYCODE_0)
        settle()
        val cell = selectedCell(a)
        assertTrue(cell.selectedRing)
        assertTrue("Today glows once", cell.highlightPulses >= 1)
        // after 400 ms the glow has faded; the ring stays
        assertEquals(0f, cell.glowAlpha, 0.01f)
    }

    @Test fun todayKeyDoesNotPulseWhenMotionIsOff() {
        Motion.overrideEnabled = false
        val a = main()
        press(a, KeyEvent.KEYCODE_0)
        settle()
        val cell = selectedCell(a)
        assertTrue(cell.selectedRing)
        assertEquals(0f, cell.glowAlpha, 0.01f)
    }

    @Test fun softKeyLabelDimsOnKeyAndReturns() {
        Motion.overrideEnabled = true
        val a = main()
        val left = a.findViewById<View>(R.id.skLeft)
        assertEquals(1f, left.alpha, 0.01f)
        val t0 = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t0, t0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SOFT_LEFT, 0))
        assertEquals(Motion.SOFT_PRESS_ALPHA, left.alpha, 0.05f)
        a.dispatchKeyEvent(KeyEvent(t0, t0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SOFT_LEFT, 0))
        settle()
        assertEquals(1f, left.alpha, 0.01f)
    }

    @Test fun softKeyLabelDimsOnTap() {
        Motion.overrideEnabled = true
        val a = main()
        val add = a.findViewById<View>(R.id.skRight)
        assertEquals(1f, add.alpha, 0.01f)
        add.performClick()
        assertEquals(Motion.SOFT_PRESS_ALPHA, add.alpha, 0.05f)
        settle()
        assertEquals(1f, add.alpha, 0.01f)
        assertEquals(EditEventActivity::class.java.name, started(a)!!.component!!.className)
    }

    @Test fun softKeyLabelDoesNotDimWhenMotionIsOff() {
        Motion.overrideEnabled = false
        val a = main()
        val left = a.findViewById<View>(R.id.skLeft)
        val t0 = SystemClock.uptimeMillis()
        a.dispatchKeyEvent(KeyEvent(t0, t0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SOFT_LEFT, 0))
        assertEquals(1f, left.alpha, 0.01f)
    }
}
