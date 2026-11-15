package org.yiddishcalendar

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
        titleView = TextView(act).apply {
            text = act.tr(title)
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = true
            maxLines = 1
            applyBidi()
        }
        valueView = TextView(act).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            applyBidi()
        }
        col.addView(titleView); col.addView(valueView)
        arrows = TextView(act).apply { text = "‹ ›"; textSize = 16f; visibility = View.INVISIBLE; textDirection = View.TEXT_DIRECTION_LTR }
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
 * One Settings row inside a section card: title (and optional note) at the start,
 * value or an ON/OFF switch at the end. Only OK/center acts. No Left/Right cycling.
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
    private val noteView: TextView?
    private val valueView: TextView
    private val chevronView: ImageView
    val view: LinearLayout = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = act.dp(42f)
        isFocusable = true
        isClickable = true
        setBackgroundResource(R.drawable.row_bg)
        setPadding(act.dp(10f), act.dp(7f), act.dp(10f), act.dp(7f))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .also { it.setMargins(act.dp(3f), act.dp(3f), act.dp(3f), 0) }
    }

    init {
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleView = TextView(act).apply {
            text = act.tr(title)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            applyBidi()
        }
        col.addView(titleView)
        noteView = if (note.isNotEmpty()) TextView(act).apply {
            text = act.tr(note)
            textSize = 11f
            maxLines = 3
            setPadding(0, act.dp(2f), 0, 0)
            applyBidi()
            col.addView(this)
        } else null
        valueView = TextView(act).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            if (kind == Kind.TOGGLE) {
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                minWidth = act.dp(46f)
                setPadding(act.dp(9f), act.dp(4f), act.dp(9f), act.dp(4f))
            } else {
                textSize = 13f
                applyBidi()
            }
        }
        chevronView = ImageView(act).apply {
            setImageResource(R.drawable.ic_chevron)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(act.dp(14f), act.dp(14f)).also { it.marginStart = act.dp(4f) }
        }
        view.addView(col)
        view.addView(valueView)
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
        noteView?.setTextColor(p.sub)
        when (kind) {
            Kind.TOGGLE -> {
                val on = isOn()
                chevronView.visibility = View.GONE
                valueView.visibility = View.VISIBLE
                valueView.text = act.tr(if (on) "ON" else "OFF")
                valueView.setTextColor(if (on) p.headerText else p.sub)
                valueView.background = GradientDrawable().apply {
                    cornerRadius = act.dp(11f).toFloat()
                    if (on) setColor(p.header) else { setColor(0); setStroke(act.dp(1.5f), p.sub) }
                }
            }
            Kind.CHOICE -> {
                chevronView.visibility = View.VISIBLE
                chevronView.setColorFilter(p.sub)
                valueView.visibility = View.VISIBLE
                valueView.text = act.tr(value())
                valueView.setTextColor(p.sub)
            }
            Kind.ACTION -> {
                chevronView.visibility = View.VISIBLE
                chevronView.setColorFilter(p.sub)
                valueView.visibility = View.GONE
            }
        }
    }
}

/** Section header with a small icon; never focusable. */
fun sectionHeader(act: BaseActivity, text: String, icon: Int, first: Boolean = false): View =
    LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setPadding(act.dp(14f), act.dp(if (first) 8f else 12f), act.dp(14f), act.dp(5f))
        addView(ImageView(act).apply {
            setImageResource(icon)
            setColorFilter(act.palette.dim)
            layoutParams = LinearLayout.LayoutParams(act.dp(16f), act.dp(16f)).also { it.marginEnd = act.dp(6f) }
        })
        addView(TextView(act).apply {
            this.text = act.tr(text)
            textSize = 11f
            setTextColor(act.palette.dim)
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = true
            applyBidi()
        })
    }

/** Rounded group card that holds a section's rows. */
fun sectionCard(act: BaseActivity): LinearLayout = LinearLayout(act).apply {
    orientation = LinearLayout.VERTICAL
    isFocusable = false
    setBackgroundResource(R.drawable.card_bg)
    setPadding(act.dp(4f), act.dp(3f), act.dp(4f), act.dp(6f))
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .also { it.setMargins(act.dp(10f), 0, act.dp(10f), 0) }
}
