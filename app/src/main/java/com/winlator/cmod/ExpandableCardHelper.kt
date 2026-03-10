package com.winlator.cmod

import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet

object ExpandableCardHelper {

    fun applyTransition(
        itemRoot: View,
        chevron: ImageView,
        contentView: View,
        expanded: Boolean,
        fadeTarget: View = contentView,
        sceneRoot: ViewGroup? = null
    ) {
        val previousTag = chevron.tag as? Boolean

        if (previousTag != null && previousTag != expanded) {
            val transition = TransitionSet().apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                addTransition(ChangeBounds())
                addTransition(Fade().apply { addTarget(fadeTarget) })
                duration = 250L
                interpolator = AccelerateDecelerateInterpolator()
            }
            TransitionManager.beginDelayedTransition(
                sceneRoot ?: itemRoot as ViewGroup, transition
            )
        }

        updateChevronRotation(chevron, expanded, previousTag)
        contentView.visibility = if (expanded) View.VISIBLE else View.GONE
    }

    fun setupClickListeners(vararg views: View, onClick: () -> Unit) {
        val listener = View.OnClickListener { onClick() }
        views.forEach { it.setOnClickListener(listener) }
    }

    private fun updateChevronRotation(chevron: ImageView, expanded: Boolean, previousExpanded: Boolean?) {
        val targetRotation = if (expanded) 90f else 0f

        if (previousExpanded == expanded) return

        chevron.animate().cancel()

        if (previousExpanded == null) {
            chevron.rotation = targetRotation
        } else {
            chevron.animate()
                .rotation(targetRotation)
                .setDuration(250L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        chevron.tag = expanded
    }
}
