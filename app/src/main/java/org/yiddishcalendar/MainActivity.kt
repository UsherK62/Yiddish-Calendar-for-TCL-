package org.yiddishcalendar

import androidx.appcompat.widget.AppCompatTextView
import android.Manifest
import android.content.Intent
import android.graphics.Typeface
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
    companion object {
        /** Host screenshot tests only: start with this day selected (epoch day). */
        const val EXTRA_TEST_DATE = "test_date"
    }
    private lateinit var cv: CalendarView
    private lateinit var selectionRing: SelectionRingView
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
        selectionRing = findViewById(R.id.selectionRing)
        installSwipe()
        findViewById<View>(R.id.header).layoutParams.height = dp(36f)
        setSoftKeys(getString(R.string.sk_menu), getString(R.string.sk_open), getString(R.string.sk_add))
        today = luach.today()
        selected = savedInstanceState?.getLong("sel")?.let { LocalDate.ofEpochDay(it) }
            ?: intent?.getLongExtra(EXTRA_TEST_DATE, Long.MIN_VALUE)?.takeIf { it != Long.MIN_VALUE }?.let { LocalDate.ofEpochDay(it) }
            ?: today
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
        if (::selectionRing.isInitialized) { selectionRing.hide(); ringGliding = false }
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
            row.addView(AppCompatTextView(this).apply {
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
        val h = HeaderText.of(luach, first, len, pages.hebrew, prefs.rtlGrid)
        setHeader(h.start, h.end)
        val title = findViewById<TextView>(R.id.headerTitle)
        val right = findViewById<TextView>(R.id.headerRight)
        // The month being viewed is the big bold text; the other calendar's months are smaller.
        title.textSize = if (h.startIsMain) 19f else 12.5f
        title.typeface = Typeface.create(title.typeface, if (h.startIsMain) Typeface.BOLD else Typeface.NORMAL)
        right.textSize = if (h.startIsMain) 13f else 17f
        right.typeface = Typeface.create(right.typeface, if (h.startIsMain) Typeface.NORMAL else Typeface.BOLD)
        findViewById<TextView>(R.id.statusTitle).apply { text = luach.dayTitle(selected); applyBidi() }
        findViewById<TextView>(R.id.statusSub).apply { text = luach.daySubtitle(selected); applyBidi() }
    }

    private fun updateCompact() {
        val (first, len) = pages.range(currentOffset)
        val lead = first.dayOfWeek.value % 7 // Sunday-first grid
        compact = (lead + len + 6) / 7 >= 6
    }

    /** Moves the selection; switches page when the date is in another month.
     *  [glide] = D-pad day/week move in the same month: the highlight slides to the new cell.
     *  [pulseToday] = the user jumped to Today: today's cell glows once after it is on screen. */
    private fun moveTo(date: LocalDate, glide: Boolean = false, pulseToday: Boolean = false) {
        if (!pages.contains(date)) {
            toast("End of calendar")
            return
        }
        val from = selected
        selected = date
        val newOffset = pages.offsetOf(date)
        val monthChanged = newOffset != currentOffset
        if (monthChanged) {
            selectionRing.hide()
            ringGliding = false
            val oldKey = pages.keyOf(currentOffset)
            val oldOffset = currentOffset
            currentOffset = newOffset
            updateCompact()
            // Only the grid moves: one month away slides vertically (next month comes in from below,
            // previous from above), farther jumps (year, today, go to date) switch at once. The header,
            // weekday row and the info lines are separate views and just update in place below.
            if (kotlin.math.abs(newOffset - oldOffset) == 1) cv.smoothScrollToMonth(pages.keyOf(newOffset))
            else cv.scrollToMonth(pages.keyOf(newOffset))
            loadEventDots()
            cv.notifyMonthChanged(oldKey) // clear old highlight in the cached page
        }
        cv.notifyMonthChanged(pages.keyOf(currentOffset))
        updateTexts()
        if (glide && !monthChanged) glideSelection(from, date)
        else if (!monthChanged) selectionRing.hide()
        if (pulseToday) scheduleTodayPulse()
    }

    private var ringGliding = false
    private var pendingTodayPulse = false

    private fun findDayCell(date: LocalDate): DayCellView? {
        val epoch = date.toEpochDay()
        fun walk(v: android.view.View): DayCellView? {
            if (v is DayCellView && (v.tag as? Long) == epoch) return v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))?.let { return it }
            return null
        }
        return walk(cv)
    }

    private fun cellInHost(cell: DayCellView): IntArray {
        val host = findViewById<android.view.View>(R.id.calendarHost)
        val c = IntArray(2); val h = IntArray(2)
        cell.getLocationInWindow(c)
        host.getLocationInWindow(h)
        return intArrayOf(c[0] - h[0], c[1] - h[1], cell.width, cell.height)
    }

    /** Slide the highlight from [from] to [to] on the same month page (120–150 ms). */
    private fun glideSelection(from: LocalDate, to: LocalDate) {
        val destView = findDayCell(to)
        if (destView == null || !Motion.enabled(this) || from == to) {
            selectionRing.hide()
            ringGliding = false
            destView?.selectedRing = true
            destView?.invalidate()
            return
        }
        val dest = cellInHost(destView)
        destView.selectedRing = false
        destView.invalidate()
        findDayCell(from)?.let { it.selectedRing = false; it.invalidate() }
        selectionRing.ringColor = destView.ringColor
        if (!ringGliding) {
            val startView = findDayCell(from)
            if (startView != null) {
                val s = cellInHost(startView)
                selectionRing.place(s[0], s[1], s[2], s[3])
            } else {
                selectionRing.place(dest[0], dest[1], dest[2], dest[3])
            }
        }
        ringGliding = true
        selectionRing.glideTo(dest[0], dest[1], dest[2], dest[3], Motion.duration(this, Motion.FOCUS_MS)) {
            ringGliding = false
            selectionRing.hide()
            findDayCell(to)?.let { it.selectedRing = true; it.invalidate() }
        }
    }

    private fun scheduleTodayPulse() {
        pendingTodayPulse = true
        val tryPulse = Runnable {
            if (!pendingTodayPulse) return@Runnable
            val cell = findDayCell(today)
            if (cell != null && cell.width > 0) {
                pendingTodayPulse = false
                cell.pulseHighlight()
            }
        }
        cv.post(tryPulse)
        cv.postDelayed(tryPulse, 120)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (isGridKey(code)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (SoftKeys.isCenter(code) && event.repeatCount == 0) flashSoftKey(R.id.skCenter)
                handleGridKey(code, event.repeatCount)
            }
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
            KeyEvent.KEYCODE_DPAD_LEFT -> moveTo(selected.plusDays(if (prefs.rtlGrid) 1 else -1), glide = true)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveTo(selected.plusDays(if (prefs.rtlGrid) -1 else 1), glide = true)
            KeyEvent.KEYCODE_DPAD_UP -> moveTo(selected.minusDays(7), glide = true)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveTo(selected.plusDays(7), glide = true)
            // 4 is the left key, 6 the right one: same direction rule as the D-pad.
            KeyEvent.KEYCODE_4 -> moveTo(pages.addMonths(selected, if (prefs.rtlGrid) 1 else -1))
            KeyEvent.KEYCODE_6 -> moveTo(pages.addMonths(selected, if (prefs.rtlGrid) -1 else 1))
            KeyEvent.KEYCODE_1 -> moveTo(pages.addYears(selected, -1))
            KeyEvent.KEYCODE_3 -> moveTo(pages.addYears(selected, 1))
            KeyEvent.KEYCODE_0 -> if (repeat == 0) moveTo(today, pulseToday = true)
            KeyEvent.KEYCODE_STAR -> if (repeat == 0) goToDate()
            KeyEvent.KEYCODE_POUND -> if (repeat == 0) {
                prefs.applyHebrewMonths(!prefs.hebrewMonths)
                prefs.bump()
                applyUiDirection()
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
        KeyDialog(this) // no title strip: the numbered list is self-explanatory
            .anchorBottomLeft() // like a normal options menu: bottom-left, right above the Menu key
            .item("Today") { moveTo(today, pulseToday = true) }
            .item("Go to date") { goToDate() }
            .item("Add event") { onRightSoft() }
            .item(settingsLabel()) { startActivity(Intent(this, SettingsActivity::class.java)) }
            .item("Help") { startActivity(Intent(this, HelpActivity::class.java)) } // About stays in Settings
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
        androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadEventDots()
        cv.notifyCalendarChanged()
    }

    /**
     * Touch: a vertical drag moves the month grid with the finger; on release it snaps to the nearest
     * month (or the next/previous one if the flick is fast). The header, weekday row and status lines
     * stay put and update when the page settles (through moveTo(), the same path as keys 4/6).
     * Sideways drags are swallowed so the grid never pages sideways. Taps still reach the day cells.
     * D-pad and keys 4/6 keep the existing one-page vertical slide (they do not use this listener).
     */
    private fun installSwipe() {
        val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        val minFling = android.view.ViewConfiguration.get(this).scaledMinimumFlingVelocity
        cv.addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {
            var x0 = 0f; var y0 = 0f; var lastY = 0f
            var mode = 0 // 0 = undecided, 1 = vertical, 2 = sideways (ignored)
            var tracker: android.view.VelocityTracker? = null
            override fun onInterceptTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent): Boolean {
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        x0 = e.x; y0 = e.y; lastY = e.y; mode = 0
                        tracker?.recycle()
                        tracker = android.view.VelocityTracker.obtain().also { it.addMovement(e) }
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        tracker?.addMovement(e)
                        if (mode == 0 && kotlin.math.hypot(e.x - x0, e.y - y0) > slop) {
                            mode = if (kotlin.math.abs(e.y - y0) > kotlin.math.abs(e.x - x0)) 1 else 2
                            if (mode == 1) lastY = e.y
                        }
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> { }
                }
                // Intercept once the direction is known, so a clickable day cell cannot eat the drag.
                return mode != 0
            }
            override fun onTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent) {
                tracker?.addMovement(e)
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_MOVE -> if (mode == 1) {
                        val dy = e.y - lastY
                        lastY = e.y
                        rv.scrollBy(0, (-dy).toInt())
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (mode == 1) {
                            tracker?.computeCurrentVelocity(1000)
                            settleMonthPage(tracker?.yVelocity ?: 0f, minFling)
                        }
                        recycleTracker()
                        mode = 0
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        if (mode == 1) settleMonthPage(0f, minFling)
                        recycleTracker()
                        mode = 0
                    }
                }
            }
            private fun recycleTracker() { tracker?.recycle(); tracker = null }
        })
    }

    /** Snap the dragged grid to the nearest month, or to the next/previous month on a fast flick. */
    private fun settleMonthPage(vy: Float, minFling: Int) {
        val lm = cv.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        if (first == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
        val child = lm.findViewByPosition(first)
        val nearest = if (child != null && child.top < -child.height / 2) first + 1 else first
        val flicked = when {
            vy < -minFling -> currentOffset + 1
            vy > minFling -> currentOffset - 1
            else -> null
        }
        val target = (flicked ?: nearest).coerceIn(0, pages.lastOffset)
        if (target == currentOffset) {
            cv.smoothScrollToMonth(pages.keyOf(currentOffset))
        } else {
            moveTo(pages.addMonths(selected, target - currentOffset))
        }
    }

    // ---------------- Day cells ----------------

    inner class DayContainer(view: View) : ViewContainer(view) {
        val box: DayCellView = view.findViewById(R.id.cellBox)
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
            val v = container.box

            // In-month days are filled tiles; days of the next/previous month have no tile at all, so the
            // shown month stands out clearly while their numbers stay readable. The selection ring is
            // drawn by the cell itself, on top of the tap ripple.
            v.background = Motion.cellBackground(
                this@MainActivity,
                when {
                    isToday -> p.today
                    !inMonth -> 0
                    shabbos || luach.isSpecialDay(date) -> p.cellShabbos
                    else -> p.cell
                },
                dp(6f).toFloat(),
            )
            v.tag = date.toEpochDay()
            v.selectedRing = isSelected && !ringGliding
            v.ringColor = p.focus
            val cell = CellText.of(luach, date, pages.hebrew)
            v.main = cell.main
            v.small = cell.small
            // Always-visible short holiday label (no need to select the day to see it).
            v.label = if (inMonth) cell.label else null
            v.mainColor = if (inMonth) p.text else p.dim
            v.smallColor = if (inMonth) p.sub else p.dim
            v.labelColor = if (isToday) p.text else p.focus
            v.dotColor = p.focus
            v.eventDot = date in eventDays
            v.compact = compact
            v.rtl = prefs.rtlGrid
            v.contentDescription = luach.fullHebrewDate(date) + ", " + luach.dayTitle(date)
            // Touch: tap a day to select it, tap the selected day again to open it. Long-press opens
            // Add event for that day. Never focusable, so the D-pad (handled in dispatchKeyEvent) and
            // the single selection ring are unchanged. The Add soft key still uses the selected day.
            v.setOnClickListener { if (date == selected && inMonth) openDay(date) else moveTo(date) }
            v.setOnLongClickListener {
                if (!pages.contains(date)) return@setOnLongClickListener false
                moveTo(date)
                startActivity(Intent(this@MainActivity, EditEventActivity::class.java).putExtra(EditEventActivity.EXTRA_DATE, date.toEpochDay()))
                true
            }
            v.isFocusable = false
            v.invalidate()
            if (pendingTodayPulse && date == today && isSelected && v.width > 0) {
                pendingTodayPulse = false
                v.post { v.pulseHighlight() }
            }
        }
    }
}

/**
 * What one day cell shows. Hebrew mode: big Hebrew day letters, small Gregorian number.
 * Gregorian mode: big Gregorian number, small Hebrew letters (0.2.1 bug: Gregorian mode kept the
 * Hebrew-first cell, so switching only moved the page boundaries and looked like a one-week scroll).
 */
data class CellText(val main: String, val small: String, val label: String?) {
    companion object {
        fun of(luach: Luach, date: LocalDate, hebrewPages: Boolean): CellText {
            val heb = luach.cellNumber(date)
            val greg = date.dayOfMonth.toString()
            return if (hebrewPages) CellText(heb, greg, luach.cellLabel(date)) else CellText(greg, heb, luach.cellLabel(date))
        }
    }
}

/**
 * Month-screen header. [start] is on the reading side (right in RTL), [end] on the other side.
 * RTL keeps the Hebrew month on the right and the Gregorian month on the left in BOTH modes;
 * the primary calendar of the mode is the big one ([startIsMain]).
 */
data class HeaderText(val start: String, val end: String, val startIsMain: Boolean) {
    companion object {
        fun of(luach: Luach, first: LocalDate, len: Int, hebrewPages: Boolean, rtl: Boolean): HeaderText {
            val main = luach.pageTitle(first, hebrewPages)           // "שבט תשפ״ז" or "January 2027"
            val other = luach.pageSubtitle(first, len, hebrewPages)  // "Jan–Feb 2027" or "טבת–שבט תשפ״ז"
            return when {
                !rtl -> HeaderText(main, other, true)
                hebrewPages -> HeaderText(main, other, true)
                else -> HeaderText(other, main, false)
            }
        }
    }
}
