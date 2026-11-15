package org.yiddishcalendar

import android.view.View
import android.widget.TextView

/** Layout and bidi helpers. The Direction setting (prefs.rtlGrid) applies to the whole UI. */
object UiDir {
    /** Copied from prefs.rtlGrid by BaseActivity.onCreate/onResume, so plain helpers can read it. */
    @JvmStatic var rtl: Boolean = true
}

fun View.applyUiDir(rtl: Boolean) {
    layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
}

/**
 * Each line aligns by its OWN language: the paragraph direction comes from the first strong character
 * (firstStrong) and the text sits at the start of that paragraph (textAlignment=textStart). So an
 * English line reads left-to-right from the LEFT edge, and a Hebrew/Yiddish line reads right-to-left
 * from the RIGHT edge, whatever the layout direction. The layout itself (grid, rows, header) stays RTL.
 */
fun TextView.applyBidi() {
    textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    textAlignment = View.TEXT_ALIGNMENT_TEXT_START
}

/** Same per-language rule for a value/time column at the far end of a row. */
fun TextView.applyBidiEnd() = applyBidi()

/** First-strong isolate so a string keeps its own direction even inside an RTL toast or window. */
fun bidiIsolate(s: CharSequence): CharSequence = "\u2068$s\u2069"
