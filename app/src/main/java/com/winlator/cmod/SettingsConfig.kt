package com.winlator.cmod

import android.content.SharedPreferences
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

object SettingsConfig {
    @JvmField
    val DEFAULT_WINE_DEBUG_CHANNELS: String = "warn,err,fixme"

    @JvmField
    val DEFAULT_WINLATOR_PATH: String =
        Environment.getExternalStorageDirectory().path + "/Winlator"

    @JvmField
    val DEFAULT_SHORTCUT_EXPORT_PATH: String =
        "$DEFAULT_WINLATOR_PATH/Shortcuts"

    @JvmStatic
    fun resetEmulatorsVersion(activity: AppCompatActivity) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val editor: SharedPreferences.Editor = preferences.edit()
        editor.remove("current_box64_version")
        editor.remove("current_wowbox64_version")
        editor.remove("current_fexcore_version")
        editor.apply()
    }
}
