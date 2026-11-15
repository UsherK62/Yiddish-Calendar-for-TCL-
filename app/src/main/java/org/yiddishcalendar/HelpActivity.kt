package org.yiddishcalendar

import androidx.appcompat.widget.AppCompatTextView
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Menu → Help: short, plain help for keys, soft keys, touch and the New event screen.
 * English always; in Yiddish mode the Yiddish text comes first, then the English. D-pad up/down scrolls.
 */
class HelpActivity : BaseActivity() {
    private lateinit var scroll: ScrollView

    /** (key, English, Yiddish) */
    private val keys = listOf(
        Triple("OK", "Open the selected day", "עפן דעם טאג"),
        Triple("D-pad", "Move to another day", "גיי צו אן אנדער טאג"),
        Triple("Menu", "Left soft key: the menu", "לינקער קנעפל: תפריט"),
        Triple("Add", "Right soft key: new event", "רעכטער קנעפל: נייע געשעעניש"),
        Triple("4  6", "Change month", "טוישן חודש"),
        Triple("1  3", "Change year", "טוישן יאר"),
        Triple("0", "Today", "היינט"),
        Triple("*", "Go to date", "גיי צו א דאטום"),
        Triple("#", "Hebrew / Gregorian months", "עברי / ענגליש חדשים"),
        Triple("Back", "Leave the screen", "ארויס פון בלאט"),
    )

    /** (heading, English, Yiddish) */
    private val notes = listOf(
        Triple("Touch · אנרירן",
            "Tap a day to select it, tap it again to open it. Swipe sideways to change month. Tap the words at the bottom to use them like the soft keys.",
            "טאפ א טאג צו קלייבן, נאכאמאל צו עפענען. שיב אויף די זייט צו טוישן דעם חודש. טאפ די ווערטער אונטן ווי די קנעפלעך."),
        Triple("New event · נייע געשעעניש",
            "Press OK (or tap) on a row to choose. Use Days for an event of several days. The left soft key saves.",
            "דריק OK (אדער טאפ) אויף א שורה צו קלייבן. \"טעג\" פאר א געשעעניש פון עטליכע טעג. דער לינקער קנעפל שפייכלט."),
        Triple("Language · שפראך",
            "Settings, top row. In Settings, press 0 for English.",
            "הגדרות, אויבערשטע שורה. אין הגדרות, דריק 0 פאר ענגליש."),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day)
        setHeader(if (prefs.hebrewText >= 2) "הילף · Help" else "Help")
        setSoftKeys("", "", getString(R.string.sk_back))
        scroll = findViewById(R.id.scroll)
        scroll.isFocusable = true
        val content = findViewById<LinearLayout>(R.id.content)
        content.setPadding(0, dp(8f), 0, dp(8f))
        if (prefs.hebrewText >= 2) content.addView(block(yiddish = true))
        content.addView(block(yiddish = false))
        scroll.requestFocus()
    }

    private fun block(yiddish: Boolean): LinearLayout {
        val p = palette
        val card = sectionCard(this).also { it.setPadding(dp(12f), dp(8f), dp(12f), dp(10f)) }
        (card.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(8f)
        fun text(t: String, color: Int = p.text, size: Float = 13f, bold: Boolean = false) = AppCompatTextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            setLineSpacing(dp(2f).toFloat(), 1f)
            setPadding(0, dp(3f), 0, dp(3f))
            applyBidi()
        }
        card.addView(text(if (yiddish) "קנעפלעך" else "Keys", p.sub, 11f, bold = true))
        for ((key, en, yi) in keys) {
            card.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = if (yiddish || prefs.rtlGrid) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                setPadding(0, dp(1f), 0, dp(1f))
                addView(text(if (yiddish) yi else en).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(AppCompatTextView(this@HelpActivity).apply {
                    text = if (yiddish) Ui.tr(2, key) else key
                    textSize = 13f; setTextColor(p.text); setTypeface(typeface, Typeface.BOLD); maxLines = 1
                    textDirection = View.TEXT_DIRECTION_LTR // "4  6" stays in key order
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(46f), LinearLayout.LayoutParams.WRAP_CONTENT)
                })
            })
        }
        for ((head, en, yi) in notes) {
            card.addView(text(head, p.sub, 11f, bold = true).apply { setPadding(0, dp(8f), 0, dp(2f)) })
            card.addView(text(if (yiddish) yi else en))
        }
        return card
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) scroll.smoothScrollBy(0, dp(if (code == KeyEvent.KEYCODE_DPAD_DOWN) 40f else -40f))
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
