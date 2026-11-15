package org.yiddishcalendar

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A full-screen keypad dialog with its own soft-key bar. Used for the Menu (numbered list),
 * event details, Go to date, About, Settings pick lists and confirmations. Never shows Material buttons.
 */
class KeyDialog(private val act: BaseActivity) :
    Dialog(act, if (act.prefs.theme == 1) R.style.YC_Dialog_Light else R.style.YC_Dialog_Dark) {
    private var title = ""
    private val items = mutableListOf<Pair<String, () -> Unit>>()
    private val lines = mutableListOf<Pair<String, String>>()
    private var leftLabel = ""; private var leftAction: (() -> Unit)? = null
    private var centerLabel = ""; private var centerAction: (() -> Unit)? = null
    private var input: EditText? = null
    private var inputHint = ""
    private var onInput: ((String) -> Unit)? = null

    private var decimal = false

    fun title(t: String) = apply { title = act.tr(t) }
    fun item(label: String, action: () -> Unit) = apply { items += act.tr(label) to action }
    /** Index of the item that is the current choice (pick list): radio dot ● on it, ○ on the others, focus starts there. -1 = plain menu. */
    private var checked = -1
    private var itemNotes: List<String> = emptyList()
    /** Pick list: one item per option; OK (or its number) selects and closes; Back cancels. */
    fun choices(options: List<String>, current: Int, notes: List<String> = emptyList(), pick: (Int) -> Unit) = apply {
        checked = current; itemNotes = notes.map { act.tr(it) }
        options.forEachIndexed { i, o -> items += act.tr(o) to { pick(i) } }
    }
    fun line(label: String, value: String = "") = apply { lines += act.tr(label) to value }
    private val paras = mutableListOf<String>()
    /** A wrapped paragraph with tight line spacing (About, help text). */
    fun para(text: String) = apply { paras += act.tr(text) }
    fun left(label: String, action: () -> Unit) = apply { leftLabel = act.tr(label); leftAction = action }
    fun center(label: String, action: () -> Unit) = apply { centerLabel = act.tr(label); centerAction = action }
    fun numberInput(hint: String, done: (String) -> Unit) = apply { inputHint = hint; onInput = done }
    /** Signed decimal entry for coordinates: * types ".", # toggles the minus sign. */
    fun decimalInput(hint: String, initial: String = "", done: (String) -> Unit) =
        apply { inputHint = hint; onInput = done; decimal = true; inputInitial = initial }
    private var inputInitial = ""
    private var preview: ((String) -> String)? = null
    /** Live line under the input (e.g. "11219 → Brooklyn NY"), recomputed on every change. */
    fun preview(f: (String) -> String) = apply { preview = f }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.key_dialog)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window?.decorView?.applyUiDir(act.prefs.rtlGrid)
        findViewById<View>(R.id.softBar)?.layoutDirection = View.LAYOUT_DIRECTION_LTR
        // Cap the list so a long pick list scrolls, while short menus wrap tightly above the bar.
        val screen = act.resources.displayMetrics.heightPixels
        findViewById<MaxHeightScrollView>(R.id.dlgScroll).maxHeightPx =
            screen - act.dp(30f + 48f + 16f)
        val p = act.palette
        findViewById<TextView>(R.id.dlgTitle).apply { text = title; visibility = if (title.isEmpty()) View.GONE else View.VISIBLE; applyBidi() }
        val body = findViewById<LinearLayout>(R.id.dlgBody)
        for (t in paras) body.addView(TextView(act).apply {
            text = t; textSize = 12.5f; setTextColor(p.text)
            includeFontPadding = false; setLineSpacing(0f, 1.0f)
            setPadding(act.dp(6f), act.dp(2f), act.dp(6f), act.dp(3f))
            applyBidi()
        })

        for ((label, value) in lines) {
            body.addView(LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(act.dp(6f), act.dp(3f), act.dp(6f), act.dp(3f))
                addView(TextView(act).apply {
                    text = label; textSize = if (value.isEmpty()) 14f else 13f; setTextColor(if (value.isEmpty()) p.text else p.sub)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    applyBidi()
                })
                if (value.isNotEmpty()) addView(TextView(act).apply {
                    text = value; textSize = 14f; setTextColor(p.text)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    gravity = android.view.Gravity.END
                    applyBidi()
                })
            })
        }

        items.forEachIndexed { i, (label, action) ->
            body.addView(LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.row_bg)
                isFocusable = true; isClickable = true
                minimumHeight = act.dp(36f)
                setPadding(act.dp(10f), act.dp(4f), act.dp(10f), act.dp(4f))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = act.dp(2f) }
                if (checked >= 0) addView(TextView(act).apply {
                    text = if (i == checked) "●" else "○"; textSize = 16f; setTextColor(if (i == checked) p.text else p.sub)
                    setPaddingRelative(0, 0, act.dp(8f), 0)
                })
                addView(LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(act).apply {
                        text = label; textSize = 15f; setTextColor(p.text); setTypeface(typeface, android.graphics.Typeface.BOLD)
                        applyBidi()
                    })
                    itemNotes.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { n ->
                        addView(TextView(act).apply { text = n; textSize = 11f; setTextColor(p.sub); applyBidi() })
                    }
                })
                if (i < 9 && checked < 0) addView(TextView(act).apply { text = (i + 1).toString(); textSize = 12f; setTextColor(p.sub) })
                setOnClickListener { dismiss(); action() }
            })
        }

        if (onInput != null) {
            input = EditText(act).apply {
                hint = inputHint; textSize = 18f; setTextColor(p.text); setHintTextColor(p.dim)
                inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED else InputType.TYPE_CLASS_NUMBER
                setText(inputInitial); setSelection(inputInitial.length)
                setBackgroundResource(R.drawable.row_bg)
                setPadding(act.dp(10f), act.dp(6f), act.dp(10f), act.dp(6f))
                applyBidi()
            }
            body.addView(input)
            preview?.let { f ->
                val pv = TextView(act).apply {
                    textSize = 14f; setTextColor(p.text); setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(act.dp(10f), act.dp(4f), act.dp(10f), 0)
                    text = f(inputInitial)
                    applyBidi()
                }
                body.addView(pv)
                input?.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) { pv.text = f(s?.toString().orEmpty()) }
                })
            }
        }

        val right = act.tr(act.getString(R.string.sk_back))
        val center = when {
            centerLabel.isNotEmpty() -> centerLabel
            items.isNotEmpty() -> act.tr(act.getString(R.string.sk_select))
            onInput != null -> act.tr(act.getString(R.string.sk_go))
            else -> ""
        }
        BaseActivity.bindBar(findViewById(R.id.softBar), leftLabel, center, right)
        (input ?: body.getChildAt(paras.size + lines.size + checked.coerceAtLeast(0)))?.requestFocus()
    }

    /** Keys whose DOWN reached this dialog. An UP without its DOWN (e.g. the OK press that opened the dialog) is ignored. */
    private val downs = mutableSetOf<Int>()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) downs += code
        val up = event.action == KeyEvent.ACTION_UP && downs.remove(code)
        val inp = input
        // Long text (no list, no input box): D-pad up/down scrolls the body; the scrollbar stays visible.
        if (items.isEmpty() && inp == null &&
            (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
            if (event.action == KeyEvent.ACTION_DOWN) findViewById<android.widget.ScrollView>(R.id.dlgScroll)
                .smoothScrollBy(0, act.dp(if (code == KeyEvent.KEYCODE_DPAD_DOWN) 40f else -40f))
            return true
        }
        if (decimal && inp != null && (code == KeyEvent.KEYCODE_STAR || code == KeyEvent.KEYCODE_POUND)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val t = inp.text.toString()
                val n = if (code == KeyEvent.KEYCODE_STAR) { if ('.' in t) t else "$t." }
                        else { if (t.startsWith("-")) t.drop(1) else "-$t" }
                inp.setText(n); inp.setSelection(n.length)
            }
            return true
        }
        when {
            SoftKeys.isRight(code) || code == KeyEvent.KEYCODE_BACK -> { if (up) dismiss(); return true }
            SoftKeys.isLeft(code) -> {
                if (up) { if (leftAction != null) { dismiss(); leftAction!!.invoke() } else dismiss() }
                return true
            }
            SoftKeys.isCenter(code) && (onInput != null || (items.isEmpty() && centerAction != null)) -> {
                if (up) {
                    val text = input?.text?.toString().orEmpty()
                    dismiss()
                    if (onInput != null) onInput!!(text) else centerAction!!.invoke()
                }
                return true
            }
            items.isNotEmpty() && code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_9 -> {
                val i = code - KeyEvent.KEYCODE_1
                if (up && i < items.size) { dismiss(); items[i].second() }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
