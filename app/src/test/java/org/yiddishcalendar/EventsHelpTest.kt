package org.yiddishcalendar

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
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
import java.time.ZoneOffset

/** 0.2.4 second round: multi-day events, New event without arrows (pop-ups), Help, month slide. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w240dp-h296dp-port-xhdpi")
class EventsHelpTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val jan21 = LocalDate.of(2027, 1, 21)

    @Before fun prefs() {
        ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("asked_perms", true).putBoolean("nightfall", false).commit()
        ctx.deleteDatabase("local_events.db")
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun repo() = EventRepository(ctx, Prefs(ctx))
    private fun views(v: View): List<View> = if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else listOf(v)
    private fun texts(v: View) = views(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun press(dispatch: (KeyEvent) -> Boolean, code: Int) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(40))
        val t = SystemClock.uptimeMillis()
        dispatch(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0)); dispatch(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0)); idle()
    }
    private fun dialog() = ShadowDialog.getLatestDialog() as KeyDialog
    private fun main(date: LocalDate = jan21): MainActivity = Robolectric.buildActivity(MainActivity::class.java,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TEST_DATE, date.toEpochDay())).setup().get()

    // ---- multi-day events ----
    @Test fun multiDayAllDayAndTimedEventsCoverEveryDay() {
        val r = repo()
        assertEquals(SaveResult.LOCAL, r.add("Trip", jan21, null, Repeat.NEVER, null, days = 3))
        assertEquals(SaveResult.LOCAL, r.add("Seminar", LocalDate.of(2027, 1, 30), 10 * 60, Repeat.NEVER, null, days = 4))
        val map = r.eventsBetween(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 28))
        fun daysOf(t: String) = map.filterValues { l -> l.any { it.title == t } }.keys.sorted()
        assertEquals((0L..2).map { jan21.plusDays(it) }, daysOf("Trip"))
        assertEquals((0L..3).map { LocalDate.of(2027, 1, 30).plusDays(it) }, daysOf("Seminar"))  // crosses into February
    }

    @Test fun oldOneDayEventsStillWork() {
        val r = repo()
        r.add("Old style", jan21, null, Repeat.NEVER, null)            // default days = 1 (old call)
        r.add("Old timed", jan21, 9 * 60, Repeat.NEVER, null)
        val map = r.eventsBetween(jan21.minusDays(3), jan21.plusDays(3))
        assertEquals(setOf(jan21), map.filterValues { l -> l.any { it.title == "Old style" } }.keys)
        assertEquals(setOf(jan21), map.filterValues { l -> l.any { it.title == "Old timed" } }.keys)
    }

    @Test fun repeatingMultiDayEventRepeatsWholeSpan() {
        repo().add("Week trip", jan21, null, Repeat.WEEKLY, null, days = 2)
        val days = repo().eventsBetween(jan21, jan21.plusDays(14)).filterValues { l -> l.any { it.title == "Week trip" } }.keys.sorted()
        assertEquals(listOf(0L, 1, 7, 8, 14).map { jan21.plusDays(it) }, days)
    }

    @Test fun newEventScreenSavesAMultiDayEventShownInGridAndDay() {
        val ctl = Robolectric.buildActivity(EditEventActivity::class.java,
            Intent(ctx, EditEventActivity::class.java).putExtra(EditEventActivity.EXTRA_DATE, jan21.toEpochDay())).setup()
        val a = ctl.get(); val root = a.window.decorView
        views(root).filterIsInstance<android.widget.EditText>().first().setText("Trip")
        // D-pad: OK on the Days row opens a pop-up; key 3 picks "3 days".
        val daysRow = views(root).first { v -> v.isFocusable && v is ViewGroup && texts(v).any { it == "Days" || it == "DAYS" } }
        daysRow.requestFocus()
        press(a::dispatchKeyEvent, KeyEvent.KEYCODE_DPAD_CENTER)
        val d = dialog(); assertTrue(d.isShowing)
        press(d::dispatchKeyEvent, KeyEvent.KEYCODE_3)
        assertFalse(d.isShowing)
        assertTrue(texts(root).toString(), texts(root).any { it == "3 days · to Sat Jan 23" })
        press(a::dispatchKeyEvent, KeyEvent.KEYCODE_SOFT_LEFT)            // Save
        assertTrue(a.isFinishing)
        // grid: dot on all three days
        val m = main()
        val dots = views(m.window.decorView).filterIsInstance<DayCellView>().filter { it.eventDot && it.mainColor == m.palette.text }
        assertEquals(3, dots.size)
        // day screen: "Day 2 of 3"
        val day = Robolectric.buildActivity(DayActivity::class.java,
            Intent(ctx, DayActivity::class.java).putExtra(DayActivity.EXTRA_DATE, jan21.plusDays(1).toEpochDay())).setup().get()
        assertTrue(texts(day.window.decorView).toString(), texts(day.window.decorView).contains("Day 2 of 3"))
    }

    @Test fun newEventScreenHasNoArrowsAndEveryRowOpensAPopUp() {
        val a = Robolectric.buildActivity(EditEventActivity::class.java).setup().get(); val root = a.window.decorView
        val all = texts(root)
        assertTrue(all.none { it.contains('‹') || it.contains('›') || it.contains('◀') || it.contains('▶') || it.contains('>') })
        assertTrue("no chevron images", views(root).filterIsInstance<ImageView>().none { it.isShown })
        val rows = views(root).filter { it.isFocusable && it is ViewGroup && it.isShown }
        assertEquals("Date, Days, Time, Repeat, Reminder", 5, rows.size)
        for (r in rows) {
            r.requestFocus()
            press(a::dispatchKeyEvent, KeyEvent.KEYCODE_DPAD_CENTER)
            val d = dialog(); assertTrue(d.isShowing)
            // pop-up wraps its contents: not taller than the screen, and Back closes it
            press(d::dispatchKeyEvent, KeyEvent.KEYCODE_SOFT_RIGHT); assertFalse(d.isShowing)
        }
        // hint under the form
        assertEquals("OK to choose", a.findViewById<TextView>(R.id.hint).text.toString())
    }

    // ---- Help ----
    @Test fun menuItemFiveIsHelpAndOpensHelpScreen() {
        val m = main()
        press(m::dispatchKeyEvent, KeyEvent.KEYCODE_SOFT_LEFT)
        val d = dialog()
        val items = texts(d.window!!.decorView)
        assertTrue(items.toString(), items.contains("Help")); assertFalse(items.contains("About"))
        press(d::dispatchKeyEvent, KeyEvent.KEYCODE_5)
        assertEquals(HelpActivity::class.java.name, shadowOf(m).nextStartedActivity.component!!.className)
        val h = Robolectric.buildActivity(HelpActivity::class.java).setup().get()
        val t = texts(h.window.decorView)
        assertTrue(t.contains("Help")); assertTrue(t.contains("Change month")); assertTrue(t.contains("Open the selected day"))
        assertFalse("English only in English mode", t.any { it == "טוישן חודש" })
    }

    @Test fun helpInYiddishModeHasYiddishAndEnglish() {
        Prefs(ctx).hebrewText = 2
        val t = texts(Robolectric.buildActivity(HelpActivity::class.java).setup().get().window.decorView)
        assertTrue(t.contains("טוישן חודש")); assertTrue(t.contains("Change month")); assertTrue(t.contains("הילף · Help"))
        val m = main(); press(m::dispatchKeyEvent, KeyEvent.KEYCODE_SOFT_LEFT)
        assertTrue(texts(dialog().window!!.decorView).contains("הילף"))
    }

    @Test fun aboutStaysInSettings() {
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertTrue(texts(s.window.decorView).contains("About"))
    }

    // ---- month change: only the grid moves ----
    @Test fun monthChangeSlidesOnlyTheGridInTheRightDirection() {
        for (rtl in listOf(true, false)) {
            ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit().putBoolean("rtl_grid", rtl).commit()
            val m = main()
            val cal = m.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.calendar)
            val fixed = listOf(R.id.header, R.id.weekdays, R.id.statusTitle, R.id.softBar).map { m.findViewById<View>(it) }
            val before = fixed.map { v -> IntArray(2).also { v.getLocationInWindow(it) }.toList() }
            assertTrue("header etc. are not inside the sliding grid", fixed.none { v -> generateSequence(v.parent) { it.parent }.any { it === cal } })
            val lm = cal.layoutManager as LinearLayoutManager
            val pos = lm.findFirstVisibleItemPosition()
            val vec = lm.computeScrollVectorForPosition(pos + 1)!!
            // next month is the page below, in both right-to-left and left-to-right
            assertEquals("rtl=$rtl no sideways scroll vector", 0f, vec.x)
            assertTrue("rtl=$rtl next month is below: ${vec.y}", vec.y > 0)
            val h0 = m.findViewById<TextView>(R.id.headerTitle).text.toString()
            press(m::dispatchKeyEvent, if (rtl) KeyEvent.KEYCODE_4 else KeyEvent.KEYCODE_6)  // the key on the "next" side
            assertNotEquals("header updates in place", h0, m.findViewById<TextView>(R.id.headerTitle).text.toString())
            assertEquals(before, fixed.map { v -> IntArray(2).also { v.getLocationInWindow(it) }.toList() })
        }
    }
}
