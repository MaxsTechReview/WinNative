/* Full-screen progress dialog used while content packages download or install from the Components screen. */
package com.winlator.cmod.core

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Dialog
import android.view.Window
import android.view.animation.LinearInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import com.winlator.cmod.R

class ContentTransferDialog(
    private val activity: Activity
) {
    private var dialog: Dialog? = null
    private var progressAnimator: ObjectAnimator? = null

    companion object {
        const val PROGRESS_SCALE = 1000
    }

    private fun ensureDialog() {
        if (dialog != null) return
        dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            setContentView(R.layout.content_transfer_dialog)
        }
    }

    fun show(title: String, message: String, indeterminate: Boolean = false) {
        activity.runOnUiThread {
            ensureDialog()
            update(title, message, if (indeterminate) null else 0, indeterminate)
            if (dialog?.isShowing != true) {
                dialog?.show()
                dialog?.findViewById<android.view.View>(R.id.TransferCard)
                    ?.apply {
                        alpha = 0f
                        scaleX = 0.96f
                        scaleY = 0.96f
                        animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(170L)
                            .start()
                    }
            }
        }
    }

    fun update(
        title: String,
        message: String,
        progress: Int? = null,
        indeterminate: Boolean = false
    ) {
        activity.runOnUiThread {
            ensureDialog()
            val progressBar = dialog?.findViewById<ProgressBar>(R.id.ProgressBar) ?: return@runOnUiThread
            val progressText = dialog?.findViewById<TextView>(R.id.TVProgress) ?: return@runOnUiThread
            dialog?.findViewById<TextView>(R.id.TVTitle)?.text = title
            dialog?.findViewById<TextView>(R.id.TVMessage)?.text = message

            progressBar.max = PROGRESS_SCALE
            progressBar.isIndeterminate = indeterminate
            if (indeterminate || progress == null) {
                progressAnimator?.cancel()
                progressBar.progress = 0
                progressText.visibility = android.view.View.GONE
            } else {
                val clamped = progress.coerceIn(0, PROGRESS_SCALE)
                animateProgress(progressBar, clamped)
                val percent = if (clamped >= PROGRESS_SCALE) 100 else (clamped * 100) / PROGRESS_SCALE
                progressText.text = "$percent%"
                progressText.visibility = android.view.View.VISIBLE
            }
        }
    }

    fun dismiss() {
        activity.runOnUiThread {
            try {
                dialog?.dismiss()
            } catch (_: Exception) {
            }
        }
    }

    private fun animateProgress(progressBar: ProgressBar, targetProgress: Int) {
        progressAnimator?.cancel()
        val startProgress = progressBar.progress
        if (startProgress == targetProgress) return

        progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", startProgress, targetProgress).apply {
            duration = ((70L + (kotlin.math.abs(targetProgress - startProgress) / 4L)).coerceAtMost(160L))
            interpolator = LinearInterpolator()
            start()
        }
    }
}
