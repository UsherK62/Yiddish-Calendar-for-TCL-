package org.yiddishcalendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

object EventDates {
    /** Wall-clock time, not elapsed minutes after midnight (which is wrong on DST transition days). */
    fun timedStart(date: LocalDate, minutes: Int, zone: ZoneId): Long {
        require(minutes in 0..1439)
        return date.atTime(minutes / 60, minutes % 60).atZone(zone).toInstant().toEpochMilli()
    }

    fun daysTouched(start: Long, end: Long, allDay: Boolean, from: LocalDate, to: LocalDate, zone: ZoneId): List<LocalDate> {
        val eventZone = if (allDay) ZoneOffset.UTC else zone
        var day = maxOf(from, Instant.ofEpochMilli(start).atZone(eventZone).toLocalDate())
        val last = minOf(to, Instant.ofEpochMilli(maxOf(start, end - 1)).atZone(eventZone).toLocalDate())
        val out = mutableListOf<LocalDate>()
        while (day <= last) { out += day; day = day.plusDays(1) }
        return out
    }
}
