package com.winlator.cmod.feature.sync.google
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment

class GoogleFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val scrollView =
            ScrollView(requireContext()).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                isFillViewport = true
            }
        val composeView =
            ComposeView(requireContext()).apply {
                setContent {
                    GoogleScreen()
                }
            }
        scrollView.addView(composeView)
        return scrollView
    }

    fun onSavedGamesPermissionResult(
        resultCode: Int,
        data: Intent?,
    ) {
        val currentActivity = activity ?: return
        CloudSyncManager.onSavedGamesPermissionResult(currentActivity)
    }
}
