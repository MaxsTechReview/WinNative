package com.winlator.cmod.shared.ui.focus

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewTreeObserver

class ControllerFocusOverlay(private val activity: Activity) {
    private val density = activity.resources.displayMetrics.density
    private val strokePx = (2f * density).toInt().coerceAtLeast(2)
    private val padPx = (3f * density).toInt()

    private val highlight =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f * density
            setColor(Color.TRANSPARENT)
            setStroke(strokePx, 0xFF00D7F5.toInt())
        }

    private var enabled = false
    private var shown = false
    private var tracked: View? = null
    private val loc = IntArray(2)
    private val decorLoc = IntArray(2)
    private val bounds = Rect()

    private val focusListener =
        ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus -> update(newFocus) }

    private val preDrawListener =
        ViewTreeObserver.OnPreDrawListener {
            if (enabled && tracked != null) reposition()
            true
        }

    fun attach() {
        val vto = activity.window.decorView.viewTreeObserver
        vto.addOnGlobalFocusChangeListener(focusListener)
        vto.addOnPreDrawListener(preDrawListener)
    }

    fun detach() {
        val vto = activity.window.decorView.viewTreeObserver
        if (vto.isAlive) {
            vto.removeOnGlobalFocusChangeListener(focusListener)
            vto.removeOnPreDrawListener(preDrawListener)
        }
        hide()
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (value) update(activity.window.decorView.findFocus()) else hide()
    }

    private fun update(view: View?) {
        if (!enabled || view == null || !isHighlightable(view)) {
            hide()
            return
        }
        tracked = view
        show()
        reposition()
    }

    private fun isHighlightable(view: View): Boolean {
        if (!view.isShown) return false
        return view.javaClass.simpleName != "AndroidComposeView"
    }

    private fun show() {
        if (shown) return
        activity.window.decorView.overlay.add(highlight)
        shown = true
    }

    private fun hide() {
        tracked = null
        if (!shown) return
        activity.window.decorView.overlay.remove(highlight)
        shown = false
        activity.window.decorView.invalidate()
    }

    private fun reposition() {
        val view = tracked ?: return
        if (!view.isShown) {
            hide()
            return
        }
        val decor = activity.window.decorView
        decor.getLocationInWindow(decorLoc)
        view.getLocationInWindow(loc)
        val left = loc[0] - decorLoc[0] - padPx
        val top = loc[1] - decorLoc[1] - padPx
        val right = left + view.width + padPx * 2
        val bottom = top + view.height + padPx * 2
        if (bounds.left != left || bounds.top != top || bounds.right != right || bounds.bottom != bottom) {
            bounds.set(left, top, right, bottom)
            highlight.bounds = bounds
            decor.invalidate()
        }
    }
}
