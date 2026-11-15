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
 * New event form: Title, Date, Time, Repeat, Reminder. Every value changes with ◀ ▶ (no clock dial,
 * no dropdowns). Left soft = Save, Right soft = Back.
 */
class EditEventActivity : BaseActivity() {
    companion object { const val EXTRA_DATE = "date" }

    private lateinit var title: EditText
    private lateinit var hint: TextView
    private var date: LocalDate = LocalDate.now()
    private var minutes: Int? = null // null = all day
    private var repeat = Repeat.NEVER
    private var reminder: Int? = 15
    private val reminders = listOf<Int?>(null, 0, 5, 15, 30, 60, 120, 1440)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        date = LocalDate.ofEpochDay(savedInstanceState?.getLong(EXTRA_DATE) ?: intent.getLongExtra(EXTRA_DATE, LocalDate.now().toEpochDay()))
        savedInstanceState?.let {
            minutes = it.getInt("minutes", -1).takeIf { m -> m >= 0 }
            repeat = Repeat.entries[it.getInt("repeat", 0).coerceIn(0, Repeat.entries.size - 1)]
            reminder = it.getInt("reminder", -1).takeIf { m -> m >= 0 }
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
            setBackgroundResource(R.drawable.row_bg)
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

        addRow(card, ChoiceRow(this, "Date",
            value = { luach.mixed(luach.fullHebrewDate(date).substringBeforeLast(' '), luach.gregShort(date)) },
            prev = { date = date.minusDays(1) }, next = { date = date.plusDays(1) },
            hint = "◀ ▶ day · 1/3 month",
            extraKeys = { k ->
                when (k) {
                    KeyEvent.KEYCODE_1 -> { date = date.minusMonths(1); true }
                    KeyEvent.KEYCODE_3 -> { date = date.plusMonths(1); true }
                    else -> false
                }
            }))
        addRow(card, ChoiceRow(this, "Time",
            value = { timeLabel(minutes) },
            prev = { minutes = minutes?.let { if (it - 15 < 0) null else it - 15 } },
            next = { minutes = minutes?.let { (it + 15).coerceAtMost(23 * 60 + 45) } ?: (9 * 60) },
            hint = "◀ ▶ 15 min · 1/3 hour · * AM/PM · 0 all day",
            extraKeys = { k ->
                val m = minutes ?: (9 * 60)
                when (k) {
                    KeyEvent.KEYCODE_1 -> { minutes = (m - 60).coerceAtLeast(0); true }
                    KeyEvent.KEYCODE_3 -> { minutes = (m + 60).coerceAtMost(23 * 60 + 45); true }
                    KeyEvent.KEYCODE_STAR -> { minutes = (m + 12 * 60) % (24 * 60); true }
                    KeyEvent.KEYCODE_0 -> { minutes = null; true }
                    else -> false
                }
            }))
        addRow(card, ChoiceRow(this, "Repeat",
            value = { repeat.label },
            prev = { repeat = Repeat.entries.toList().after(repeat, -1) },
            next = { repeat = Repeat.entries.toList().after(repeat, 1) }))
        addRow(card, ChoiceRow(this, "Reminder",
            value = { reminderLabel(reminder) },
            prev = { reminder = reminders.after(reminder, -1) },
            next = { reminder = reminders.after(reminder, 1) }))

        setSoftKeys(getString(R.string.sk_save), getString(R.string.sk_edit), getString(R.string.sk_back))
        title.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(EXTRA_DATE, date.toEpochDay())
        outState.putInt("minutes", minutes ?: -1)
        outState.putInt("repeat", repeat.ordinal)
        outState.putInt("reminder", reminder ?: -1)
        outState.putString("title", title.text.toString())
    }

    private fun addRow(parent: LinearLayout, row: ChoiceRow) {
        parent.addView(row.view)
        val scroll = findViewById<ScrollView>(R.id.scroll)
        row.onFocus = { has ->
            if (has) {
                onRowFocus(row.hint, getString(R.string.sk_change))
                FocusNav.ensureVisible(scroll, row.view, dp(24f))
            }
        }
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
        runCatching { events.add(t, date, minutes, repeat, reminder) }
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
