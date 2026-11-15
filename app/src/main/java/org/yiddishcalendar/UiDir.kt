package org.yiddishcalendar

import android.view.View
import android.widget.TextView

/** Layout and bidi helpers. The Direction setting (prefs.rtlGrid) applies to the whole UI. */
fun View.applyUiDir(rtl: Boolean) {
    layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
}

/**
 * Align to the layout start (right in RTL). Paragraph direction follows the first strong
 * character, so Hebrew/Yiddish reads RTL and English (with its punctuation) reads LTR.
 * Matches Android's [View.TEXT_DIRECTION_FIRST_STRONG].
 */
fun TextView.applyBidi() {
    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    textDirection = View.TEXT_DIRECTION_FIRST_STRONG
}

/** First-strong isolate so a string keeps its own direction even inside an RTL toast or window. */
fun bidiIsolate(s: CharSequence): CharSequence = "\u2068$s\u2069"
