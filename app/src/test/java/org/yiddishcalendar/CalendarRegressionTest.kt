package org.yiddishcalendar

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.OutDateStyle
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.*
import java.util.TimeZone

/** Host tests only: SharedPreferences is an in-memory fake, not a device/provider test. */
class CalendarRegressionTest {
    private fun prefs(): Pair<Context, Prefs> {
        val data = mutableMapOf<String, Any?>()
        lateinit var sp: SharedPreferences
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, m, a ->
            when (m.name) {
                "apply" -> null
                "commit" -> true
                "remove" -> { data.remove(a!![0]); editor }
                "clear" -> { data.clear(); editor }
                else -> { if (m.name.startsWith("put")) data[a!![0] as String] = a[1]; editor }
            }
        } as SharedPreferences.Editor
        sp = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, m, a ->
            when (m.name) {
                "edit" -> editor
                "getAll" -> data
                "contains" -> data.containsKey(a!![0])
                else -> if (m.name.startsWith("get")) data[a!![0]] ?: a[1] else null
            }
        } as SharedPreferences
        val context = object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String, mode: Int) = sp
        }
        return context to Prefs(context)
    }

    @Test fun monthPagesRoundTripAcrossFullSupportedRange() {
        for (hebrew in listOf(true, false)) {
            val pages = MonthPages(hebrew, LocalDate.of(2026, 11, 7))
            var count = 0
            for (off in 0..pages.lastOffset) {
                val (first, len) = pages.range(off)
                assertEquals(first.plusDays(len.toLong()), pages.range(off + 1).first)
                for (day in 0 until len) {
                    val d = first.plusDays(day.toLong())
                    assertEquals(off, pages.offsetOf(d))
                    assertTrue(pages.contains(d))
                    count++
                }
            }
            assertTrue(count > 7000)
            assertFalse(pages.contains(pages.range(0).first.minusDays(1)))
            assertFalse(pages.contains(pages.range(pages.lastOffset + 1).first))
        }
    }

    @Test fun hebrewGridHasCorrectWeekdayPositionsAndNoGaps() {
        val pages = MonthPages(true, LocalDate.of(2026, 11, 7))
        for (off in 0..pages.lastOffset) {
            val month = pages.month(pages.startKey, off, DayOfWeek.SUNDAY, OutDateStyle.EndOfRow)
            val days = month.weekDays.flatten()
            assertEquals(DayOfWeek.SUNDAY, days.first().date.dayOfWeek)
            assertEquals(DayOfWeek.SATURDAY, days.last().date.dayOfWeek)
            assertEquals(pages.range(off).second, days.count { it.position == DayPosition.MonthDate })
            days.zipWithNext().forEach { (a,b) -> assertEquals(a.date.plusDays(1), b.date) }
            assertTrue(month.weekDays.size in 4..6)
        }
    }

    @Test fun hebrewMonthAndYearLeapTransitionsClampCorrectly() {
        val pages = MonthPages(true, LocalDate.of(2026, 11, 7))
        val adar = JewishDate(5783, JewishDate.ADAR, 29).localDate
        assertEquals(JewishDate(5784, JewishDate.ADAR_II, 29).localDate, pages.addYears(adar, 1))
        assertEquals(adar, pages.addYears(pages.addYears(adar, 1), -1))
        val adarI30 = JewishDate(5784, JewishDate.ADAR, 30).localDate
        assertEquals(JewishDate(5784, JewishDate.ADAR_II, 29).localDate, pages.addMonths(adarI30, 1))
        for (off in 1 until pages.lastOffset) {
            val d = pages.range(off).first
            assertEquals(pages.range(off+1).first, pages.addMonths(d, 1))
            assertEquals(pages.range(off-1).first, pages.addMonths(d, -1))
        }
    }

    @Test fun defaultsAndPreferenceRoundTrip() {
        val (_, p) = prefs()
        assertTrue(p.rtlGrid); assertTrue(p.hebrewMonths); assertTrue(p.showParsha)
        assertFalse(p.inIsrael); assertEquals(0, p.hebrewText)
        assertEquals(1, p.theme)
        p.theme = 0; assertEquals(0, p.theme)
        p.theme = 1; assertEquals(1, p.theme)
        p.hebrewText = 99; assertEquals(2, p.hebrewText)
        p.rtlGrid = false; assertFalse(p.rtlGrid)
        p.saveCalendarId = -2; assertEquals(-2L, p.saveCalendarId)
        p.ownEventIds = setOf("12", "14"); assertEquals(setOf("12", "14"), p.ownEventIds)
        p.bump(); assertEquals(1, p.revision)
    }

    @Test fun screenshotShabbosIsHebrewInAllModes() {
        val (c, p) = prefs(); val l = Luach(c,p)
        for (level in 0..2) {
            p.hebrewText = level
            assertEquals("שבת פרשת חיי שרה", l.dayTitle(LocalDate.of(2026,11,7)))
            assertFalse(l.fullHebrewDate(LocalDate.of(2026,11,7)).any { it in '0'..'9' })
            assertTrue(l.cellNumber(LocalDate.of(2026,11,7)).all { it in 'א'..'ת' })
            assertEquals("שבת", l.weekdayLabels()[6])
        }
        p.hebrewText = 0
        assertEquals("Wednesday", l.weekdayName(LocalDate.of(2026,11,4)))
        assertFalse(l.dayTitle(LocalDate.of(2026,10,12)).contains("Rosh Chodesh"))
    }

    @Test fun israelFlagAndParshaSwitchReachCalendar() {
        val (c,p) = prefs(); val l = Luach(c,p)
        p.inIsrael = true; assertTrue(l.jc(LocalDate.of(2027,6,12)).inIsrael)
        p.inIsrael = false; assertFalse(l.jc(LocalDate.of(2027,6,12)).inIsrael)
        p.showParsha = false; assertFalse(l.dayTitle(LocalDate.of(2026,11,7)).contains("חיי שרה"))
        p.showParsha = true; assertTrue(l.dayTitle(LocalDate.of(2026,11,7)).contains("חיי שרה"))
    }

    @Test fun nightDateRolloverAndMidnightAreStable() {
        val (c,p) = prefs(); val l=Luach(c,p)
        p.nightfall = true
        assertEquals(LocalDate.of(2026,11,7), l.today(Instant.parse("2026-11-07T17:00:00Z")))
        assertEquals(LocalDate.of(2026,11,8), l.today(Instant.parse("2026-11-08T02:00:00Z")))
        assertEquals(LocalDate.of(2026,11,8), l.today(Instant.parse("2026-11-08T06:00:00Z")))
        p.inIsrael = true
        assertEquals(LocalDate.of(2026,11,8), l.today(Instant.parse("2026-11-07T20:00:00Z")))
        p.nightfall = false
        val old=TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(LocalDate.of(2026,11,7), l.today(Instant.parse("2026-11-08T02:00:00Z")))
        } finally { TimeZone.setDefault(old) }
    }

    private fun occurrences(anchor: String, rule: String?, from: String, to: String) =
        EventOccurrences.expand(ZonedDateTime.parse(anchor), 3_600_000, rule,
            Instant.parse(from).toEpochMilli(), Instant.parse(to).toEpochMilli())
            .map { Instant.ofEpochMilli(it.first).atZone(ZoneOffset.UTC).toLocalDate() }

    @Test fun monthly31stDoesNotDriftTo28th() {
        assertEquals(listOf(LocalDate.of(2026,1,31),LocalDate.of(2026,3,31),LocalDate.of(2026,5,31)),
            occurrences("2026-01-31T09:00:00Z", "FREQ=MONTHLY", "2026-01-01T00:00:00Z", "2026-06-01T00:00:00Z"))
    }

    @Test fun yearlyLeapDaySkipsNonLeapYears() {
        assertEquals(listOf(LocalDate.of(2024,2,29),LocalDate.of(2028,2,29)),
            occurrences("2024-02-29T09:00:00Z", "FREQ=YEARLY", "2024-01-01T00:00:00Z", "2029-01-01T00:00:00Z"))
    }

    @Test fun oldDailyEventSurvivesMoreThan4000Days() {
        assertEquals(listOf(LocalDate.of(2026,11,7)), occurrences("2000-01-01T09:00:00Z", "FREQ=DAILY", "2026-11-07T00:00:00Z", "2026-11-08T00:00:00Z"))
    }

    @Test fun dailyRepeatKeepsWallClockAcrossDst() {
        val a=ZonedDateTime.parse("2026-03-07T09:00:00-05:00[America/New_York]")
        val out=EventOccurrences.expand(a,3_600_000,"FREQ=DAILY",a.toInstant().toEpochMilli(),a.plusDays(3).toInstant().toEpochMilli())
        assertEquals(3,out.size)
        assertTrue(out.all { Instant.ofEpochMilli(it.first).atZone(a.zone).hour == 9 })
    }

    @Test fun recurrenceBoundsAndNonRepeatingEvents() {
        assertTrue(occurrences("2026-01-01T09:00:00Z", null, "2026-01-02T00:00:00Z", "2026-01-03T00:00:00Z").isEmpty())
        assertEquals(1, occurrences("2026-01-01T09:00:00Z", null, "2026-01-01T09:30:00Z", "2026-01-01T11:00:00Z").size)
        assertEquals(2, occurrences("2026-01-01T09:00:00Z", "FREQ=WEEKLY", "2026-01-07T00:00:00Z", "2026-01-20T00:00:00Z").size)
    }
    @Test fun eventEntryUsesWallClockOnBothDstChanges() {
        val zone = ZoneId.of("America/New_York")
        for (date in listOf(LocalDate.of(2026,3,8), LocalDate.of(2026,11,1))) {
            val actual = Instant.ofEpochMilli(EventDates.timedStart(date, 9*60+15, zone)).atZone(zone)
            assertEquals(9, actual.hour); assertEquals(15, actual.minute)
        }
    }

    @Test fun longRunningEventsAppearInsideALaterQueryWindow() {
        val from=LocalDate.of(2026,11,7)
        val out=EventDates.daysTouched(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(),
            Instant.parse("2027-01-01T00:00:00Z").toEpochMilli(), true, from, from.plusDays(1), ZoneId.of("America/New_York"))
        assertEquals(listOf(from, from.plusDays(1)),out)
    }

    @Test fun allDayIsUtcAndEndMidnightIsExclusive() {
        val start=Instant.parse("2026-11-07T00:00:00Z").toEpochMilli()
        val end=Instant.parse("2026-11-08T00:00:00Z").toEpochMilli()
        val day=LocalDate.of(2026,11,7)
        for (zone in listOf("America/New_York", "Pacific/Kiritimati", "Pacific/Honolulu")) {
            assertEquals(listOf(day), EventDates.daysTouched(start,end,true,day.minusDays(1),day.plusDays(1),ZoneId.of(zone)))
        }
        assertEquals(listOf(day),EventDates.daysTouched(start,end,false,day.minusDays(1),day.plusDays(1),ZoneOffset.UTC))
    }

}
