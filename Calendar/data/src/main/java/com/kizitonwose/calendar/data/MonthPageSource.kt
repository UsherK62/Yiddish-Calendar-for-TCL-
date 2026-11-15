package com.kizitonwose.calendar.data

import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.OutDateStyle
import java.time.DayOfWeek
import java.time.YearMonth

/**
 * Yiddish Calendar fork addition.
 *
 * Lets an app supply its own month pages (for example Hebrew months) instead of
 * Gregorian [YearMonth] pages. The page key stays a [YearMonth] (startMonth + offset)
 * so the adapter's indexing and scrollToMonth() keep working; only the days inside
 * each page change. When [provider] is null the library behaves exactly as upstream.
 *
 * Note: in custom mode, APIs that look a page up from a date (scrollToDate,
 * notifyDateChanged) are not valid. Use scrollToMonth(pageKey) / notifyMonthChanged.
 */
public object MonthPageSource {
    public fun interface Provider {
        public fun month(
            startMonth: YearMonth,
            offset: Int,
            firstDayOfWeek: DayOfWeek,
            outDateStyle: OutDateStyle,
        ): CalendarMonth
    }

    @JvmStatic
    public var provider: Provider? = null
}
