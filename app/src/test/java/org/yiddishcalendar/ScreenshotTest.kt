package org.yiddishcalendar

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper
import java.io.File
import java.time.LocalDate

/**
 * Renders real app screens with Robolectric native graphics at the TCL Flip 2 size
 * (240x320 dp screen minus a ~24dp status bar = 240x296 dp app window, 2 px/dp, xhdpi). Output PNGs are mockups for review, not device proof.
 * Runs only when -Dycal.shots is set (always set by build.gradle; skipped if the folder can't be made).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = "w240dp-h296dp-port-xhdpi")
class ScreenshotTest {
    private val out: File? = System.getProperty("ycal.shots")?.let { File(it).apply { mkdirs() } }

    private fun ctx() = ApplicationProvider.getApplicationContext<Context>()

    private fun setPrefs(theme: Int = 1, hebrewMonths: Boolean = true, text: Int = 0, rtl: Boolean = true) {
        ctx().getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("rtl_grid", rtl).putInt("theme", theme).putBoolean("hebrew_months", hebrewMonths).putInt("hebrew_text", text)
            .putBoolean("asked_perms", true).putBoolean("nightfall", false).commit()
    }

    private fun shoot(view: View, name: String, overlay: View? = null) {
        val dir = out ?: return
        shadowOf(Looper.getMainLooper()).idle()
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        view.draw(c)
        overlay?.let {
            it.measure(View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.EXACTLY))
            it.layout(0, 0, view.width, view.height)
            it.draw(c)
        }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Keypad phone: not in touch mode, so the focused row/item shows its highlight like on the TCL. */
    private fun <T : android.app.Activity> start(cls: Class<T>, intent: Intent = Intent(ctx(), cls)): T {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        return Robolectric.buildActivity(cls, intent).setup().get()
    }

    private fun day(name: String, date: LocalDate) {
        val a = start(DayActivity::class.java, Intent(ctx(), DayActivity::class.java).putExtra(DayActivity.EXTRA_DATE, date.toEpochDay()))
        shoot(a.window.decorView.rootView, name)
    }

    private fun menu(name: String) {
        val a = start(MainActivity::class.java, Intent(ctx(), MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TEST_DATE, LocalDate.of(2027, 1, 21).toEpochDay()))
        a.onLeftSoft()
        shadowOf(Looper.getMainLooper()).idle()
        val dlg = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        shoot(a.window.decorView.rootView, name, dlg.window!!.decorView)
    }

    private fun main(name: String, date: LocalDate) {
        val intent = Intent(ctx(), MainActivity::class.java).putExtra(MainActivity.EXTRA_TEST_DATE, date.toEpochDay())
        shoot(start(MainActivity::class.java, intent).window.decorView.rootView, name)
    }

    @Test fun dayShabbosLight() { assumeTrue(out != null); setPrefs(); day("day_light", LocalDate.of(2027, 1, 23)) }
    @Test fun settingsLight() { assumeTrue(out != null); setPrefs(); shoot(start(SettingsActivity::class.java).window.decorView.rootView, "settings_top_light") }
    @Test fun aboutLight() { assumeTrue(out != null); setPrefs(); shoot(start(AboutActivity::class.java).window.decorView.rootView, "about_light") }
    @Test fun menuLight() { assumeTrue(out != null); setPrefs(); menu("menu_light") }
    @Test fun monthHebrewDark() { assumeTrue(out != null); setPrefs(theme = 0); main("main_hebrew_dark", LocalDate.of(2027, 1, 21)) }
    @Test fun monthGregorianLight() { assumeTrue(out != null); setPrefs(hebrewMonths = false); main("main_greg_light", LocalDate.of(2027, 1, 21)) }
    @Test fun monthTishreiLight() { assumeTrue(out != null); setPrefs(); main("main_tishrei_light", LocalDate.of(2026, 9, 26)) }
    @Test fun monthNisanLight() { assumeTrue(out != null); setPrefs(); main("main_nisan_light", LocalDate.of(2027, 4, 22)) }
    @Test fun monthGregorianDark() { assumeTrue(out != null); setPrefs(theme = 0, hebrewMonths = false); main("main_greg_dark", LocalDate.of(2027, 1, 21)) }
    @Test fun settingsBottomLight() {
        assumeTrue(out != null); setPrefs()
        val a = start(SettingsActivity::class.java)
        a.findViewById<android.widget.ScrollView>(R.id.scroll).scrollTo(0, 10_000)
        shoot(a.window.decorView.rootView, "settings_bottom_light")
    }
    @Test fun settingsDark() { assumeTrue(out != null); setPrefs(theme = 0); shoot(start(SettingsActivity::class.java).window.decorView.rootView, "settings_top_dark") }
    @Test fun addEventLight() { assumeTrue(out != null); setPrefs(); shoot(start(EditEventActivity::class.java).window.decorView.rootView, "add_event_light") }
    @Test fun dayDark() { assumeTrue(out != null); setPrefs(theme = 0); day("day_dark", LocalDate.of(2027, 1, 23)) }
    @Test fun monthLtrLight() { assumeTrue(out != null); setPrefs(rtl = false); main("check_main_ltr", LocalDate.of(2027, 1, 21)) }
    @Test fun dayEverything() { assumeTrue(out != null); setPrefs(text = 2); day("check_day_everything", LocalDate.of(2027, 1, 23)) }
    @Test fun monthHebrewLight() { assumeTrue(out != null); setPrefs(); main("main_hebrew_light", LocalDate.of(2027, 1, 21)) }
    @Test fun settingsEverything() { assumeTrue(out != null); setPrefs(text = 2); shoot(start(SettingsActivity::class.java).window.decorView.rootView, "settings_top_everything") }
    @Test fun aboutBottomLight() {
        assumeTrue(out != null); setPrefs()
        val a = start(AboutActivity::class.java)
        (a.findViewById<android.view.View>(R.id.scroll) as? android.widget.ScrollView)?.scrollTo(0, 10_000)
        shoot(a.window.decorView.rootView, "about_bottom_light")
    }

    // ---- 0.2.4 round 2 ----
    private fun key(dispatch: (android.view.KeyEvent) -> Boolean, code: Int) {
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(40))
        val t = android.os.SystemClock.uptimeMillis()
        dispatch(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, code, 0))
        dispatch(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, code, 0))
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun addTrip() {
        ctx().deleteDatabase("local_events.db")
        EventRepository(ctx(), Prefs(ctx())).add("Trip to Monsey", LocalDate.of(2027, 1, 25), null, Repeat.NEVER, null, days = 3)
    }
    @Test fun addEventDaysPopup() {
        assumeTrue(out != null); setPrefs()
        val a = start(EditEventActivity::class.java, Intent(ctx(), EditEventActivity::class.java).putExtra(EditEventActivity.EXTRA_DATE, LocalDate.of(2027, 1, 25).toEpochDay()))
        // move focus to the Days row (2nd row after the title) and press OK
        key(a::dispatchKeyEvent, android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        key(a::dispatchKeyEvent, android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        shoot(a.window.decorView.rootView, "add_event_rows_light")
        key(a::dispatchKeyEvent, android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        val dlg = org.robolectric.shadows.ShadowDialog.getLatestDialog() as KeyDialog
        key(dlg::dispatchKeyEvent, android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        key(dlg::dispatchKeyEvent, android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        shoot(a.window.decorView.rootView, "add_event_days_popup_light", dlg.window!!.decorView)
    }
    @Test fun multiDayInGrid() { assumeTrue(out != null); setPrefs(); addTrip(); main("main_multiday_light", LocalDate.of(2027, 1, 26)) }
    @Test fun multiDayDay() { assumeTrue(out != null); setPrefs(); addTrip(); day("day_multiday_light", LocalDate.of(2027, 1, 26)) }
    @Test fun helpLight() { assumeTrue(out != null); setPrefs(); shoot(start(HelpActivity::class.java).window.decorView.rootView, "help_light") }
    @Test fun helpYiddish() { assumeTrue(out != null); setPrefs(text = 2); shoot(start(HelpActivity::class.java).window.decorView.rootView, "help_yiddish") }

    // ---- 0.2.4 round 3: tight pop-ups ----
    private fun popupOver(a: android.app.Activity, name: String) {
        shadowOf(Looper.getMainLooper()).idle(); shadowOf(Looper.getMainLooper()).idle()
        val dlg = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        shoot(a.window.decorView.rootView, name, dlg.window!!.decorView)
    }
    @Test fun settingsLanguagePopup() {
        assumeTrue(out != null); setPrefs()
        val a = start(SettingsActivity::class.java)
        key(a::dispatchKeyEvent, android.view.KeyEvent.KEYCODE_DPAD_CENTER)   // Language is the first, focused row
        popupOver(a, "popup_language_light")
    }
    @Test fun addEventRepeatPopup() {
        assumeTrue(out != null); setPrefs()
        val a = start(EditEventActivity::class.java)
        repeat(4) { key(a::dispatchKeyEvent, android.view.KeyEvent.KEYCODE_DPAD_DOWN) }
        key(a::dispatchKeyEvent, android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        popupOver(a, "popup_repeat_light")
    }
    @Test fun goToDatePopup() {
        assumeTrue(out != null); setPrefs()
        val a = start(MainActivity::class.java, Intent(ctx(), MainActivity::class.java).putExtra(MainActivity.EXTRA_TEST_DATE, LocalDate.of(2027, 1, 21).toEpochDay()))
        key(a::dispatchKeyEvent, android.view.KeyEvent.KEYCODE_STAR)
        popupOver(a, "popup_gotodate_light")
    }
    @Test @Config(qualifiers = "w400dp-h240dp-land-xhdpi") fun menuSmallLandscape() { assumeTrue(out != null); setPrefs(); menu("popup_menu_400x240") }
    @Test @Config(qualifiers = "w240dp-h400dp-port-xhdpi") fun menuLgExalt() { assumeTrue(out != null); setPrefs(); menu("popup_menu_240x400") }
}
