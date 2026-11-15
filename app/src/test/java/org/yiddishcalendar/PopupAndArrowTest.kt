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
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.LocalDate

/** 0.2.4: no arrows in Settings; every pop-up wraps its contents (240x296/320 dp and small 400x240 dp screens). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE) // real text sizes
@Config(sdk = [30], qualifiers = "w240dp-h296dp-port-xhdpi")
class PopupAndArrowTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun prefs() {
        ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("asked_perms", true).putBoolean("nightfall", false).commit()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun views(v: View): List<View> = if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else listOf(v)
    private fun texts(v: View) = views(v).filterIsInstance<TextView>().filter { it.isShown }.map { it.text.toString() }
    private fun press(dispatch: (KeyEvent) -> Boolean, code: Int) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(40))
        val t = SystemClock.uptimeMillis()
        dispatch(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0)); dispatch(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0)); idle()
    }
    private fun arrows(t: String) = t.any { it in "<>‹›◀▶❯❮→←" }

    private fun checkNoArrows(level: Int) {
        Prefs(ctx).hebrewText = level
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        val root = a.window.decorView
        assertTrue("no arrow images", views(root).filterIsInstance<ImageView>().none { it.isShown })
        assertTrue("no arrow characters: ${texts(root)}", texts(root).none { arrows(it) })
    }

    @Test fun settingsHasNoArrowsInEnglishOrYiddish() { checkNoArrows(0); checkNoArrows(2) }

    @Test fun languageAndAboutRowsStillWork() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)  // keypad phone
        val a = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        val root = a.window.decorView
        val lang = views(root).first { it.isFocusable && it is ViewGroup && texts(it).contains(SettingsActivity.LANGUAGE_LABEL) }
        lang.requestFocus(); press(a::dispatchKeyEvent, KeyEvent.KEYCODE_DPAD_CENTER)
        val d = ShadowDialog.getLatestDialog() as KeyDialog; assertTrue(d.isShowing); d.dismiss()
        lang.performClick(); idle()
        assertTrue((ShadowDialog.getLatestDialog() as KeyDialog).isShowing)
        val about = views(root).first { it.isFocusable && it is ViewGroup && texts(it).contains("About") }
        about.performClick(); idle()
        assertEquals(AboutActivity::class.java.name, shadowOf(a).nextStartedActivity.component!!.className)
        about.requestFocus(); press(a::dispatchKeyEvent, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(AboutActivity::class.java.name, shadowOf(a).nextStartedActivity.component!!.className)
    }

    // ---- pop-ups wrap their contents ----
    private fun main(): MainActivity = Robolectric.buildActivity(MainActivity::class.java,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TEST_DATE, LocalDate.of(2027, 1, 21).toEpochDay())).setup().get()

    /** Asserts the card hugs its contents and stays on screen above the bar; returns true if the list scrolls. */
    private fun checkTight(d: KeyDialog, name: String): Boolean {
        idle(); idle()
        val root = d.window!!.decorView
        val card = root.findViewById<ViewGroup>(R.id.card)
        val scroll = root.findViewById<android.widget.ScrollView>(R.id.dlgScroll)
        val body = root.findViewById<ViewGroup>(R.id.dlgBody)
        val bar = root.findViewById<View>(R.id.softBar)
        val dp = ctx.resources.displayMetrics.density
        val scrolls = body.height > scroll.height
        if (!scrolls) assertEquals("$name: no empty space under the last item", body.height, scroll.height)
        // card = title + scroll + padding, nothing more
        val title = root.findViewById<View>(R.id.dlgTitle)
        val inner = (if (title.visibility == View.VISIBLE) title.height else 0) + scroll.height + card.paddingTop + card.paddingBottom
        assertEquals("$name: card height = contents", inner, card.height)
        // never off screen, never under the bar
        val cardLoc = IntArray(2).also { card.getLocationInWindow(it) }
        val barLoc = IntArray(2).also { bar.getLocationInWindow(it) }
        assertTrue("$name: top on screen (${cardLoc[1]})", cardLoc[1] >= 0)
        assertTrue("$name: above the bar", cardLoc[1] + card.height <= barLoc[1])
        // not wider than needed: the widest row decides; short menus don't stretch to the screen edge
        val screenW = root.width
        assertTrue("$name: width ${card.width} <= screen $screenW", card.width <= screenW - (20 * dp).toInt() + 1)
        return scrolls
    }

    private fun allPopups(tag: String) {
        // main menu
        var m = main(); press(m::dispatchKeyEvent, KeyEvent.KEYCODE_SOFT_LEFT)
        var d = ShadowDialog.getLatestDialog() as KeyDialog
        assertFalse("$tag menu fits without scrolling", checkTight(d, "$tag menu"))
        val menuW = d.window!!.decorView.findViewById<View>(R.id.card).width
        assertTrue("$tag menu is not full width", menuW < d.window!!.decorView.width - (20 * ctx.resources.displayMetrics.density).toInt())
        d.dismiss()
        // go to date (input)
        m = main(); press(m::dispatchKeyEvent, KeyEvent.KEYCODE_STAR)
        d = ShadowDialog.getLatestDialog() as KeyDialog; checkTight(d, "$tag go to date"); d.dismiss()
        // settings language pick list
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get(); idle()
        views(s.window.decorView).first { it.isFocusable && it is ViewGroup && texts(it).contains(SettingsActivity.LANGUAGE_LABEL) }.performClick(); idle()
        d = ShadowDialog.getLatestDialog() as KeyDialog; checkTight(d, "$tag language"); d.dismiss()
        // new event pop-ups: Repeat (short), Days and Date (long -> capped, scrolls)
        val e = Robolectric.buildActivity(EditEventActivity::class.java).setup().get(); idle()
        fun open(label: String): KeyDialog {
            views(e.window.decorView).first { it.isFocusable && it is ViewGroup && texts(it).any { t -> t.equals(label, true) } }.performClick(); idle()
            return ShadowDialog.getLatestDialog() as KeyDialog
        }
        // Repeat (5 items + title) fits on every portrait screen; on a 240dp-tall landscape screen it may scroll a little.
        d = open("Repeat"); val rs = checkTight(d, "$tag repeat"); if (!tag.startsWith("400")) assertFalse("$tag repeat fits", rs); d.dismiss()
        d = open("Reminder"); checkTight(d, "$tag reminder"); d.dismiss()
        d = open("Days"); checkTight(d, "$tag days"); d.dismiss()
        d = open("Date"); assertTrue("$tag 75 dates scroll", checkTight(d, "$tag date")); d.dismiss()
    }

    @Test fun popupsWrapOnTclAppWindow() = allPopups("240x296")
    @Test @Config(qualifiers = "w240dp-h320dp-port-xhdpi") fun popupsWrapOn240x320() = allPopups("240x320")
    @Test @Config(qualifiers = "w400dp-h240dp-land-mdpi") fun popupsWrapOnSmallLandscape() = allPopups("400x240")
    @Test @Config(qualifiers = "w240dp-h400dp-port-mdpi") fun popupsWrapOnLgExaltPortrait() = allPopups("240x400")
}
