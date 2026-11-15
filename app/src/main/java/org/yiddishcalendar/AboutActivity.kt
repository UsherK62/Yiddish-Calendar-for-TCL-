package org.yiddishcalendar

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
        setSoftKeys("", "", getString(R.string.sk_back))
        scroll = findViewById(R.id.scroll)
        scroll.isFocusable = true
        val content = findViewById<LinearLayout>(R.id.content)
        content.setPadding(0, dp(8f), 0, dp(8f))
        val version = BuildConfig.VERSION_NAME
        val p = palette
        val card = sectionCard(this).also { it.setPadding(dp(12f), dp(10f), dp(12f), dp(12f)) }
        content.addView(card)
        card.addView(TextView(this).apply {
            text = getString(R.string.app_name) + if (version.isEmpty()) "" else "  $version"
            textSize = 16f
            setTextColor(p.text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8f))
            applyBidi()
        })
        for (para in listOf(
            "Offline, no ads, no internet.",
            "Keys: 4/6 month · 1/3 year · 0 today · * go to date · # Hebrew/Gregorian",
            "Credits: KosherJava (LGPL), Calendar by kizitonwose (MIT), Noto Sans Hebrew (OFL)",
        )) {
            card.addView(TextView(this).apply {
                text = para
                textSize = 13f
                setTextColor(p.text)
                includeFontPadding = false
                setLineSpacing(dp(2f).toFloat(), 1f)
                setPadding(0, dp(4f), 0, dp(4f))
                applyBidi()
            })
        }
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
}
