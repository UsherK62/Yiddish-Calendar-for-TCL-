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
    /**
     * Left soft key. Keypad Android phones differ: SOFT_LEFT (TCL), MENU (many ROMs), F1 (some Kyocera/LG
     * builds). All are treated the same.
     */
    fun isLeft(code: Int) = code == KeyEvent.KEYCODE_SOFT_LEFT || code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_F1

    /** Right soft key: SOFT_RIGHT (TCL, Sonim XP3), F2 (some Kyocera/LG builds). BACK is handled separately. */
    fun isRight(code: Int) = code == KeyEvent.KEYCODE_SOFT_RIGHT || code == KeyEvent.KEYCODE_F2

    fun isCenter(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER

    fun isSoft(code: Int) = isLeft(code) || isRight(code) || code == KeyEvent.KEYCODE_BACK
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
        UiDir.rtl = prefs.rtlGrid
        setTheme(if (prefs.theme == 1) R.style.Theme_YC_Light else R.style.Theme_YC_Dark)
        if (android.os.Build.VERSION.SDK_INT < 26) theme.applyStyle(R.style.YC_SystemFont, true)
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
        UiDir.rtl = rtl
        window?.decorView?.applyUiDir(rtl)
        findViewById<View>(R.id.softBar)?.layoutDirection = View.LAYOUT_DIRECTION_LTR
        findViewById<View>(R.id.header)?.applyUiDir(rtl)
        findViewById<TextView>(R.id.headerTitle)?.applyBidi()
        findViewById<TextView>(R.id.headerRight)?.applyBidiEnd()
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
    fun setSoftKeys(left: String, center: String, right: String) {
        val bar = findViewById<View>(R.id.softBar)
        bindBar(bar, tr(left), tr(center), tr(right))
        // Touch phones: tapping a label does what its key does (D-pad/keys are unchanged).
        bindBarTaps(bar,
            left = if (left.isEmpty()) null else ({ onLeftSoft() }),
            center = if (center.isEmpty()) null else ({ pressKey(this::dispatchKeyEvent, KeyEvent.KEYCODE_DPAD_CENTER) }),
            right = if (right.isEmpty()) null else ({ onRightSoft() }))
    }

    open fun onLeftSoft() {}
    open fun onRightSoft() { finish() }
    /** Hardware Back. Default is to leave the screen; MainActivity overrides this so Back is not Add. */
    open fun onBackKey() { finish() }

    /**
     * Soft keys fire ONCE per press, on whichever event arrives first:
     *  - normally on key DOWN (repeat 0); the matching UP is then swallowed;
     *  - on key UP when the phone never delivered the DOWN to the app (0.2.2 bug: some phones, e.g. the
     *    tester's Sonim, let the system take the DOWN, and 0.2.2 only acted on an UP whose DOWN it had
     *    seen, so "Add" did nothing). An UP whose press STARTED before this screen appeared (event.downTime)
     *    is ignored: that is the tail of the key press that opened this screen.
     * Long presses (repeats) are swallowed so holding a key doesn't open a screen many times.
     */
    private val softDowns = mutableSetOf<Int>()
    private var resumedAt = 0L

    override fun onPostResume() {
        super.onPostResume()
        resumedAt = android.os.SystemClock.uptimeMillis()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && SoftKeys.isCenter(code)) {
            flashSoftKey(R.id.skCenter)
        }
        if (!SoftKeys.isSoft(code)) return super.dispatchKeyEvent(event)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0 && softDowns.add(code)) fireSoft(code)
            KeyEvent.ACTION_UP -> if (!softDowns.remove(code) && startedHere(event) && !event.isCanceled) fireSoft(code)
        }
        return true // consume DOWN and UP so nothing else (e.g. AppCompat's MENU handling) reacts
    }

    /** True when the key press began while this screen was already showing. */
    // downTime <= 0: some phones synthesise a lone UP with no down time; it can't be a leftover press.
    fun startedHere(event: KeyEvent) = resumedAt > 0 && (event.downTime <= 0 || event.downTime > resumedAt)

    private fun fireSoft(code: Int) {
        when {
            SoftKeys.isLeft(code) -> { flashSoftKey(R.id.skLeft); onLeftSoft() }
            SoftKeys.isRight(code) -> { flashSoftKey(R.id.skRight); onRightSoft() }
            else -> { flashSoftKey(R.id.skRight); onBackKey() }
        }
    }

    /** Briefly dim the matching soft-key label. Safe to call twice (tap + key) — Motion.pressFlash debounces. */
    fun flashSoftKey(id: Int) {
        val v = findViewById<View>(id) ?: return
        Motion.pressFlash(v, this)
    }

    /** "Settings", plus the English word in Yiddish mode so it can always be found (language escape). */
    fun settingsLabel(): String = if (prefs.hebrewText >= 2) "הגדרות · Settings" else getString(R.string.settings)

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
        /**
         * The bar is always left-to-right and each label is pinned with ABSOLUTE gravity over its key:
         * left label flush left (left soft key), right label flush right (right soft key), center label
         * centred (OK). 0.2.1 bug: applyBidi() set textAlignment=viewStart, which overrode gravity=end, so
         * the right label ("Add"/"Back") sat next to the centre pill instead of at the far right.
         */
        fun bindBar(bar: View, left: String, center: String, right: String) {
            bar.layoutDirection = View.LAYOUT_DIRECTION_LTR
            fun TextView.pin(g: Int) {
                gravity = g or android.view.Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_GRAVITY
                textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            }
            bar.findViewById<TextView>(R.id.skLeft).apply { text = left; pin(android.view.Gravity.LEFT) }
            bar.findViewById<TextView>(R.id.skRight).apply { text = right; pin(android.view.Gravity.RIGHT) }
            bar.findViewById<TextView>(R.id.skCenter).apply {
                text = center
                pin(android.view.Gravity.CENTER_HORIZONTAL)
                visibility = if (center.isEmpty()) View.INVISIBLE else View.VISIBLE
            }
        }

        /**
         * Touch support for the soft-key bar: a tap on a label runs [left]/[center]/[right]; null = no tap.
         * The labels never take focus, so D-pad focus and the look of the bar are unchanged.
         */
        fun bindBarTaps(bar: View, left: (() -> Unit)?, center: (() -> Unit)?, right: (() -> Unit)?) {
            fun wire(id: Int, f: (() -> Unit)?) = bar.findViewById<View>(id).apply {
                isFocusable = false
                if (f == null) {
                    setOnClickListener(null)
                    isClickable = false
                    // Keep the centre pill (with ripple) in the layout; clear leftover ripples on the side labels.
                    if (id != R.id.skCenter) background = null
                } else {
                    setOnClickListener { Motion.pressFlash(this, context); f() }
                    isClickable = true
                    if (id == R.id.skCenter) {
                        if (background !is android.graphics.drawable.RippleDrawable) {
                            background = context.getDrawable(R.drawable.pill_ripple)
                        }
                    } else if (background !is android.graphics.drawable.RippleDrawable) {
                        background = Motion.softKeyRipple(context, resources.displayMetrics.density * 6f)
                    }
                }
                importantForAccessibility = if (f == null) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            wire(R.id.skLeft, left); wire(R.id.skCenter, center); wire(R.id.skRight, right)
        }

        /** Sends one fresh key press (DOWN + UP, new downTime) through [dispatch], as if the key was pressed now. */
        fun pressKey(dispatch: (KeyEvent) -> Boolean, code: Int) {
            val t = android.os.SystemClock.uptimeMillis() + 1 // strictly after any screen start time
            dispatch(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
            dispatch(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        }
    }
}
