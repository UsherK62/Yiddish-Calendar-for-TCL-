package org.yiddishcalendar

import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.OutDateStyle
import com.kizitonwose.calendar.data.MonthPageSource
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.min

/**
 * Month pages for the CalendarView: Hebrew months (via the fork's MonthPageSource hook) or plain
 * Gregorian months. Page keys are YearMonth values (startKey + offset) in both modes, so the
 * library's scrollToMonth()/notifyMonthChanged() work unchanged.
 */
class MonthPages(val hebrew: Boolean, today: LocalDate) : MonthPageSource.Provider {
    companion object {
        const val SPAN = 120 // months before and after today (~10 years each way)
        private val KEY_BASE: YearMonth = YearMonth.of(2000, 1) // synthetic keys in Hebrew mode
    }

    private val anchorYear: Int
    private val anchorMonth: Int

    init {
        val j = JewishDate(today)
        j.setJewishDate(j.jewishYear, j.jewishMonth, 1)
        j.minusMonths(SPAN)
        anchorYear = j.jewishYear
        anchorMonth = j.jewishMonth
    }

    val startKey: YearMonth = if (hebrew) KEY_BASE else YearMonth.from(today).minusMonths(SPAN.toLong())
    val endKey: YearMonth = startKey.plusMonths(2L * SPAN)
    val lastOffset = 2 * SPAN

    private val cache = HashMap<Int, Pair<LocalDate, Int>>()

    /** First day and length of page [offset]. */
    fun range(offset: Int): Pair<LocalDate, Int> {
        if (!hebrew) {
            val ym = startKey.plusMonths(offset.toLong())
            return ym.atDay(1) to ym.lengthOfMonth()
        }
        return cache.getOrPut(offset) {
            val j = JewishDate(anchorYear, anchorMonth, 1)
            if (offset > 0) j.plusMonths(offset) else if (offset < 0) j.minusMonths(-offset) // plusMonths(0) throws
            j.localDate to j.daysInJewishMonth
        }
    }

    fun keyOf(offset: Int): YearMonth = startKey.plusMonths(offset.toLong())
    fun offsetOfKey(key: YearMonth): Int = ChronoUnit.MONTHS.between(startKey, key).toInt()

    /** Page offset that contains [date]. */
    fun offsetOf(date: LocalDate): Int {
        if (!hebrew) return ChronoUnit.MONTHS.between(startKey, YearMonth.from(date)).toInt()
        var g = Math.floorDiv(ChronoUnit.DAYS.between(range(0).first, date), 30L).toInt()
        while (range(g).first > date) g--
        while (range(g + 1).first <= date) g++
        return g
    }

    fun contains(date: LocalDate) = offsetOf(date) in 0..lastOffset

    /** Same day-of-month, [n] pages later (clamped: 30 Cheshvan -> 29 Kislev etc.). */
    fun addMonths(date: LocalDate, n: Int): LocalDate {
        if (!hebrew) return date.plusMonths(n.toLong())
        val off = offsetOf(date)
        val idx = ChronoUnit.DAYS.between(range(off).first, date).toInt()
        val (first, len) = range(off + n)
        return first.plusDays(min(idx, len - 1).toLong())
    }

    /** Same date [n] years later. Hebrew: Adar <-> Adar II in leap years, day clamped. */
    fun addYears(date: LocalDate, n: Int): LocalDate {
        if (!hebrew) return date.plusYears(n.toLong())
        val j = JewishDate(date)
        val y = j.jewishYear + n
        var m = j.jewishMonth
        val srcLeap = JewishDate.isJewishLeapYear(j.jewishYear)
        val dstLeap = JewishDate.isJewishLeapYear(y)
        if (m == JewishDate.ADAR_II && !dstLeap) m = JewishDate.ADAR
        else if (m == JewishDate.ADAR && !srcLeap && dstLeap) m = JewishDate.ADAR_II
        val len = JewishDate(y, m, 1).daysInJewishMonth
        return JewishDate(y, m, min(j.jewishDayOfMonth, len)).localDate
    }

    /** MonthPageSource.Provider: builds the grid for a Hebrew month page. */
    override fun month(
        startMonth: YearMonth,
        offset: Int,
        firstDayOfWeek: DayOfWeek,
        outDateStyle: OutDateStyle,
    ): CalendarMonth {
        val (first, len) = range(offset)
        val last = first.plusDays((len - 1).toLong())
        val inDays = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val used = inDays + len
        val endOfRow = if (used % 7 != 0) 7 - used % 7 else 0
        val outDays = if (outDateStyle == OutDateStyle.EndOfRow) endOfRow else endOfRow + (6 - (used + endOfRow) / 7) * 7
        val gridStart = first.minusDays(inDays.toLong())
        val days = (0 until used + outDays).map { i ->
            val d = gridStart.plusDays(i.toLong())
            val pos = when {
                d < first -> DayPosition.InDate
                d > last -> DayPosition.OutDate
                else -> DayPosition.MonthDate
            }
            CalendarDay(d, pos)
        }
        return CalendarMonth(keyOf(offset), days.chunked(7))
    }
}
