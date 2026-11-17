package org.yiddishcalendar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.View

/**
 * Short, subtle touch motion. All timings are 150–250 ms (close is a bit quicker).
 * When the system "Remove animations" setting is on (ANIMATOR_DURATION_SCALE = 0),
 * nothing is animated. Host tests stay instant unless a test turns [overrideEnabled] on.
 */
object Motion {
    const val SHOW_MS = 200L
    const val HIDE_MS = 120L
    const val POP_SCALE = 0.92f
    const val FOCUS_MS = 140L
    const val TODAY_PULSE_MS = 400L
    const val SOFT_PRESS_MS = 120L
    const val SOFT_PRESS_ALPHA = 0.4f
    private const val TAG_FLASH_AT = 0x7f011001

    /** Host tests: true/false forces motion on or off; null follows the usual rules. */
    var overrideEnabled: Boolean? = null

    fun animatorScale(ctx: Context): Float = try {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (_: Exception) {
        1f
    }

    fun enabled(ctx: Context): Boolean {
        overrideEnabled?.let { return it }
        if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) return false
        return animatorScale(ctx) != 0f
    }

    fun duration(ctx: Context, ms: Long): Long = if (enabled(ctx)) ms else 0L

    fun rippleColor(ctx: Context): Int {
        val tv = TypedValue()
        if (ctx.theme.resolveAttribute(R.attr.ycRipple, tv, true) && tv.data != 0) return tv.data
        val focus = TypedValue()
        ctx.theme.resolveAttribute(R.attr.ycFocus, focus, true)
        return focus.data and 0x00FFFFFF or 0x33000000
    }

    /** Colour fill with a ripple on top. The selection ring is drawn by the view itself, so it stays visible. */
    fun cellBackground(ctx: Context, fillColor: Int, cornerPx: Float): Drawable {
        val fill = GradientDrawable().apply {
            cornerRadius = cornerPx
            setColor(fillColor)
        }
        val mask = GradientDrawable().apply {
            cornerRadius = cornerPx
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor(ctx)), fill, mask)
    }

    fun softKeyRipple(ctx: Context, cornerPx: Float): Drawable {
        val mask = GradientDrawable().apply {
            cornerRadius = cornerPx
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor(ctx)), null, mask)
    }

    fun wrapRipple(ctx: Context, content: Drawable?, cornerPx: Float): Drawable {
        val mask = GradientDrawable().apply {
            cornerRadius = cornerPx
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor(ctx)), content, mask)
    }

    fun clear(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    /** Briefly dim a soft-key label when its key or a tap on the label is pressed. */
    fun pressFlash(view: View, ctx: Context) {
        if (!enabled(ctx) || view.visibility != View.VISIBLE) return
        val now = android.os.SystemClock.uptimeMillis()
        val last = (view.getTag(TAG_FLASH_AT) as? Long) ?: 0L
        if (now - last < 50) return
        view.setTag(TAG_FLASH_AT, now)
        view.animate().cancel()
        view.alpha = SOFT_PRESS_ALPHA
        view.animate().alpha(1f).setDuration(duration(ctx, SOFT_PRESS_MS)).start()
    }
}
