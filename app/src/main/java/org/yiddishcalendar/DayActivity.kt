package org.yiddishcalendar

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One day: dates, parsha / holiday, omer, daf yomi, and the day's events.
 * Up/Down move between events (OK opens one). Left soft = Menu, Right soft = Back.
 */
class DayActivity : BaseActivity() {
    companion object { const val EXTRA_DATE = "date" }

    private lateinit var date: LocalDate
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private val eventRows = mutableListOf<View>()
    private var eventIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day)
        date = LocalDate.ofEpochDay(savedInstanceState?.getLong(EXTRA_DATE) ?: intent.getLongExtra(EXTRA_DATE, LocalDate.now().toEpochDay()))
        content = findViewById(R.id.content)
        scroll = findViewById(R.id.scroll)
        scroll.isFocusable = false
        content.setPadding(0, dp(4f), 0, dp(8f))
        setSoftKeys(getString(R.string.sk_menu), getString(R.string.sk_open), getString(R.string.sk_back))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(EXTRA_DATE, date.toEpochDay())
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()
        val p = palette
        setSoftKeys(getString(R.string.sk_menu), "", getString(R.string.sk_back))
        val hebShort = luach.fullHebrewDate(date).substringBeforeLast(' ')
        val weekday = luach.weekdayName(date)
        // In RTL the title sits on the right: Hebrew date there, weekday on the left.
        if (prefs.rtlGrid) setHeader(hebShort, weekday) else setHeader("$weekday $hebShort", "")

        if (prefs.rtlGrid) content.addView(twoCol(luach.fullHebrewDate(date), luach.gregLong(date), p.sub, p.sub, 12f))
        else content.addView(twoCol(luach.gregLong(date), luach.fullHebrewDate(date), p.sub, p.sub, 12f))

        // Featured card: parsha / Yom Tov / Rosh Chodesh, else the weekday name.
        content.addView(TextView(this).apply {
            text = luach.dayTitle(date)
            textSize = 16f; setTextColor(p.text); setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(R.drawable.card_bg)
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            layoutParams = lp(top = 8f, side = 10f)
            applyBidi()
        })

        for ((k, v) in luach.dayFacts(date)) content.addView(factRow(k, v))

        content.addView(label(tr(getString(R.string.events))))
        eventRows.clear()
        eventIndex = 0
        val list = runCatching { events.eventsOn(date) }.getOrDefault(emptyList())
        if (list.isEmpty()) {
            content.addView(TextView(this).apply {
                text = tr(getString(R.string.no_events)); textSize = 13f; setTextColor(p.sub)
                setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
                isFocusable = false
                applyBidi()
            })
            scroll.isFocusable = true
            scroll.requestFocus()
        } else {
            setSoftKeys(getString(R.string.sk_menu), getString(R.string.sk_open), getString(R.string.sk_back))
            scroll.isFocusable = false
            for (e in list) {
                val row = eventRow(e)
                eventRows += row
                content.addView(row)
            }
            eventRows.first().requestFocus()
        }
    }

    private fun timeText(e: Ev): String {
        if (e.allDay) return tr("All day")
        return DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(Instant.ofEpochMilli(e.startMillis).atZone(ZoneId.systemDefault()))
    }

    private fun eventRow(e: Ev): View {
        val timeView: TextView
        val titleView: TextView
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            minimumHeight = dp(44f)
            setBackgroundResource(R.drawable.row_bg)
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.setMargins(dp(10f), dp(3f), dp(10f), 0) }
            // Title at start (right in RTL), time at end (left in RTL).
            titleView = TextView(this@DayActivity).apply {
                text = e.title; textSize = 15f; setTypeface(typeface, Typeface.BOLD)
                maxLines = 2; setTextColor(palette.text); applyBidi()
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            timeView = TextView(this@DayActivity).apply {
                text = timeText(e); textSize = 12f; setTextColor(palette.sub); applyBidi()
                layoutParams = LinearLayout.LayoutParams(dp(72f), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            addView(titleView); addView(timeView)
            setOnFocusChangeListener { _, has ->
                if (has) {
                    eventIndex = eventRows.indexOf(this).coerceAtLeast(0)
                    FocusNav.ensureVisible(scroll, this, dp(24f))
                }
            }
            setOnClickListener { showEvent(e) }
            setOnKeyListener { _, code, ev ->
                if (!SoftKeys.isCenter(code)) return@setOnKeyListener false
                if (ev.action == KeyEvent.ACTION_UP) showEvent(e)
                true
            }
        }
    }

    private fun showEvent(e: Ev) {
        val d = KeyDialog(this).title(timeText(e)).line(e.title)
        if (e.location.isNotEmpty()) d.line("Where", e.location)
        if (e.notes.isNotEmpty()) d.line(e.notes.take(200))
        d.line(if (e.local) "Saved on this phone only" else "In the phone calendar")
        if (e.deletable) d.left(getString(R.string.sk_delete)) { confirmDelete(e) }
        d.show()
    }

    private fun confirmDelete(e: Ev) {
        KeyDialog(this).title("Delete?").line(e.title).line("Repeating events are deleted completely.")
            .center(getString(R.string.sk_delete)) {
                runCatching { events.delete(e) }
                    .onSuccess { toast("Deleted") }
                    .onFailure { toast("Could not delete") }
                render()
            }.show()
    }

    override fun onLeftSoft() {
        KeyDialog(this).title(getString(R.string.sk_menu))
            .item("Add event") { addEvent() }
            .item(getString(R.string.settings)) { startActivity(Intent(this, SettingsActivity::class.java)) }
            .show()
    }

    private fun addEvent() =
        startActivity(Intent(this, EditEventActivity::class.java).putExtra(EditEventActivity.EXTRA_DATE, date.toEpochDay()))

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        // Left/Right = previous/next day (same screen, no need to go back to the grid).
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val forward = if (prefs.rtlGrid) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
                date = date.plusDays(if (code == forward) 1 else -1)
                render()
            }
            return true
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (eventRows.isEmpty()) {
                    scroll.smoothScrollBy(0, dp(if (code == KeyEvent.KEYCODE_DPAD_DOWN) 40f else -40f))
                } else {
                    val delta = if (code == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1
                    val next = (eventIndex + delta).coerceIn(0, eventRows.lastIndex)
                    eventRows[next].requestFocus()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // ---- small view helpers ----
    private fun lp(top: Float = 0f, side: Float = 10f) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .also { it.setMargins(dp(side), dp(top), dp(side), 0) }

    private fun label(t: String) = TextView(this, null, 0, R.style.YC_Label).apply {
        text = t; setPadding(dp(14f), dp(12f), dp(14f), dp(2f))
        isFocusable = false
        applyBidi()
    }

    private fun twoCol(a: String, b: String, ca: Int, cb: Int, size: Float, boldValue: Boolean = false) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = false
            setPadding(dp(14f), dp(3f), dp(14f), dp(3f))
            addView(TextView(this@DayActivity).apply {
                text = a; textSize = size; setTextColor(ca); applyBidi()
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@DayActivity).apply {
                text = b; textSize = size; setTextColor(cb); if (boldValue) setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END; applyBidi()
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

    /** Label on the left, value on the right, so English pairs such as "Daf Yomi / Bechoros 43" read naturally. */
    private fun factRow(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        isFocusable = false
        setPadding(dp(14f), dp(3f), dp(14f), dp(3f))
        addView(TextView(this@DayActivity).apply {
            text = label; textSize = 13f; setTextColor(palette.sub); applyBidi()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(this@DayActivity).apply {
            text = value; textSize = 13f; setTextColor(palette.text)
            gravity = Gravity.END; applyBidi()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
    }
}
