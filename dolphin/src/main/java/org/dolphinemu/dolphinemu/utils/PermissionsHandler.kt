package org.dolphinemu.dolphinemu.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.Keep
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionsHandler {
    const val REQUEST_RECORD_AUDIO = 900

    @Keep
    @JvmStatic
    fun hasRecordAudioPermission(context: Context?): Boolean =
        context != null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    @Keep
    @JvmStatic
    fun requestRecordAudioPermission(activity: Activity?) {
        if (activity == null) return
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO,
        )
    }
}
