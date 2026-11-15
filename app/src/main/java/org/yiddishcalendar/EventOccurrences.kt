package org.yiddishcalendar

import java.time.Instant
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Local recurrence expansion anchored to the original date, matching simple provider RRULEs.
 * Missing dates (31st / Feb 29) are skipped, not clamped and then permanently drifted.
 * Jump near the query window so even an old daily event remains visible after 4000 days.
 */
object EventOccurrences {
    fun expand(anchor: ZonedDateTime, duration: Long, rule: String?, begin: Long, end: Long): List<Pair<Long, Long>> {
        if (end <= begin || duration <= 0) return emptyList()
        val target = Instant.ofEpochMilli(begin - duration).atZone(anchor.zone)
        val estimate = when (rule) {
            "FREQ=DAILY" -> ChronoUnit.DAYS.between(anchor.toLocalDate(), target.toLocalDate())
            "FREQ=WEEKLY" -> ChronoUnit.WEEKS.between(anchor.toLocalDate(), target.toLocalDate())
            "FREQ=MONTHLY" -> ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(target))
            "FREQ=YEARLY" -> (target.year - anchor.year).toLong()
            else -> 0L
        }
        var n = (estimate - 1).coerceAtLeast(0)
        val out = mutableListOf<Pair<Long, Long>>()
        while (true) {
            val occ = when (rule) {
                "FREQ=DAILY" -> anchor.plusDays(n)
                "FREQ=WEEKLY" -> anchor.plusWeeks(n)
                "FREQ=MONTHLY" -> anchor.plusMonths(n)
                "FREQ=YEARLY" -> anchor.plusYears(n)
                else -> anchor
            }
            val start = occ.toInstant().toEpochMilli()
            if (start >= end) break
            val valid = when (rule) {
                "FREQ=MONTHLY", "FREQ=YEARLY" -> occ.dayOfMonth == anchor.dayOfMonth
                else -> true
            }
            if (valid && start + duration > begin) out += start to (start + duration)
            if (rule !in setOf("FREQ=DAILY", "FREQ=WEEKLY", "FREQ=MONTHLY", "FREQ=YEARLY")) break
            n++
        }
        return out
    }
}
