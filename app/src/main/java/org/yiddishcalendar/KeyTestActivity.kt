package org.yiddishcalendar

import androidx.appcompat.widget.AppCompatTextView
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Key test (About → left soft key "Keys"). Shows the code of every key the phone sends, so a tester
 * whose soft keys don't work can tell us exactly what their phone sends (e.g. "SOFT_RIGHT 2", "F2 132",
 * "BACK 4", or nothing at all). Every key is only shown, never acted on. Leave with Back, or by
 * pressing the same key 3 times in a row.
 */
class KeyTestActivity : BaseActivity() {
    private lateinit var log: TextView
    private val lines = ArrayDeque<String>()
    private var lastCode = -1
    private var sameCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day)
        setHeader("Key test")
        setSoftKeys("", "", "")
        val content = findViewById<LinearLayout>(R.id.content)
        content.addView(AppCompatTextView(this).apply {
            text = tr("Press any key to see its code. Back, or the same key 3 times, leaves.")
            textSize = 12f; setTextColor(palette.sub); setPadding(dp(12f), dp(6f), dp(12f), dp(6f)); applyBidi()
        })
        log = AppCompatTextView(this).apply {
            textSize = 15f; setTextColor(palette.text); setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
            textDirection = android.view.View.TEXT_DIRECTION_LTR
            textAlignment = android.view.View.TEXT_ALIGNMENT_TEXT_START
        }
        content.addView(log)
    }

    private fun name(code: Int): String = when (code) {
        KeyEvent.KEYCODE_SOFT_LEFT -> "SOFT_LEFT"; KeyEvent.KEYCODE_SOFT_RIGHT -> "SOFT_RIGHT"
        KeyEvent.KEYCODE_MENU -> "MENU"; KeyEvent.KEYCODE_BACK -> "BACK"
        KeyEvent.KEYCODE_F1 -> "F1"; KeyEvent.KEYCODE_F2 -> "F2"
        KeyEvent.KEYCODE_DPAD_CENTER -> "OK"; KeyEvent.KEYCODE_ENTER -> "ENTER"
        KeyEvent.KEYCODE_CALL -> "CALL"; KeyEvent.KEYCODE_ENDCALL -> "ENDCALL"
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (code == lastCode) sameCount++ else { lastCode = code; sameCount = 1 }
            if (code == KeyEvent.KEYCODE_BACK || sameCount >= 3) { finish(); return true }
        }
        if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
            val action = if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"
            lines.addFirst("${name(code)}  $code  $action  scan ${event.scanCode}")
            while (lines.size > 8) lines.removeLast()
            log.text = lines.joinToString("\n")
        }
        return true
    }
}
