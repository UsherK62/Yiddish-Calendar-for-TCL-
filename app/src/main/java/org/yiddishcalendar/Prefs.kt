package org.yiddishcalendar

import android.content.Context
import android.content.SharedPreferences

/** All user settings in one place. Defaults suit a Yiddish-speaking user in the US. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("yc_prefs", Context.MODE_PRIVATE)

    var inIsrael: Boolean
        get() = sp.getBoolean("in_israel", false)
        set(v) = sp.edit().putBoolean("in_israel", v).apply()

    /**
     * How much text is in Hebrew letters.
     * 0 = Less (default): only the Hebrew date (כ״ א חשון, cell numbers) and Shabbos/parsha names.
     * 1 = More: also Yom Tov / Rosh Chodesh names. (Month names + years are Hebrew in every level.)
     * 2 = Everything: also weekdays, menus, settings, soft-key labels.
     */
    var hebrewText: Int
        get() = sp.getInt("hebrew_text", 0)
        set(v) = sp.edit().putInt("hebrew_text", v.coerceIn(0, 2)).apply()

    var showParsha: Boolean
        get() = sp.getBoolean("show_parsha", true)
        set(v) = sp.edit().putBoolean("show_parsha", v).apply()

    /** true = one page per Hebrew month, false = per Gregorian month. */
    var hebrewMonths: Boolean
        get() = sp.getBoolean("hebrew_months", true)
        set(v) = sp.edit().putBoolean("hebrew_months", v).apply()

    /** Hebrew date (and "today") changes at nightfall instead of midnight (internal tzeis; see Luach.today). */
    var nightfall: Boolean
        get() = sp.getBoolean("nightfall", true)
        set(v) = sp.edit().putBoolean("nightfall", v).apply()

    /** UI and month-grid direction: true (default) = right-to-left (Sunday on the right, titles on the right); false = left-to-right. */
    var rtlGrid: Boolean
        get() = sp.getBoolean("rtl_grid", true)
        set(v) = sp.edit().putBoolean("rtl_grid", v).apply()

    /** 0 = Dark, 1 = Light (default on first run). A saved choice is kept. */
    var theme: Int
        get() = sp.getInt("theme", 1)
        set(v) = sp.edit().putInt("theme", v).apply()

    /** Calendar id to save to; -1 = automatic (first writable), -2 = this phone only. */
    var saveCalendarId: Long
        get() = sp.getLong("save_cal", -1L)
        set(v) = sp.edit().putLong("save_cal", v).apply()

    /** Event ids this app created in the phone calendar (only those may be deleted here). */
    var ownEventIds: Set<String>
        get() = sp.getStringSet("own_ids", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("own_ids", v).apply()

    var askedPermissions: Boolean
        get() = sp.getBoolean("asked_perms", false)
        set(v) = sp.edit().putBoolean("asked_perms", v).apply()

    /** Bumped on every settings change so open screens know to redraw. */
    var revision: Int
        get() = sp.getInt("rev", 0)
        set(v) = sp.edit().putInt("rev", v).apply()

    fun bump() { revision = revision + 1 }
}
