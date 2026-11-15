package org.yiddishcalendar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
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
import java.io.File
import java.time.Duration
import java.time.LocalDate

/** 0.2.3: soft keys on phones that send odd codes / hide DOWNs (Sonim X320), the language escape, minSdk. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w240dp-h296dp-port-xhdpi")
class SoftKeyAndLegacyTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun prefs() {
        ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("asked_perms", true).putBoolean("nightfall", false).commit()
    }

    private fun main(): MainActivity = Robolectric.buildActivity(MainActivity::class.java,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TEST_DATE, LocalDate.of(2027, 1, 21).toEpochDay())).setup().get()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun now() = SystemClock.uptimeMillis()
    private fun down(code: Int, t: Long = now()) = KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0)
    private fun up(code: Int, t: Long = now()) = KeyEvent(t, now(), KeyEvent.ACTION_UP, code, 0)
    private fun started(a: Activity) = shadowOf(a).nextStartedActivity?.component?.className

    private fun press(a: Activity, code: Int) { val t = now(); a.dispatchKeyEvent(down(code, t)); a.dispatchKeyEvent(up(code, t)); idle() }

    @Test fun rightSoftDownOpensAddEventExactlyOnce() {
        val a = main(); ShadowSystemClock.advanceBy(Duration.ofMillis(50))
        press(a, KeyEvent.KEYCODE_SOFT_RIGHT)
        assertEquals(EditEventActivity::class.java.name, started(a))
        assertNull("DOWN+UP must not open it twice", started(a))
    }

    @Test fun everyRightSoftCodeOpensAddEvent() {
        for (code in listOf(KeyEvent.KEYCODE_SOFT_RIGHT, KeyEvent.KEYCODE_F2)) {
            val a = main(); ShadowSystemClock.advanceBy(Duration.ofMillis(50))
            press(a, code)
            assertEquals(KeyEvent.keyCodeToString(code), EditEventActivity::class.java.name, started(a))
        }
    }

    @Test fun loneUpWithoutDownStillOpensAddEvent() {
        // Sonim-style: the phone/system swallows the DOWN and only the UP reaches the app.
        val a = main(); ShadowSystemClock.advanceBy(Duration.ofMillis(50))
        a.dispatchKeyEvent(up(KeyEvent.KEYCODE_SOFT_RIGHT)); idle()
        assertEquals(EditEventActivity::class.java.name, started(a))
        val b = main()
        b.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SOFT_RIGHT, 0)); idle()
        assertEquals("lone UP with no down time", EditEventActivity::class.java.name, started(b))
    }

    @Test fun upOfAPressThatStartedOnThePreviousScreenIsIgnored() {
        val pressStart = now()
        ShadowSystemClock.advanceBy(Duration.ofMillis(100))
        val a = main()
        a.dispatchKeyEvent(up(KeyEvent.KEYCODE_SOFT_RIGHT, pressStart)); idle()
        assertNull(started(a))
    }

    @Test fun leftSoftCodesOpenTheMenuAndMenuAddEventWorks() {
        for (code in listOf(KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_F1)) {
            val a = main(); ShadowSystemClock.advanceBy(Duration.ofMillis(50))
            press(a, code)
            val dlg = ShadowDialog.getLatestDialog()
            assertTrue(KeyEvent.keyCodeToString(code), dlg is KeyDialog && dlg.isShowing)
            ShadowSystemClock.advanceBy(Duration.ofMillis(50))
            // "Add event" is item 3 in the menu.
            val t = now(); dlg.dispatchKeyEvent(down(KeyEvent.KEYCODE_3, t)); dlg.dispatchKeyEvent(up(KeyEvent.KEYCODE_3, t)); idle()
            assertFalse(dlg.isShowing)
            assertEquals(EditEventActivity::class.java.name, started(a))
            dlg.dismiss()
        }
    }

    @Test fun menuSurvivesTheUpOfThePressThatOpenedIt() {
        val a = main(); ShadowSystemClock.advanceBy(Duration.ofMillis(50))
        val t = now()
        a.dispatchKeyEvent(down(KeyEvent.KEYCODE_SOFT_LEFT, t)); idle()   // menu opens on DOWN
        val dlg = ShadowDialog.getLatestDialog()
        ShadowSystemClock.advanceBy(Duration.ofMillis(30))
        dlg.dispatchKeyEvent(up(KeyEvent.KEYCODE_SOFT_LEFT, t)); idle()   // its UP lands in the dialog
        assertTrue(dlg.isShowing)
        dlg.dismiss()
    }

    @Test fun backStillLeavesAndDoesNotAdd() {
        val a = main(); ShadowSystemClock.advanceBy(Duration.ofMillis(50))
        press(a, KeyEvent.KEYCODE_BACK)
        assertNull(started(a)); assertTrue(a.isFinishing)
    }

    // ---- language escape ----
    private fun texts(v: View): List<String> = when (v) {
        is TextView -> listOf(v.text.toString())
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun languageRowIsBilingualFirstAndZeroResetsToEnglish() {
        val prefs = Prefs(ctx); prefs.hebrewText = 2
        val ctl = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val all = texts(ctl.get().findViewById(android.R.id.content))
        val firstRow = all.indexOfFirst { it == SettingsActivity.LANGUAGE_LABEL }
        assertTrue("bilingual label shown in Yiddish mode: $all", firstRow >= 0)
        assertTrue("value is bilingual", all.contains("Yiddish · אידיש"))
        assertTrue("header keeps the English word", all.any { it.contains("Settings") })
        // it is the first row: only the header and the section title come before it
        assertTrue("first row: $all", all.take(firstRow).none { it in listOf("Dark theme", "In Israel", "Show parsha") })
        ShadowSystemClock.advanceBy(Duration.ofMillis(50))
        press(ctl.get(), KeyEvent.KEYCODE_0)
        assertEquals(0, Prefs(ctx).hebrewText)
        for (level in 0..1) { prefs.hebrewText = level
            val t = texts(Robolectric.buildActivity(SettingsActivity::class.java).setup().get().findViewById(android.R.id.content))
            assertTrue(t.contains(SettingsActivity.LANGUAGE_LABEL)) }
    }

    @Test fun menuSettingsItemKeepsEnglishInYiddishMode() {
        Prefs(ctx).hebrewText = 2
        val a = main()
        assertEquals("הגדרות · Settings", a.settingsLabel())
        Prefs(ctx).hebrewText = 0
        assertEquals("Settings", main().settingsLabel())
    }

    @Test fun aboutMentionsLanguageEscapeAndKeyTest() {
        val a = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        val all = texts(a.findViewById(android.R.id.content)).joinToString("\n")
        assertTrue(all, all.contains("press 0 for English"))
        assertTrue(all, all.contains("Key test"))
        ShadowSystemClock.advanceBy(Duration.ofMillis(50))
        press(a, KeyEvent.KEYCODE_SOFT_LEFT)
        assertEquals(KeyTestActivity::class.java.name, started(a))
    }

    @Test fun keyTestShowsCodesAndNeverActs() {
        val a = Robolectric.buildActivity(KeyTestActivity::class.java).setup().get()
        press(a, KeyEvent.KEYCODE_F2)
        val all = texts(a.findViewById(android.R.id.content)).joinToString("\n")
        assertTrue(all, all.contains("F2  132  down"))
        assertNull(started(a)); assertFalse(a.isFinishing)
        press(a, KeyEvent.KEYCODE_F2); press(a, KeyEvent.KEYCODE_F2)
        assertTrue("same key 3x leaves", a.isFinishing)
    }

    // ---- LG Exalt (Android 6.0.1 = API 23) ----
    @Test fun minSdkCoversLgExaltAndLintHasNoNewApi() {
        assertTrue("minSdk ${ctx.applicationInfo.minSdkVersion}", ctx.applicationInfo.minSdkVersion <= 23)
        assertEquals(9, BuildConfig.VERSION_CODE); assertEquals("0.2.4", BuildConfig.VERSION_NAME)
        for (f in listOf("build/reports/lint-results-debug.xml", "../calendarlib/build/reports/lint-results-debug.xml")) {
            val x = File(f)
            if (x.exists()) assertFalse("$f has NewApi", x.readText().contains("id=\"NewApi\""))
        }
    }
}

/** Launch every screen on old Android (Robolectric framework jars for API 21 and 23 = LG Exalt). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21, 23], qualifiers = "w240dp-h400dp-port-mdpi")
class LegacyApiSmokeTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun prefs() {
        ctx.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE).edit().clear().putBoolean("asked_perms", false).commit()
    }
    @Test fun everyScreenStartsAndDatesWork() {
        val m = Robolectric.buildActivity(MainActivity::class.java).setup().get()   // also asks calendar permission
        assertFalse(m.isFinishing)
        val l = Luach(ctx, Prefs(ctx))
        assertEquals("פסח", l.cellLabel(LocalDate.of(2027, 4, 22)))   // java.time + KosherJava
        for (c in listOf(SettingsActivity::class.java, AboutActivity::class.java, EditEventActivity::class.java, KeyTestActivity::class.java))
            assertNotNull(Robolectric.buildActivity(c).setup().get())
        assertNotNull(Robolectric.buildActivity(DayActivity::class.java,
            Intent(ctx, DayActivity::class.java).putExtra(DayActivity.EXTRA_DATE, LocalDate.of(2027, 1, 23).toEpochDay())).setup().get())
        Prefs(ctx).hebrewText = 2
        assertNotNull(Robolectric.buildActivity(SettingsActivity::class.java).setup().get())
    }
}
