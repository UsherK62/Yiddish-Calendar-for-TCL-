package org.yiddishcalendar

import androidx.appcompat.widget.AppCompatTextView
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * One-screen About page (Menu → About and Settings → About). Fits 240x320 dp;
 * D-pad up/down scrolls if a larger font ever overflows.
 */
class AboutActivity : BaseActivity() {
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day)
        setHeader("About")
        setSoftKeys("Keys", "", getString(R.string.sk_back))
        scroll = findViewById(R.id.scroll)
        scroll.isFocusable = true
        val content = findViewById<LinearLayout>(R.id.content)
        content.setPadding(0, dp(8f), 0, dp(8f))
        val version = BuildConfig.VERSION_NAME
        val p = palette
        val card = sectionCard(this).also { it.setPadding(dp(12f), dp(10f), dp(12f), dp(12f)) }
        content.addView(card)
        card.addView(AppCompatTextView(this).apply {
            text = getString(R.string.app_name) + if (version.isEmpty()) "" else "  $version"
            textSize = 16f
            setTextColor(p.text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8f))
            applyBidi()
        })
        fun para(t: String, color: Int = p.text, size: Float = 13f) = card.addView(AppCompatTextView(this).apply {
            text = tr(t); textSize = size; setTextColor(color)
            includeFontPadding = false
            setLineSpacing(dp(2f).toFloat(), 1f)
            setPadding(0, dp(4f), 0, dp(4f))
            applyBidi()
        })
        para("Offline, no ads, no internet.")
        para("Keys", p.sub, 11f)
        // One key per line: what it does on the reading side, the key itself on the far side.
        for ((action, key) in listOf(
            "Next / previous month" to "4  6",
            "Next / previous year" to "1  3",
            "Today" to "0",
            "Go to date" to "*",
            "Hebrew / Gregorian" to "#",
        )) {
            card.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = if (prefs.rtlGrid) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR
                setPadding(0, dp(2f), 0, dp(2f))
                addView(AppCompatTextView(this@AboutActivity).apply {
                    text = tr(action); textSize = 13f; setTextColor(p.text); maxLines = 1; applyBidi()
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(AppCompatTextView(this@AboutActivity).apply {
                    text = key; textSize = 13f; setTextColor(p.text); setTypeface(typeface, Typeface.BOLD); maxLines = 1
                    textDirection = android.view.View.TEXT_DIRECTION_LTR
                    layoutParams = LinearLayout.LayoutParams(dp(34f), LinearLayout.LayoutParams.WRAP_CONTENT)
                    gravity = android.view.Gravity.CENTER
                })
            })
        }
        para("Add event", p.sub, 11f)
        para("Right soft key, or Menu, then Add event.")
        para("Language · שפראך", p.sub, 11f)
        para("Settings, top row. In Settings, press 0 for English.")
        para("Key test: press the left soft key (Keys) here.")
        para("Credits", p.sub, 11f)
        para("KosherJava (LGPL)")
        para("Calendar by kizitonwose (MIT)")
        para("Noto Sans Hebrew (OFL)")
        scroll.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                scroll.smoothScrollBy(0, dp(if (code == KeyEvent.KEYCODE_DPAD_DOWN) 40f else -40f))
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Left soft key: key test, so testers can report what their phone's keys send. */
    override fun onLeftSoft() = startActivity(android.content.Intent(this, KeyTestActivity::class.java))
}
