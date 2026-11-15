package org.yiddishcalendar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Theme colours resolved once per screen (so code-drawn views match the XML theme). */
class Palette(private val c: Context) {
    private fun attr(id: Int): Int = TypedValue().also { c.theme.resolveAttribute(id, it, true) }.data
    val bg = attr(R.attr.ycBg)
    val cell = attr(R.attr.ycCell)
    val cellShabbos = attr(R.attr.ycCellShabbos)
    val today = attr(R.attr.ycToday)
    val bar = attr(R.attr.ycBar)
    val card = attr(R.attr.ycCard)
    val pill = attr(R.attr.ycPill)
    val line = attr(R.attr.ycLine)
    val text = attr(R.attr.ycText)
    val sub = attr(R.attr.ycSub)
    val dim = attr(R.attr.ycDim)
    val focus = attr(R.attr.ycFocus)
    val header = attr(R.attr.ycHeader)
    val headerText = attr(R.attr.ycHeaderText)
}

object SoftKeys {
    /** Left soft key. Many keypad ROMs send MENU instead of SOFT_LEFT; treat both the same. */
    fun isLeft(code: Int) = code == KeyEvent.KEYCODE_SOFT_LEFT || code == KeyEvent.KEYCODE_MENU
    fun isRight(code: Int) = code == KeyEvent.KEYCODE_SOFT_RIGHT
    fun isCenter(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER
}

/**
 * Every screen: theme, a labelled soft-key bar, and soft-key handling.
 * Left soft key = menu / primary action, Right soft key = Add (grid) or Back (other screens).
 */
abstract class BaseActivity : AppCompatActivity() {
    lateinit var prefs: Prefs
    lateinit var palette: Palette
    lateinit var luach: Luach
    lateinit var events: EventRepository
    private var themeRev = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        setTheme(if (prefs.theme == 1) R.style.Theme_YC_Light else R.style.Theme_YC_Dark)
        themeRev = prefs.theme
        super.onCreate(savedInstanceState)
        palette = Palette(this)
        luach = Luach(this, prefs)
        events = EventRepository(this, prefs)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyUiDirection()
    }

    /** Whole UI follows Settings → Right-to-left. The soft-key bar stays LTR so labels match the physical keys. */
    fun applyUiDirection() {
        val rtl = prefs.rtlGrid
        window?.decorView?.applyUiDir(rtl)
        findViewById<View>(R.id.softBar)?.layoutDirection = View.LAYOUT_DIRECTION_LTR
        findViewById<View>(R.id.header)?.applyUiDir(rtl)
        findViewById<TextView>(R.id.headerTitle)?.applyBidi()
        findViewById<TextView>(R.id.headerRight)?.applyBidi()
        findViewById<TextView>(R.id.statusTitle)?.applyBidi()
        findViewById<TextView>(R.id.statusSub)?.applyBidi()
    }

    override fun onResume() {
        super.onResume()
        if (themeRev != prefs.theme) recreate()
        else applyUiDirection()
    }

    /** UI word in the user's Hebrew-text level (English unless "Everything"). */
    fun tr(s: String): String = Ui.tr(prefs.hebrewText, s)

    fun setHeader(title: String, right: String = "") {
        findViewById<TextView>(R.id.headerTitle).text = tr(title)
        findViewById<TextView>(R.id.headerRight).text = right
    }

    /** Sets the three bar labels; an empty label hides that slot (the key then does nothing visible). */
    fun setSoftKeys(left: String, center: String, right: String) =
        bindBar(findViewById(R.id.softBar), tr(left), tr(center), tr(right))

    open fun onLeftSoft() {}
    open fun onRightSoft() { finish() }
    /** Hardware Back. Default is to leave the screen; MainActivity overrides this so Back is not Add. */
    open fun onBackKey() { finish() }

    private val softDowns = mutableSetOf<Int>()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (SoftKeys.isLeft(code) || SoftKeys.isRight(code) || code == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) softDowns += code
            if (event.action == KeyEvent.ACTION_UP && softDowns.remove(code)) {
                when {
                    SoftKeys.isLeft(code) -> onLeftSoft()
                    SoftKeys.isRight(code) -> onRightSoft()
                    else -> onBackKey()
                }
            }
            return true // consume DOWN and UP so nothing else (e.g. AppCompat's MENU handling) reacts
        }
        return super.dispatchKeyEvent(event)
    }

    /** About is its own one-screen page (Menu → About and Settings → About). */
    fun about() {
        startActivity(Intent(this, AboutActivity::class.java))
    }

    fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    /** Toast text follows the string's first strong character, even when the window is RTL. */
    fun toast(msg: String, long: Boolean = false) {
        Toast.makeText(this, bidiIsolate(tr(msg)), if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }


    companion object {
        fun bindBar(bar: View, left: String, center: String, right: String) {
            bar.findViewById<TextView>(R.id.skLeft).apply { text = left; applyBidi() }
            bar.findViewById<TextView>(R.id.skRight).apply { text = right; applyBidi() }
            bar.findViewById<TextView>(R.id.skCenter).apply {
                text = center
                applyBidi()
                visibility = if (center.isEmpty()) View.INVISIBLE else View.VISIBLE
            }
        }
    }
}
