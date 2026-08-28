package com.winlator.cmod.app.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.winlator.cmod.R
import com.winlator.cmod.shared.ui.toast.WinToast

@Composable
fun UpdateHost() {
    val context = LocalContext.current
    val release = UpdateService.available
    val stage = UpdateService.stage

    if (release != null && UpdateService.dialogVisible) {
        UpdateAvailableDialog(
            release = release,
            stage = stage,
            error = UpdateService.lastError,
            onClose = { ignore -> UpdateService.dismissDialog(context, ignore) },
            onUpdate = { UpdateService.startInstall(context) },
        )
    }

    val upToDate = UpdateService.upToDateNotice
    val upToDateMessage = stringResource(R.string.update_up_to_date)
    LaunchedEffect(upToDate) {
        if (upToDate) {
            WinToast.show(context, upToDateMessage, android.widget.Toast.LENGTH_SHORT)
            UpdateService.dismissUpToDateNotice()
        }
    }
}
