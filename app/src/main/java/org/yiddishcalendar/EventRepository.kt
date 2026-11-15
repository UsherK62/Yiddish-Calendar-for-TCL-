package org.yiddishcalendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** One event occurrence on a given day. */
data class Ev(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String,
    val notes: String,
    val local: Boolean,      // stored only in this app (no phone calendar)
    val deletable: Boolean,  // created by this app
)

enum class SaveResult { PHONE, PHONE_WITHOUT_REMINDER, LOCAL }

enum class Repeat(val label: String, val rrule: String?) {
    NEVER("Never", null), DAILY("Every day", "FREQ=DAILY"), WEEKLY("Every week", "FREQ=WEEKLY"),
    MONTHLY("Every month", "FREQ=MONTHLY"), YEARLY("Every year", "FREQ=YEARLY")
}

/**
 * Events live in the phone's calendar (CalendarContract) so they also show in TCL Calendar and its
 * reminders fire. If there is no permission or no writable calendar, they are kept in a small local
 * database instead ("This phone only"); local reminders are NOT implemented yet.
 */
class EventRepository(private val context: Context, private val prefs: Prefs) {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val db by lazy { LocalDb(context) }

    fun hasCalendarPermission(): Boolean =
        // ContextCompat: on Android 5.x (no runtime permissions) install-time grants count as granted.
        androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    data class Cal(val id: Long, val name: String, val account: String)

    fun writableCalendars(): List<Cal> {
        if (!hasCalendarPermission()) return emptyList()
        val out = mutableListOf<Cal>()
        val proj = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        val sel = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1"
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, proj, sel,
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) out += Cal(c.getLong(0), c.getString(1) ?: "Calendar", c.getString(2) ?: "")
            }
        }
        return out.sortedByDescending { it.account.contains("@") } // prefer a Google account calendar
    }

    /** Calendar new events go to, or null = this phone only. */
    fun targetCalendar(): Cal? {
        if (prefs.saveCalendarId == -2L) return null
        val cals = writableCalendars()
        return if (prefs.saveCalendarId >= 0) cals.firstOrNull { it.id == prefs.saveCalendarId } else cals.firstOrNull()
    }

    fun targetLabel(): String = targetCalendar()?.let { if (it.account.isNotEmpty()) "${it.name} (${it.account})" else it.name }
        ?: "This phone only"

    // ---------- Reading ----------

    fun eventsOn(date: LocalDate): List<Ev> =
        eventsBetween(date, date)[date].orEmpty().sortedWith(compareBy({ !it.allDay }, { it.startMillis }))

    /** Map of day -> events for [from]..[to] inclusive (used for the dots in the grid). */
    fun eventsBetween(from: LocalDate, to: LocalDate): Map<LocalDate, List<Ev>> {
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // All-day timestamps are UTC, timed events are local: widen query then filter by civil day.
        val all = providerInstances(begin - 86_400_000L, end + 86_400_000L) + db.instances(begin - 86_400_000L, end + 86_400_000L)
        val map = HashMap<LocalDate, MutableList<Ev>>()
        for (e in all) {
            for (d in EventDates.daysTouched(e.startMillis, e.endMillis, e.allDay, from, to, zone)) {
                map.getOrPut(d) { mutableListOf() } += e
            }
        }
        return map
    }

    private fun providerInstances(begin: Long, end: Long): List<Ev> {
        if (!hasCalendarPermission()) return emptyList()
        val own = prefs.ownEventIds
        val out = mutableListOf<Ev>()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin); ContentUris.appendId(it, end)
        }.build()
        val proj = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
        )
        runCatching {
            context.contentResolver.query(uri, proj, "${CalendarContract.Instances.VISIBLE} = 1", null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out += Ev(
                        id, c.getString(1) ?: "(no title)", c.getLong(2), c.getLong(3), c.getInt(4) == 1,
                        c.getString(5) ?: "", c.getString(6) ?: "", local = false, deletable = id.toString() in own,
                    )
                }
            }
        }
        return out
    }

    // ---------- Writing ----------

    /**
     * Saves an event. [startMinutes] null = all day. Reports the destination and any reminder failure.
     * Throws only if the event could not be saved anywhere.
     */
    fun add(title: String, date: LocalDate, startMinutes: Int?, repeat: Repeat, reminderMinutes: Int?, days: Int = 1): SaveResult {
        require(title.isNotBlank()) { "Type a title first" }
        require(days in 1..366) { "Invalid number of days" }
        require(startMinutes == null || startMinutes in 0..1439) { "Invalid time" }
        require(reminderMinutes == null || reminderMinutes >= 0) { "Invalid reminder" }
        val allDay = startMinutes == null
        val start: Long
        val end: Long
        if (allDay) {
            start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            end = date.plusDays(days.toLong()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            // Multi-day timed event: from the start time on the first day to one hour after it on the last day.
            start = EventDates.timedStart(date, startMinutes!!, zone)
            end = EventDates.timedStart(date.plusDays(days - 1L), startMinutes, zone) + 3_600_000L
        }
        val cal = targetCalendar()
        if (cal == null) {
            db.insert(title, start, end, allDay, repeat.rrule)
            return SaveResult.LOCAL
        }
        val v = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, cal.id)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else zone.id)
            if (repeat.rrule != null) {
                put(CalendarContract.Events.RRULE, repeat.rrule)
                put(CalendarContract.Events.DURATION, if (allDay) "P${days}D" else "PT${(end - start) / 1000}S")
            } else {
                put(CalendarContract.Events.DTEND, end)
            }
            put(CalendarContract.Events.HAS_ALARM, if (reminderMinutes != null) 1 else 0)
        }
        val uri = runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v) }.getOrNull()
        if (uri == null) {
            db.insert(title, start, end, allDay, repeat.rrule)
            return SaveResult.LOCAL
        }
        val id = ContentUris.parseId(uri)
        prefs.ownEventIds = prefs.ownEventIds + id.toString()
        if (reminderMinutes != null) {
            val reminderUri = runCatching {
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, id)
                    put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                })
            }.getOrNull()
            if (reminderUri == null) return SaveResult.PHONE_WITHOUT_REMINDER
        }
        return SaveResult.PHONE
    }

    /** Deletes the whole event (all repeats). Only for events this app created. */
    fun delete(e: Ev) {
        if (!e.deletable) return
        if (e.local) { db.delete(e.id); return }
        context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id), null, null)
        prefs.ownEventIds = prefs.ownEventIds - e.id.toString()
    }

    // ---------- Local fallback store ----------

    private class LocalDb(context: Context) : SQLiteOpenHelper(context, "local_events.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, " +
                    "start INTEGER NOT NULL, end_ INTEGER NOT NULL, all_day INTEGER NOT NULL, rrule TEXT)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

        fun insert(title: String, start: Long, end: Long, allDay: Boolean, rrule: String?) {
            writableDatabase.insertOrThrow("events", null, ContentValues().apply {
                put("title", title); put("start", start); put("end_", end)
                put("all_day", if (allDay) 1 else 0); put("rrule", rrule)
            })
        }

        fun delete(id: Long) { writableDatabase.delete("events", "_id = ?", arrayOf(id.toString())) }

        /** Expands simple repeats (FREQ=DAILY/WEEKLY/MONTHLY/YEARLY) into occurrences in [begin, end). */
        fun instances(begin: Long, end: Long): List<Ev> {
            val out = mutableListOf<Ev>()
            readableDatabase.rawQuery("SELECT _id, title, start, end_, all_day, rrule FROM events WHERE start < ?", arrayOf(end.toString())).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0); val title = c.getString(1); val s = c.getLong(2); val e = c.getLong(3)
                    val allDay = c.getInt(4) == 1; val rule = c.getString(5)
                    val z = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
                    val anchor = Instant.ofEpochMilli(s).atZone(z)
                    for ((os, oe) in EventOccurrences.expand(anchor, e - s, rule, begin, end)) {
                        out += Ev(id, title, os, oe, allDay, "", "", local = true, deletable = true)
                    }
                }
            }
            return out
        }
    }
}
