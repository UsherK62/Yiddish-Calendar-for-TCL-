package org.yiddishcalendar

import androidx.appcompat.widget.AppCompatTextView
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A form row that changes its value with the D-pad: Left = previous, Right or OK = next.
 * Label small + value large. [extraKeys] lets a row use number keys (e.g. 1/3 = hour).
 * Shows ‹ › only while focused. Used on the add-event form, not in Settings.
 */
class ChoiceRow(
    private val act: BaseActivity,
    private val title: String,
    private val value: () -> String,
    private val prev: () -> Unit,
    private val next: () -> Unit,
    val hint: String = "◀ ▶ or OK to change",
    private val extraKeys: ((Int) -> Boolean)? = null,
    private val onChanged: () -> Unit = {},
) {
    var onFocus: ((Boolean) -> Unit)? = null
    private val titleView: TextView
    private val valueView: TextView
    private val arrows: TextView
    val view: LinearLayout = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = act.dp(48f)
        isFocusable = true
        isClickable = true
        setBackgroundResource(R.drawable.row_bg)
        setPadding(act.dp(12f), act.dp(7f), act.dp(12f), act.dp(7f))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.setMargins(act.dp(4f), act.dp(3f), act.dp(4f), 0) }
    }

    init {
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleView = AppCompatTextView(act).apply {
            text = act.tr(title)
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = true
            maxLines = 1
            applyBidi()
        }
        valueView = AppCompatTextView(act).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            applyBidi()
        }
        col.addView(titleView); col.addView(valueView)
        arrows = AppCompatTextView(act).apply { text = "‹ ›"; textSize = 16f; visibility = View.INVISIBLE; textDirection = View.TEXT_DIRECTION_LTR }
        view.addView(col); view.addView(arrows)
        view.setOnFocusChangeListener { _, has ->
            arrows.visibility = if (has) View.VISIBLE else View.INVISIBLE
            paint()
            onFocus?.invoke(has)
        }
        view.setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when {
                extraKeys?.invoke(code) == true -> { refresh(); onChanged(); true }
                code == KeyEvent.KEYCODE_DPAD_LEFT -> { prev(); refresh(); onChanged(); true }
                code == KeyEvent.KEYCODE_DPAD_RIGHT || SoftKeys.isCenter(code) -> { next(); refresh(); onChanged(); true }
                else -> false
            }
        }
        refresh()
        paint()
    }

    fun refresh() { valueView.text = act.tr(value()) }

    private fun paint() {
        val p = act.palette
        titleView.setTextColor(p.sub)
        valueView.setTextColor(p.text)
        arrows.setTextColor(p.text)
    }
}

/** Cycles through a list with wrap-around. */
fun <T> List<T>.after(cur: T, step: Int): T {
    val i = indexOf(cur).coerceAtLeast(0)
    return this[((i + step) % size + size) % size]
}

/**
 * One Settings row inside a section card. Reading side (right in RTL): the title, and under it the
 * current value or a short note, so every row fits a 240dp screen without wrapping. Far side (left
 * in RTL): an ON/OFF switch for two-choice settings, or a chevron for pick lists / pages.
 * Only OK/center acts. No Left/Right cycling.
 */
class SettingRow(
    private val act: BaseActivity,
    title: String,
    val kind: Kind,
    private val value: () -> String,
    private val isOn: () -> Boolean = { false },
    note: String = "",
    private val onOk: () -> Unit,
) {
    enum class Kind { TOGGLE, CHOICE, ACTION }
    var onFocus: ((Boolean) -> Unit)? = null
    private val titleView: TextView
    private val subView: TextView
    private val note = act.tr(note)
    private val switchView: View
    private var rowRtl = UiDir.rtl
    private val chevronView: ImageView
    val view: LinearLayout = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = act.dp(40f)
        isFocusable = true
        isClickable = true
        setBackgroundResource(R.drawable.row_bg)
        setPadding(act.dp(10f), act.dp(5f), act.dp(10f), act.dp(5f))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    init {
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleView = AppCompatTextView(act).apply {
            text = act.tr(title)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            applyBidi()
        }
        subView = AppCompatTextView(act).apply {
            textSize = 11.5f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            setPadding(0, act.dp(2f), 0, 0)
            applyBidi()
        }
        col.addView(titleView)
        col.addView(subView)
        switchView = View(act).apply {
            layoutParams = LinearLayout.LayoutParams(act.dp(34f), act.dp(20f)).also { it.marginStart = act.dp(10f) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        chevronView = ImageView(act).apply {
            setImageResource(R.drawable.ic_chevron)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(act.dp(16f), act.dp(16f)).also { it.marginStart = act.dp(8f) }
        }
        // The switch / chevron comes AFTER the words. English label -> row runs left-to-right, label on
        // the left and switch at the right end; Hebrew/Yiddish label (Everything mode) -> mirrored.
        // Pick-list/page rows do the same: the chevron comes after the words, pointing ">" for English
        // (ic_chevron is auto-mirrored, so it points "<" at the left end of a Yiddish row).
        rowRtl = startsRtl(titleView.text)
        view.layoutDirection = if (rowRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        view.addView(col)
        view.addView(switchView)
        view.addView(chevronView)
        view.setOnFocusChangeListener { _, has ->
            paint()
            onFocus?.invoke(has)
        }
        view.setOnClickListener { onOk(); refresh() }
        var centerDown = false
        view.setOnKeyListener { _, code, ev ->
            if (!SoftKeys.isCenter(code)) return@setOnKeyListener false
            if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) centerDown = true
            if (ev.action == KeyEvent.ACTION_UP && centerDown) { centerDown = false; onOk(); refresh() }
            true
        }
        refresh()
    }

    fun refresh() { paint() }

    private fun paint() {
        val p = act.palette
        titleView.setTextColor(p.text)
        subView.setTextColor(p.sub)
        val sub = when (kind) {
            Kind.TOGGLE -> note
            Kind.CHOICE -> act.tr(value())
            Kind.ACTION -> note
        }
        subView.text = sub
        subView.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
        chevronView.visibility = if (kind == Kind.TOGGLE) View.GONE else View.VISIBLE
        chevronView.setColorFilter(p.sub)
        switchView.visibility = if (kind == Kind.TOGGLE) View.VISIBLE else View.GONE
        if (kind == Kind.TOGGLE) {
            val on = isOn()
            switchView.background = switchDrawable(on)
            switchView.contentDescription = act.tr(if (on) "ON" else "OFF")
        }
    }

    /** Small switch: blue track with the knob toward the text when ON, gray outlined track when OFF. */
    private fun switchDrawable(on: Boolean): android.graphics.drawable.Drawable {
        val p = act.palette
        val track = GradientDrawable().apply {
            cornerRadius = act.dp(10f).toFloat()
            if (on) setColor(p.header) else { setColor(p.cell); setStroke(act.dp(1.5f), p.dim) }
        }
        val knob = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (on) p.headerText else p.dim)
        }
        // Fixed 34x20dp switch, knob 14dp. Only setLayerInset (API 1) so it works on Android 5+.
        return android.graphics.drawable.LayerDrawable(arrayOf(track, knob)).apply {
            val m = act.dp(3f); val far = act.dp(34f - 3f - 14f)
            // Standard switch: ON = knob at the row's end side (right in a left-to-right row), OFF = toward the words.
            val knobRight = on != rowRtl
            if (knobRight) setLayerInset(1, far, m, m, m) else setLayerInset(1, m, m, far, m)
        }
    }
}

/** True when the first strong letter is Hebrew/Yiddish (same rule as textDirection=firstStrong). */
fun startsRtl(s: CharSequence): Boolean {
    for (c in s) {
        if (c in '\u0590'..'\u05FF') return true
        if (Character.isLetter(c)) return false
    }
    return false
}

/** Section header: small caps text on the reading side, lined up with the row titles. No icon. */
fun sectionHeader(act: BaseActivity, text: String, first: Boolean = false): View =
    AppCompatTextView(act).apply {
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        this.text = act.tr(text)
        textSize = 11f
        setTextColor(act.palette.sub)
        setTypeface(typeface, Typeface.BOLD)
        isAllCaps = true
        setPadding(act.dp(18f), act.dp(if (first) 6f else 10f), act.dp(18f), act.dp(4f))
        applyBidi()
    }

/** Group card that holds a section's rows: one hairline outline; the rows inside have none. */
fun sectionCard(act: BaseActivity): LinearLayout = LinearLayout(act).apply {
    orientation = LinearLayout.VERTICAL
    isFocusable = false
    setBackgroundResource(R.drawable.card_bg)
    setPadding(act.dp(3f), act.dp(3f), act.dp(3f), act.dp(3f))
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .also { it.setMargins(act.dp(8f), 0, act.dp(8f), 0) }
}
