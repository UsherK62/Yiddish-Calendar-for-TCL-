package org.yiddishcalendar

import android.content.Context
import com.kosherjava.zmanim.ZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * All Jewish-calendar text, from KosherJava (com.kosherjava:zmanim 3.0.0), HebrewDateFormatter.setHebrewFormat(true).
 * Settings → "Hebrew text" picks how much is in Hebrew letters:
 *  Every level: the Hebrew date (cell numbers, "כ׳ חשון תשפ״ז", month headers "חשון תשפ״ז"), parsha names
 *  ("וירא") and short markers (ר״ח). Gregorian dates stay English.
 *  More: also Yom Tov / Rosh Chodesh / special Shabbos names.
 *  Everything: also weekday names/headers and menus (see [Ui]). שבת פרשת is always Hebrew.
 */
class Luach(private val context: Context, private val prefs: Prefs) {
    private val en = HebrewDateFormatter()
    private val he = HebrewDateFormatter().apply { isHebrewFormat = true }
    /** Level 1+: holiday names in Hebrew. */
    private val names get() = prefs.hebrewText >= 1
    /** Level 2: everything, including weekdays and labels. */
    private val all get() = prefs.hebrewText >= 2
    /** Formatter for holiday names. Dates, month names, years and parshiyos always use [he]. */
    private val fmt get() = if (names) he else en

    fun jc(date: LocalDate): JewishCalendar = JewishCalendar(date).apply { inIsrael = prefs.inIsrael }

    // ---- Month grid ----

    /** Weekday header row, Sunday first. */
    fun weekdayLabels(): List<String> =
        if (all) listOf("א", "ב", "ג", "ד", "ה", "ו", "שבת")
        else listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "שבת")

    /** Big number in a day cell, always Hebrew letters: כ״ז (geresh dropped on single letters). */
    fun cellNumber(date: LocalDate): String = he.formatHebrewNumber(jc(date).jewishDayOfMonth).replace("׳", "").replace("״", "")

    /** Short text at the bottom of a cell (parsha on Shabbos, Yom Tov, Rosh Chodesh) or null. */
    fun cellMark(date: LocalDate): String? {
        val j = jc(date)
        if (prefs.showParsha && j.parshah != JewishCalendar.Parshah.NONE) return he.formatParshah(j)
        val yt = fmt.formatYomTov(j)
        if (yt.isNotEmpty()) return yt
        // Short cell markers for Jewish terms are always Hebrew letters, in every Hebrew-text level.
        if (j.isRoshChodesh) return "ר״ח"
        return null
    }

    fun isSpecialDay(date: LocalDate): Boolean { val j = jc(date); return j.isYomTov || j.isRoshChodesh }

    /** Header title for a page: Hebrew mode "חשון תשפ״ז" (always Hebrew letters); Gregorian mode "October 2026". */
    fun pageTitle(first: LocalDate, hebrewPage: Boolean): String =
        if (hebrewPage) monthName(first) else gregMonth(YearMonth.from(first), long = true)

    /** Header right side: the other calendar's months covered by the page. */
    fun pageSubtitle(first: LocalDate, len: Int, hebrewPage: Boolean): String {
        val last = first.plusDays((len - 1).toLong())
        return if (hebrewPage) {
            val a = YearMonth.from(first); val b = YearMonth.from(last)
            when {
                a == b -> gregMonth(a, long = false) + " " + a.year
                a.year == b.year -> shortMonth(a) + "–" + shortMonth(b) + " " + b.year
                else -> shortMonth(a) + " " + a.year + "–" + shortMonth(b) + " " + b.year
            }
        } else {
            val a = jc(first); val b = jc(last)
            val am = he.formatMonth(a); val bm = he.formatMonth(b)
            if (am == bm) "$am ${year(b.jewishYear)}" else "$am–$bm ${year(b.jewishYear)}"
        }
    }

    /** Hebrew-date pieces (day, month, year) are always Hebrew letters, in every Hebrew-text level. */
    fun monthName(date: LocalDate): String { val j = jc(date); return he.formatMonth(j) + " " + year(j.jewishYear) }

    private fun year(y: Int) = he.formatHebrewNumber(y % 1000)
    private fun shortMonth(m: YearMonth) = m.month.getDisplayName(TextStyle.SHORT, Locale.US)
    private fun gregMonth(m: YearMonth, long: Boolean) =
        m.month.getDisplayName(if (long) TextStyle.FULL else TextStyle.SHORT, Locale.US) + if (long) " " + m.year else ""

    // ---- Status line / day detail ----

    fun weekdayName(date: LocalDate): String {
        val i = date.dayOfWeek.value % 7 // Sunday = 0
        // Plain English below Everything; Hebrew day names in Everything. "Shabbos" kept for Saturday.
        return if (all) listOf("יום ראשון", "יום שני", "יום שלישי", "יום רביעי", "יום חמישי", "יום שישי", "שבת קודש")[i]
        else listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "שבת")[i]
    }

    /** Main line: שבת פרשת, a holiday, ר״ח, ערב שבת, or the weekday. */
    fun dayTitle(date: LocalDate): String {
        val j = jc(date)
        val parts = mutableListOf<String>()
        if (j.parshah != JewishCalendar.Parshah.NONE && prefs.showParsha) {
            parts += "שבת פרשת " + he.formatParshah(j)
        }
        val yt = fmt.formatYomTov(j)
        if (yt.isNotEmpty()) parts += yt
        // The month name is a Hebrew-date piece, so it is Hebrew even in Less: "Rosh Chodesh חשון".
        if (j.isRoshChodesh) parts += "ר״ח " + he.formatRoshChodesh(j).removePrefix("ראש חודש").trim()
        val special = if (j.specialShabbos != JewishCalendar.Parshah.NONE) he.formatSpecialParshah(j) else ""
        if (special.isNotEmpty()) parts += special
        if (parts.isEmpty()) {
            parts += if (j.dayOfWeek == 6 && !j.isErevYomTov) "ערב שבת" else weekdayName(date)
        }
        return parts.distinct().joinToString(sep)
    }

    /** Second line: "Sat Oct 31 · כ׳ חשון תשפ״ז". */
    fun daySubtitle(date: LocalDate): String = mixed(fullHebrewDate(date), gregShort(date))

    /**
     * Joins a Hebrew and an English part so the line reads naturally: English first in Less/More
     * (paragraph stays left-to-right), Hebrew first in Everything (paragraph becomes right-to-left).
     */
    fun mixed(hebrew: String, english: String): String = if (all) "$hebrew $sep $english" else "$english $sep $hebrew"

    /**
     * " · " wrapped in direction marks (LRM in Less/More, RLM in Everything). Without them, Unicode bidi
     * would glue "וירא · ראש חודש" into one right-to-left run and show the two names in swapped order.
     */
    private val sep get() = if (all) " \u200F·\u200F " else " \u200E·\u200E "

    /** Always Hebrew letters: "כ׳ חשון תשפ״ז". */
    fun fullHebrewDate(date: LocalDate): String = he.format(jc(date)).replace(",", "")
    fun gregShort(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE MMM d", Locale.US))
    fun gregLong(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE MMM d, yyyy", Locale.US))

    /** Extra facts for the day screen (omer, daf yomi). */
    fun dayFacts(date: LocalDate): List<Pair<String, String>> {
        val j = jc(date)
        val out = mutableListOf<Pair<String, String>>()
        if (j.dayOfOmer > 0) out += (if (all) "ספירה" else "Omer") to fmt.formatOmer(j)
        runCatching { j.dafYomiBavli?.let { out += (if (all) "דף יומי" else "Daf Yomi") to fmt.formatDafYomiBavli(it) } }
        return out
    }

    // ---- Nightfall (internal only; the app shows no zmanim) ----

    /**
     * Fixed place for the "date changes at night" option: Jerusalem when In Israel is on, else Brooklyn 11219.
     * There is no Location setting. This is only accurate for the selected fixed place, not for travel.
     */
    private fun nightfallPlace(): GeoLocation =
        if (prefs.inIsrael) GeoLocation("Jerusalem", 31.7683, 35.2137, 0.0, ZoneId.of("Asia/Jerusalem"))
        else GeoLocation("Brooklyn", 40.634, -73.996, 0.0, ZoneId.of("America/New_York"))

    /** "Today" for the grid. With the nightfall option, after nightfall (tzeis, 8.5°) it is already tomorrow's Hebrew date. */
    fun today(instant: Instant = Instant.now()): LocalDate {
        if (!prefs.nightfall) return instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val place = nightfallPlace()
        val now = instant.atZone(if (prefs.inIsrael) ZoneId.of("Asia/Jerusalem") else ZoneId.of("America/New_York")).toLocalDate()
        val night = runCatching { ZmanimCalendar(place).apply { setLocalDate(now) }.tzaisGeonim8Point5Degrees }
            .getOrNull() ?: return now
        return if (!instant.isBefore(night)) now.plusDays(1) else now
    }
}
