package org.yiddishcalendar

import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate

/**
 * New event form: Title, Date, Days, Time, Repeat, Reminder. No arrows: OK or a tap on a row opens a
 * small pop-up list (no clock dial). Left soft = Save, Right soft = Back.
 */
class EditEventActivity : BaseActivity() {
    companion object {
        const val EXTRA_DATE = "date"
        const val MAX_DAYS = 30
        val DAY_CHOICES = (1..14).toList() + listOf(21, 30)
    }

    private lateinit var title: EditText
    private lateinit var hint: TextView
    private var date: LocalDate = LocalDate.now()
    private var minutes: Int? = null // null = all day
    private var repeat = Repeat.NEVER
    private var reminder: Int? = 15
    /** How many days the event lasts (1 = a normal one-day event). */
    private var days = 1
    private val pickRows = mutableListOf<PickRow>()
    private val reminders = listOf<Int?>(null, 0, 5, 15, 30, 60, 120, 1440)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        date = LocalDate.ofEpochDay(savedInstanceState?.getLong(EXTRA_DATE) ?: intent.getLongExtra(EXTRA_DATE, LocalDate.now().toEpochDay()))
        savedInstanceState?.let {
            minutes = it.getInt("minutes", -1).takeIf { m -> m >= 0 }
            repeat = Repeat.entries[it.getInt("repeat", 0).coerceIn(0, Repeat.entries.size - 1)]
            reminder = it.getInt("reminder", -1).takeIf { m -> m >= 0 }
            days = it.getInt("days", 1).coerceIn(1, MAX_DAYS)
        }
        setHeader(getString(R.string.new_event))
        hint = findViewById(R.id.hint)
        val scroll = findViewById<ScrollView>(R.id.scroll)
        scroll.isFocusable = false
        val rows = findViewById<LinearLayout>(R.id.rows)
        rows.setPadding(0, dp(6f), 0, dp(8f))
        val card = sectionCard(this)
        rows.addView(card)

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = false
            setBackgroundResource(R.drawable.row_bg_ripple)
            setPadding(dp(12f), dp(7f), dp(12f), dp(7f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.setMargins(dp(4f), dp(3f), dp(4f), 0) }
        }
        val titleLabel = AppCompatTextView(this).apply {
            text = tr("Title"); textSize = 10f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            isAllCaps = true; setTextColor(palette.sub); applyBidi()
        }
        title = AppCompatEditText(this).apply {
            hint = tr("Type a title")
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text); setHintTextColor(palette.dim)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_NEXT
            background = null
            setPadding(0, dp(2f), 0, 0)
            applyBidi()
            setOnFocusChangeListener { _, has ->
                titleBlock.isActivated = has
                if (has) {
                    onRowFocus("Type with the keypad", getString(R.string.sk_edit))
                    FocusNav.ensureVisible(scroll, titleBlock, dp(24f))
                }
            }
        }
        titleBlock.addView(titleLabel)
        titleBlock.addView(title)
        title.setText(savedInstanceState?.getString("title").orEmpty())
        card.addView(titleBlock)

        // No arrows: each row shows its value; OK or a tap opens a pop-up list sized to its contents.
        addRow(card, PickRow(this, "Date", value = { dateLabel(date) }) { pickDate() })
        addRow(card, PickRow(this, "Days", value = { daysLabel(days, withEnd = days > 1) }) { pickDays() })
        addRow(card, PickRow(this, "Time", value = { timeLabel(minutes) }) { pickHour() })
        addRow(card, PickRow(this, "Repeat", value = { repeat.label }) { pickRepeat() })
        addRow(card, PickRow(this, "Reminder", value = { reminderLabel(reminder) }) { pickReminder() })

        setSoftKeys(getString(R.string.sk_save), getString(R.string.sk_edit), getString(R.string.sk_back))
        title.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(EXTRA_DATE, date.toEpochDay())
        outState.putInt("minutes", minutes ?: -1)
        outState.putInt("repeat", repeat.ordinal)
        outState.putInt("reminder", reminder ?: -1)
        outState.putInt("days", days)
        outState.putString("title", title.text.toString())
    }

    private fun addRow(parent: LinearLayout, row: PickRow) {
        parent.addView(row.view)
        pickRows += row
        val scroll = findViewById<ScrollView>(R.id.scroll)
        row.onFocus = { has ->
            if (has) {
                onRowFocus(row.hint, getString(R.string.sk_change))
                FocusNav.ensureVisible(scroll, row.view, dp(24f))
            }
        }
    }

    private fun refreshRows() = pickRows.forEach { it.refresh() }

    // ---------- pop-ups (D-pad: up/down + OK or the item's number; touch: tap an item; Back cancels) ----------

    private fun dateLabel(d: LocalDate) = luach.mixed(luach.fullHebrewDate(d).substringBeforeLast(' '), luach.gregShort(d))

    fun daysLabel(n: Int, withEnd: Boolean, short: Boolean = false): String {
        val count = if (prefs.hebrewText >= 2) (if (n == 1) "1 טאג" else "$n טעג") else (if (n == 1) "1 day" else "$n days")
        if (!withEnd) return count
        val until = if (prefs.hebrewText >= 2) "ביז" else "to"
        val last = date.plusDays(n - 1L)
        // Short in the pop-up so each choice fits on one line; with the weekday on the row.
        val end = if (short) last.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US)) else luach.gregShort(last)
        return "$count · $until $end"
    }

    /** Dates two weeks back to two months ahead; left soft key "Type" for any other date (MMDDYYYY). */
    private fun pickDate() {
        val first = date.minusDays(14)
        val list = (0 until 75).map { first.plusDays(it.toLong()) }
        KeyDialog(this).title("Date")
            .choices(list.map { dateLabel(it) }, list.indexOf(date)) { i -> date = list[i]; refreshRows() }
            .left("Type") { typeDate() }
            .show()
    }

    private fun typeDate() {
        KeyDialog(this).title("Date")
            .numberInput("MMDDYYYY") { s -> parseDate(s)?.let { date = it; refreshRows() } ?: toast("Not a date") }
            .preview { s -> parseDate(s)?.let { dateLabel(it) } ?: if (s.isBlank()) "Type month, day, year (MMDDYYYY)" else "…" }
            .show()
    }

    private fun parseDate(s: String): LocalDate? {
        val d = s.filter { it.isDigit() }
        return runCatching {
            when (d.length) {
                8 -> LocalDate.of(d.substring(4).toInt(), d.substring(0, 2).toInt(), d.substring(2, 4).toInt())
                4 -> LocalDate.of(date.year, d.substring(0, 2).toInt(), d.substring(2, 4).toInt())
                else -> null
            }
        }.getOrNull()
    }

    private fun pickDays() {
        KeyDialog(this).title("Days")
            .choices(DAY_CHOICES.map { daysLabel(it, withEnd = it > 1, short = true) }, DAY_CHOICES.indexOf(days)) { i -> days = DAY_CHOICES[i]; refreshRows() }
            .show()
    }

    /** Step 1: All day, or the hour. Step 2 (for an hour): :00 / :15 / :30 / :45. */
    private fun pickHour() {
        val options = listOf(tr("All day")) + (0..23).map { hourLabel(it) }
        val current = minutes?.let { it / 60 + 1 } ?: 0
        KeyDialog(this).title("Time")
            .choices(options, current) { i -> if (i == 0) { minutes = null; refreshRows() } else pickMinutes(i - 1) }
            .show()
    }

    private fun pickMinutes(hour: Int) {
        val steps = listOf(0, 15, 30, 45)
        val current = minutes?.takeIf { it / 60 == hour }?.let { steps.indexOf(it % 60) } ?: 0
        KeyDialog(this).title(hourLabel(hour))
            .choices(steps.map { timeLabel(hour * 60 + it) }, current) { i -> minutes = hour * 60 + steps[i]; refreshRows() }
            .show()
    }

    private fun hourLabel(h: Int) = "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"

    private fun pickRepeat() {
        val all = Repeat.entries.toList()
        KeyDialog(this).title("Repeat")
            .choices(all.map { it.label }, all.indexOf(repeat)) { i -> repeat = all[i]; refreshRows() }
            .show()
    }

    private fun pickReminder() {
        KeyDialog(this).title("Reminder")
            .choices(reminders.map { reminderLabel(it) }, reminders.indexOf(reminder)) { i -> reminder = reminders[i]; refreshRows() }
            .show()
    }

    override fun onBackKey() { onRightSoft() }

    private fun onRowFocus(h: String, center: String) {
        hint.text = h
        setSoftKeys(getString(R.string.sk_save), center, getString(R.string.sk_back))
    }

    private fun timeLabel(m: Int?): String {
        if (m == null) return tr("All day")
        val h = m / 60; val mm = m % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, mm, if (h < 12) "AM" else "PM")
    }

    private fun reminderLabel(r: Int?) = when (r) {
        null -> "None"
        0 -> "At start time"
        in 1..59 -> "$r minutes before"
        60 -> "1 hour before"
        1440 -> "1 day before"
        else -> "${r / 60} hours before"
    }

    override fun onLeftSoft() {
        val t = title.text.toString().trim()
        if (t.isEmpty()) {
            toast("Type a title first")
            title.requestFocus()
            return
        }
        runCatching { events.add(t, date, minutes, repeat, reminder, days) }
            .onSuccess { result ->
                val message = when (result) {
                    SaveResult.PHONE -> "Saved"
                    SaveResult.PHONE_WITHOUT_REMINDER -> "Saved, but the phone calendar could not set the reminder."
                    SaveResult.LOCAL -> "Saved on this phone only. No reminders."
                }
                toast(message, long = true)
                finish()
            }
            .onFailure { toast("Could not save: ${it.message}", long = true) }
    }

    override fun onRightSoft() {
        if (title.text.isNotBlank()) toast("Not saved")
        finish()
    }
}
