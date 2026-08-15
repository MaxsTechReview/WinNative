package org.dolphinemu.dolphinemu

import android.content.Context

/** WinNative replacement: vendored upstream classes call
 *  DolphinApplication.getAppContext(); the host app (or DolphinEmulationActivity)
 *  installs the context before any Dolphin code runs. */
object DolphinApplication {
    @Volatile
    private var appContext: Context? = null

    @JvmStatic
    fun install(context: Context) {
        appContext = context.applicationContext
    }

    @JvmStatic
    fun getAppContext(): Context =
        appContext ?: error("DolphinApplication.install() not called")
}
