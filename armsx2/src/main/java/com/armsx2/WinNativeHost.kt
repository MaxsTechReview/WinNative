package com.armsx2

import androidx.activity.ComponentActivity

object WinNativeHost {
    @Volatile
    var attachOverlay: ((ComponentActivity) -> Unit)? = null

    @Volatile
    var applyBootSettings: ((android.content.Context) -> Unit)? = null

    @Volatile
    var openMenu: (() -> Unit)? = null

    @Volatile
    var isMenuOpen: (() -> Boolean)? = null

    @Volatile
    var menuKeyHandler: ((android.view.KeyEvent) -> Boolean)? = null

    @Volatile
    var menuAxisHandler: ((Float, Float) -> Boolean)? = null

    fun enabled(): Boolean =
        attachOverlay != null &&
            runCatching {
                com.armsx2.runtime.MainActivityRuntime.prefs.getBoolean("wn.controls", false)
            }.getOrDefault(false)
}
