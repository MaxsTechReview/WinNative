package com.winlator.cmod.feature.sync.google
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.winlator.cmod.shared.theme.WinNativeTheme

class GoogleFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WinNativeTheme {
                    GoogleScreen()
                }
            }
        }

    fun onSavedGamesPermissionResult(
        resultCode: Int,
        data: Intent?,
    ) {
        val currentActivity = activity ?: return
        CloudSyncManager.onSavedGamesPermissionResult(currentActivity)
    }
}
