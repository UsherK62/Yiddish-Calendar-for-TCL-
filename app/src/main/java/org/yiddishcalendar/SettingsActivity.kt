package org.yiddishcalendar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Settings, in three rounded section cards that match the calendar tiles.
 *  Display:  Hebrew text (pick list: Less/More/Everything), Dark theme [ON/OFF], Right-to-left [ON/OFF]
 *  Calendar: In Israel, Date changes at night, Show parsha   (toggles)
 *  Other:    Save events to (pick list), About (opens a page)
 * Two-choice settings are toggles; only lists with 3+ options open a pick list.
 * D-pad up/down moves one row at a time and keeps the focused row on screen. No Left/Right cycling.
 */
class SettingsActivity : BaseActivity() {
    private val rowList = mutableListOf<SettingRow>()
    private lateinit var scroll: ScrollView
    private var calendars: List<EventRepository.Cal> = emptyList()
    private var focusIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        setHeader(settingsLabel())
        setSoftKeys("", getString(R.string.sk_select), getString(R.string.sk_back))
        findViewById<TextView>(R.id.hint).visibility = View.GONE
        scroll = findViewById(R.id.scroll)
        scroll.isFocusable = false
        calendars = events.writableCalendars()
        val rows = findViewById<LinearLayout>(R.id.rows)
        rows.setPadding(0, dp(2f), 0, dp(10f))

        section(rows, "Display", first = true) { card ->
            // Language escape: this row is always the first row and its label/values are ALWAYS in both
            // English and Yiddish, so someone who switched to Yiddish by mistake can find the way back.
            choice(card, LANGUAGE_LABEL, LANGUAGE_LEVELS, { prefs.hebrewText }, HEBREW_LEVEL_NOTES) { prefs.hebrewText = it; relabel() }
            toggle(card, "Dark theme", { prefs.theme == 0 }) { prefs.theme = if (prefs.theme == 0) 1 else 0; relabel() }
            toggle(card, "Right-to-left", { prefs.rtlGrid }) { prefs.rtlGrid = !prefs.rtlGrid; relabel() }
        }

        section(rows, "Calendar") { card ->
            // Same as the # key on the month screen (touch-only phones have no # key). ON = Hebrew (Yiddish) months.
            toggle(card, "Hebrew months", { prefs.hebrewMonths }, note = "Off: Gregorian months") { prefs.hebrewMonths = !prefs.hebrewMonths }
            toggle(card, "In Israel", { prefs.inIsrael }) { prefs.inIsrael = !prefs.inIsrael }
            toggle(
                card, "Date at nightfall", { prefs.nightfall },
                note = "Brooklyn or Jerusalem",
            ) { prefs.nightfall = !prefs.nightfall }
            toggle(card, "Show parsha", { prefs.showParsha }) { prefs.showParsha = !prefs.showParsha }
        }

        section(rows, "Other") { card ->
            add(card, SettingRow(this, "Save events to", SettingRow.Kind.CHOICE, { events.targetLabel() }, onOk = { pickTarget() }))
            add(card, SettingRow(this, "About", SettingRow.Kind.ACTION, { "" }, onOk = { about() }))
        }

        focusIndex = savedInstanceState?.getInt(KEY_FOCUS, 0)?.coerceIn(0, rowList.lastIndex) ?: 0
        rowList[focusIndex].view.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_FOCUS, focusIndex)
    }

    private fun section(parent: LinearLayout, title: String, first: Boolean = false, fill: (LinearLayout) -> Unit) {
        parent.addView(sectionHeader(this, title, first))
        val card = sectionCard(this)
        fill(card)
        parent.addView(card)
    }

    private fun add(parent: LinearLayout, row: SettingRow) {
        row.onFocus = { has ->
            if (has) {
                focusIndex = rowList.indexOf(row).coerceAtLeast(0)
                setSoftKeys("", getString(when (row.kind) {
                    SettingRow.Kind.TOGGLE -> R.string.sk_change
                    SettingRow.Kind.CHOICE -> R.string.sk_select
                    SettingRow.Kind.ACTION -> R.string.sk_open
                }), getString(R.string.sk_back))
                FocusNav.ensureVisible(scroll, row.view, dp(24f))
            }
        }
        rowList += row
        parent.addView(row.view)
    }

    private fun toggle(parent: LinearLayout, title: String, on: () -> Boolean, note: String = "", flip: () -> Unit) =
        add(parent, SettingRow(this, title, SettingRow.Kind.TOGGLE, { "" }, on, note, onOk = { flip(); changed() }))

    private fun choice(
        parent: LinearLayout, title: String, options: List<String>, current: () -> Int,
        notes: List<String> = emptyList(), set: (Int) -> Unit,
    ) =
        add(parent, SettingRow(this, title, SettingRow.Kind.CHOICE, { options[current().coerceIn(0, options.size - 1)] }, onOk = {
            KeyDialog(this).title(title).choices(options, current(), notes) { i ->
                if (i != current()) { set(i); changed() }
            }.show()
        }))

    private fun changed() { prefs.bump(); rowList.forEach { it.refresh() } }

    /** Hebrew-text level, theme or direction changed: words, colours or layout change, so rebuild. */
    private fun relabel() { prefs.bump(); recreate() }

    /** Pick list of writable phone calendars, plus Automatic and This phone only. */
    private fun pickTarget() {
        if (!events.hasCalendarPermission()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), REQ_CAL)
            return
        }
        calendars = events.writableCalendars()
        val ids = listOf(-1L) + calendars.map { it.id } + listOf(-2L)
        val names = listOf("Automatic") + calendars.map { it.name } + listOf("This phone only")
        KeyDialog(this).title("Save events to").choices(names, ids.indexOf(prefs.saveCalendarId).coerceAtLeast(0)) { i ->
            prefs.saveCalendarId = ids[i]; changed()
        }.show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val delta = if (code == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1
                val next = (focusIndex + delta).coerceIn(0, rowList.lastIndex)
                if (next != focusIndex) rowList[next].view.requestFocus()
            }
            return true
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) return true
        // Escape hatch: 0 in Settings switches the language back to English.
        if (code == KeyEvent.KEYCODE_0) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && prefs.hebrewText != 0) {
                prefs.hebrewText = 0
                Toast.makeText(this, "Language: English · שפראך: ענגליש", Toast.LENGTH_SHORT).show()
                relabel()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ok = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (requestCode == REQ_CAL && ok) { calendars = events.writableCalendars(); pickTarget() }
        changed()
    }

    companion object {
        private const val REQ_CAL = 12
        private const val KEY_FOCUS = "focus"
        const val LANGUAGE_LABEL = "Language · שפראך"
        val LANGUAGE_LEVELS = listOf("English · ענגליש", "English + Hebrew names · מער עברית", "Yiddish · אידיש")
        val HEBREW_LEVEL_NOTES = listOf("Shabbos names + date", "+ holiday names", "all text")
    }
}
