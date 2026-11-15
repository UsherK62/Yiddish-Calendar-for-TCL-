package org.yiddishcalendar

import android.Manifest
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.data.MonthPageSource
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Month grid. The activity owns ONE selected day and handles every D-pad / number key itself;
 * day cells are never focusable, so focus can't get lost in the RecyclerView or jump to a toolbar.
 *
 * Keys: arrows move by day (left/right) or week (up/down) and cross month borders.
 *   OK/5 = open day   4/6 = prev/next month   1/3 = prev/next year   0 = today
 *   * = go to date    # = switch Hebrew/Gregorian months
 *   Left soft (or MENU) = Menu   Right soft = Add event
 */
class MainActivity : BaseActivity() {
    private lateinit var cv: CalendarView
    private lateinit var pages: MonthPages
    private var selected: LocalDate = LocalDate.now()
    private var today: LocalDate = LocalDate.now()
    private var currentOffset = 0
    private var eventDays: Set<LocalDate> = emptySet()
    private var seenRevision = -1
    /** True when the visible page has 6 week rows: cells are ~29dp tall, so text steps down one size. */
    private var compact = false
    private val clockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() { refreshToday(); clockHandler.postDelayed(this, 60_000) }
    }

    private fun refreshToday() {
        val next = luach.today()
        if (next != today) {
            if (selected == today) selected = next
            today = next
            setupCalendar()
        }
    }

    override fun onPause() { clockHandler.removeCallbacks(clockTick); super.onPause() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cv = findViewById(R.id.calendar)
        setSoftKeys(getString(R.string.sk_menu), getString(R.string.sk_open), getString(R.string.sk_add))
        today = luach.today()
        selected = savedInstanceState?.getLong("sel")?.let { LocalDate.ofEpochDay(it) } ?: today
        cv.dayBinder = DayBinder()
        cv.itemAnimator = null // instant page changes (slow MT6739 CPU)
        setupCalendar()
        seenRevision = prefs.revision
        askPermissionsOnce()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("sel", selected.toEpochDay())
    }

    override fun onResume() {
        super.onResume()
        clockHandler.removeCallbacks(clockTick)
        clockHandler.postDelayed(clockTick, 60_000)
        val newToday = luach.today()
        val modeChanged = ::pages.isInitialized && pages.hebrew != prefs.hebrewMonths
        if (modeChanged || newToday != today) {
            if (selected == today) selected = newToday
            today = newToday
            setupCalendar()
        } else {
            if (seenRevision != prefs.revision) fillWeekdays()
            loadEventDots()
            cv.notifyCalendarChanged()
            updateTexts()
        }
        seenRevision = prefs.revision
    }

    private fun setupCalendar() {
        pages = MonthPages(prefs.hebrewMonths, today)
        // Must be set before setup(): the adapter reads it when building pages.
        MonthPageSource.provider = if (pages.hebrew) pages else null
        if (!pages.contains(selected)) selected = today
        currentOffset = pages.offsetOf(selected)
        updateCompact()
        cv.setup(pages.startKey, pages.endKey, DayOfWeek.SUNDAY)
        cv.scrollToMonth(pages.keyOf(currentOffset))
        fillWeekdays()
        loadEventDots()
        updateTexts()
    }

    /** Mirrors the weekday row and the grid (week rows inherit the direction) when right-to-left is on. */
    private fun applyDirection() {
        val dir = if (prefs.rtlGrid) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        findViewById<View>(R.id.weekdays).layoutDirection = dir
        // Top bar too: Hebrew month title on the right, Gregorian range on the left in right-to-left mode.
        findViewById<View>(R.id.header).layoutDirection = dir
        cv.layoutDirection = dir
    }

    private fun fillWeekdays() {
        applyDirection()
        val row = findViewById<LinearLayout>(R.id.weekdays)
        row.removeAllViews()
        luach.weekdayLabels().forEachIndexed { i, label ->
            row.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textDirection = View.TEXT_DIRECTION_FIRST_STRONG
                textSize = 10f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (i == 6) palette.text else palette.sub)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            })
        }
    }

    private fun loadEventDots() {
        val (first, len) = pages.range(currentOffset)
        eventDays = runCatching { events.eventsBetween(first.minusDays(7), first.plusDays(len + 7L)).keys }.getOrDefault(emptySet())
    }

    private fun updateTexts() {
        val (first, len) = pages.range(currentOffset)
        if (!pages.hebrew && prefs.rtlGrid) {
            setHeader(luach.pageSubtitle(first, len, false), luach.pageSubtitle(first, len, true))
        } else {
            setHeader(luach.pageTitle(first, pages.hebrew), luach.pageSubtitle(first, len, pages.hebrew))
        }
        findViewById<TextView>(R.id.statusTitle).text = luach.dayTitle(selected)
        findViewById<TextView>(R.id.statusSub).text = luach.daySubtitle(selected)
    }

    private fun updateCompact() {
        val (first, len) = pages.range(currentOffset)
        val lead = first.dayOfWeek.value % 7 // Sunday-first grid
        compact = (lead + len + 6) / 7 >= 6
    }

    /** Moves the selection; switches page when the date is in another month. */
    private fun moveTo(date: LocalDate) {
        if (!pages.contains(date)) {
            toast("End of calendar")
            return
        }
        selected = date
        val newOffset = pages.offsetOf(date)
        if (newOffset != currentOffset) {
            val oldKey = pages.keyOf(currentOffset)
            currentOffset = newOffset
            updateCompact()
            cv.scrollToMonth(pages.keyOf(newOffset)) // jump, no animation: reliable and fast
            loadEventDots()
            cv.notifyMonthChanged(oldKey) // clear old highlight in the cached page
        }
        cv.notifyMonthChanged(pages.keyOf(currentOffset))
        updateTexts()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (isGridKey(code)) {
            if (event.action == KeyEvent.ACTION_DOWN) handleGridKey(code, event.repeatCount)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isGridKey(code: Int) = code in setOf(
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5,
        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_POUND,
    ) || SoftKeys.isCenter(code)

    private fun handleGridKey(code: Int, repeat: Int) {
        when (code) {
            // Right-to-left grid (default): the next day is to the LEFT, so the keys follow what is seen.
            KeyEvent.KEYCODE_DPAD_LEFT -> moveTo(selected.plusDays(if (prefs.rtlGrid) 1 else -1))
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveTo(selected.plusDays(if (prefs.rtlGrid) -1 else 1))
            KeyEvent.KEYCODE_DPAD_UP -> moveTo(selected.minusDays(7))
            KeyEvent.KEYCODE_DPAD_DOWN -> moveTo(selected.plusDays(7))
            // 4 is the left key, 6 the right one: same direction rule as the D-pad.
            KeyEvent.KEYCODE_4 -> moveTo(pages.addMonths(selected, if (prefs.rtlGrid) 1 else -1))
            KeyEvent.KEYCODE_6 -> moveTo(pages.addMonths(selected, if (prefs.rtlGrid) -1 else 1))
            KeyEvent.KEYCODE_1 -> moveTo(pages.addYears(selected, -1))
            KeyEvent.KEYCODE_3 -> moveTo(pages.addYears(selected, 1))
            KeyEvent.KEYCODE_0 -> if (repeat == 0) moveTo(today)
            KeyEvent.KEYCODE_STAR -> if (repeat == 0) goToDate()
            KeyEvent.KEYCODE_POUND -> if (repeat == 0) {
                prefs.hebrewMonths = !prefs.hebrewMonths
                prefs.bump()
                setupCalendar()
                toast(if (prefs.hebrewMonths) "Hebrew months" else "Gregorian months")
            }
            else -> if (repeat == 0) openDay(selected) // OK / Enter / 5
        }
    }

    private fun openDay(date: LocalDate) =
        startActivity(Intent(this, DayActivity::class.java).putExtra(DayActivity.EXTRA_DATE, date.toEpochDay()))

    override fun onRightSoft() =
        startActivity(Intent(this, EditEventActivity::class.java).putExtra(EditEventActivity.EXTRA_DATE, selected.toEpochDay()))

    override fun onBackKey() { finish() }

    override fun onLeftSoft() {
        KeyDialog(this).title(getString(R.string.sk_menu))
            .item("Today") { moveTo(today) }
            .item("Go to date") { goToDate() }
            .item("Add event") { onRightSoft() }
            .item(getString(R.string.settings)) { startActivity(Intent(this, SettingsActivity::class.java)) }
            .item("About") { about() }
            .show()
    }

    private fun goToDate() {
        KeyDialog(this).title("Go to date")
            .numberInput("MMDDYYYY") { s ->
                val date = parseTypedDate(s)
                if (date == null) toast("Not a date") else moveTo(date)
            }
            .preview { s ->
                val date = parseTypedDate(s)
                if (s.isBlank()) "Type month, day, year (MMDDYYYY)"
                else if (date == null) "…"
                else luach.mixed(luach.fullHebrewDate(date).substringBeforeLast(' '), luach.gregShort(date))
            }
            .show()
    }

    private fun parseTypedDate(s: String): LocalDate? {
        val d = s.filter { it.isDigit() }
        return runCatching {
            when (d.length) {
                8 -> LocalDate.of(d.substring(4).toInt(), d.substring(0, 2).toInt(), d.substring(2, 4).toInt())
                4 -> LocalDate.of(selected.year, d.substring(0, 2).toInt(), d.substring(2, 4).toInt())
                else -> null
            }
        }.getOrNull()
    }

    private fun askPermissionsOnce() {
        if (prefs.askedPermissions || events.hasCalendarPermission()) return
        prefs.askedPermissions = true
        requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadEventDots()
        cv.notifyCalendarChanged()
    }

    // ---------------- Day cells ----------------

    inner class DayContainer(view: View) : ViewContainer(view) {
        val box: FrameLayout = view.findViewById(R.id.cellBox)
        val greg: TextView = view.findViewById(R.id.cellGreg)
        val heb: TextView = view.findViewById(R.id.cellHeb)
        val mark: TextView = view.findViewById(R.id.cellMark)
        val dot: View = view.findViewById(R.id.cellEventDot)
    }

    inner class DayBinder : MonthDayBinder<DayContainer> {
        override fun create(view: View) = DayContainer(view)

        override fun bind(container: DayContainer, data: CalendarDay) {
            val date = data.date
            val inMonth = data.position == DayPosition.MonthDate
            val shabbos = date.dayOfWeek == DayOfWeek.SATURDAY
            val isToday = date == today
            val isSelected = inMonth && date == selected
            val p = palette

            container.box.applyUiDir(prefs.rtlGrid)
            container.box.background = GradientDrawable().apply {
                cornerRadius = dp(6f).toFloat()
                setColor(
                    when {
                        isToday -> p.today
                        shabbos || (inMonth && luach.isSpecialDay(date)) -> p.cellShabbos
                        else -> p.cell
                    },
                )
                if (isSelected) setStroke(dp(3f), p.focus)
            }
            val main = if (inMonth) p.text else p.dim
            container.greg.text = date.dayOfMonth.toString()
            container.greg.setTextColor(if (inMonth) p.sub else p.dim)
            val num = luach.cellNumber(date)
            container.heb.text = num
            container.heb.textSize = 14f
            container.greg.textSize = 9f
            container.heb.setTextColor(main)
            val markText = luach.cellMark(date)
            // Never put full holiday/parsha names into a ~31dp cell. They are in the footer/day screen.
            // Five-row pages can show the short Hebrew Rosh Chodesh label; six-row pages use an indicator.
            val shortLabel = !compact && luach.jc(date).isRoshChodesh
            container.mark.text = if (shortLabel) "ר״ח" else ""
            container.mark.textSize = if (shortLabel) 11f else 10f
            container.mark.setTextColor(main)
            container.mark.background = if (!shortLabel && markText != null) {
                android.graphics.drawable.LayerDrawable(arrayOf(GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(main)
                })).apply { setLayerSize(0, dp(3f), dp(3f)); setLayerGravity(0, Gravity.CENTER) }
            } else null
            container.mark.visibility = if (markText == null && !shortLabel) View.GONE else View.VISIBLE
            container.mark.layoutParams = container.mark.layoutParams.apply { height = dp(if (shortLabel) 14f else 5f) }
            container.box.contentDescription = luach.fullHebrewDate(date) + ", " + luach.dayTitle(date)

            container.dot.visibility = if (date in eventDays) View.VISIBLE else View.GONE
            container.dot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(main) }
        }
    }
}
