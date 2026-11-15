package org.yiddishcalendar

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView
import kotlin.math.min

/** ScrollView that wraps its content, but never grows past [maxHeightPx] (long lists stay scrollable). */
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ScrollView(context, attrs, defStyle) {
    var maxHeightPx: Int = Int.MAX_VALUE
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cap = if (maxHeightPx == Int.MAX_VALUE) {
            heightMeasureSpec
        } else {
            val size = MeasureSpec.getSize(heightMeasureSpec)
            val mode = MeasureSpec.getMode(heightMeasureSpec)
            val limited = if (mode == MeasureSpec.UNSPECIFIED) maxHeightPx else min(size, maxHeightPx)
            MeasureSpec.makeMeasureSpec(limited, MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, cap)
    }
}
