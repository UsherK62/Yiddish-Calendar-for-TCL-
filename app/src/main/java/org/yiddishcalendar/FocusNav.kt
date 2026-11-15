package org.yiddishcalendar

import android.view.View
import android.widget.ScrollView

/** Smoothly keep the focused row on screen, with a little context above and below. */
object FocusNav {
    fun ensureVisible(scroll: ScrollView, child: View, padPx: Int) {
        scroll.post {
            if (!child.isAttachedToWindow) return@post
            var y = 0
            var v: View = child
            while (v.parent is View && v.parent !== scroll) {
                y += v.top
                v = v.parent as View
            }
            y += v.top
            val top = (y - padPx).coerceAtLeast(0)
            val bottom = y + child.height + padPx
            val visTop = scroll.scrollY
            val visBot = visTop + scroll.height
            when {
                top < visTop -> scroll.smoothScrollTo(0, top)
                bottom > visBot -> scroll.smoothScrollTo(0, (bottom - scroll.height).coerceAtLeast(0))
            }
        }
    }
}
