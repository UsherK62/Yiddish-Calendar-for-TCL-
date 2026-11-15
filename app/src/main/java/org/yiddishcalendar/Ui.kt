package org.yiddishcalendar

/**
 * UI words in "Everything" Hebrew mode (Settings → Hebrew text → Everything). In Less/More the
 * English text is kept. Unknown strings pass through unchanged, so callers can wrap anything.
 */
object Ui {
    private val HE = mapOf(
        // soft keys
        "Menu" to "תפריט", "Open" to "פתח", "Add" to "הוסף", "Back" to "צוריק", "Save" to "שמור",
        "Change" to "שנה", "Edit" to "ערוך", "Select" to "בחר", "Go" to "גיי", "Delete" to "מחק",
        // screens and menu
        "Settings" to "הגדרות", "New event" to "נייע געשעעניש", "Events" to "געשעענישן",
        "Today" to "היינט", "Go to date" to "גיי צו א דאטום", "Add event" to "הוסף געשעעניש", "About" to "וועגן",
        "Delete?" to "מחק?", "Where" to "וואו",
        "No events. Choose Menu → Add event." to "קיין געשעענישן. קלייב תפריט ← הוסף געשעעניש.",
        "Night date: Brooklyn; Jerusalem if In Israel. Not your location." to "דאטום ביי נאכט: ברוקלין; אין ארץ ישראל: ירושלים. נישט לויט אייער ארט.",
        "Saved" to "נשמר",
        "Saved on this phone only. No reminders." to "נשמר נאר אין טעלעפאן. אן דערמאנונגען.",
        "Saved, but the phone calendar could not set the reminder." to "נשמר, אבער אן דערמאנונג אינעם טעלעפאן לוח.",
        // form
        "Title" to "נאמען", "Date" to "דאטום", "Time" to "צייט", "Repeat" to "איבערחזר'ן", "Reminder" to "דערמאנונג",
        "All day" to "גאנצן טאג", "Never" to "קיינמאל", "Every day" to "יעדן טאג", "Every week" to "יעדע וואך",
        "Every month" to "יעדן חודש", "Every year" to "יעדעס יאר", "None" to "קיינע", "Type a title" to "שרייב א נאמען",
        // settings
        "Hebrew text" to "עברית", "In Israel" to "ארץ ישראל", "Show parsha" to "ווייז פרשה",
        "Date changes at night" to "דאטום טוישט זיך ביי נאכט", "Theme" to "פארבן", "Direction" to "ריכטונג",
        "Display" to "אויסזען", "Calendar" to "לוח", "Other" to "אנדערע",
        "Save events to" to "שפייכלער", "Automatic" to "אויטאמאטיש", "This phone only" to "נאר אין טעלעפאן",
        "ON" to "יא", "OFF" to "ניין", "Dark" to "טונקל", "Light" to "ליכטיג",
        "Shabbos names + date" to "שבת + דאטום", "+ holiday names" to "+ יום טוב", "all text" to "אלע טעקסט",
        "Less" to "ווייניגער", "More" to "מער", "Everything" to "אלעס",
        "Right-to-left" to "רעכטס צו לינקס", "Left-to-right" to "לינקס צו רעכטס",
        "Dark theme" to "טונקלע פארבן",
    )

    fun tr(level: Int, s: String): String = if (level >= 2) HE[s] ?: s else s
}
