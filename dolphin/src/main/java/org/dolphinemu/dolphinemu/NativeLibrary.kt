package org.dolphinemu.dolphinemu

import android.view.Surface
import androidx.annotation.Keep

/** WinNative replacement matching upstream's JNI contract; UI callbacks route
 *  to the registered [WnHost]. */
object NativeLibrary {
    object ButtonType {
        const val WIIMOTE_BUTTON_A = 100
    }

    interface WnHost {
        fun onToast(message: String, long: Boolean)

        fun onAlert(
            caption: String,
            text: String,
            yesNo: Boolean,
            isWarning: Boolean,
            nonBlocking: Boolean,
        ): Boolean

        fun onUpdateTouchPointer()

        fun onTitleChanged()

        fun onEmulationFinished()
    }

    @Volatile
    var host: WnHost? = null

    @Volatile
    var frameListener: Runnable? = null

    @Keep
    @JvmStatic
    fun onGameFrame() {
        frameListener?.run()
    }

    @Keep
    @JvmStatic
    fun displayToastMsg(message: String, long: Boolean) {
        host?.onToast(message, long)
    }

    @Keep
    @JvmStatic
    fun displayAlertMsg(
        caption: String,
        text: String,
        yesNo: Boolean,
        isWarning: Boolean,
        nonBlocking: Boolean,
    ): Boolean = host?.onAlert(caption, text, yesNo, isWarning, nonBlocking) ?: true

    @Keep
    @JvmStatic
    fun updateTouchPointer() {
        host?.onUpdateTouchPointer()
    }

    @Keep
    @JvmStatic
    fun onTitleChanged() {
        host?.onTitleChanged()
    }

    @Keep
    @JvmStatic
    fun finishEmulationActivity() {
        host?.onEmulationFinished()
    }

    @Keep
    @JvmStatic
    fun getRenderSurfaceScale(): Float =
        android.content.res.Resources.getSystem().displayMetrics.density

    @JvmStatic external fun Initialize()

    @JvmStatic external fun ReloadConfig()

    @JvmStatic external fun ReloadLoggerConfig()

    @JvmStatic external fun SetUserDirectory(directory: String)

    @JvmStatic external fun GetUserDirectory(): String

    @JvmStatic external fun SetCacheDirectory(directory: String)

    @JvmStatic external fun SetObscuredPixelsLeft(width: Int)

    @JvmStatic external fun SetObscuredPixelsTop(height: Int)

    @JvmStatic external fun SurfaceChanged(surf: Surface)

    @JvmStatic external fun SurfaceDestroyed()

    @JvmStatic external fun HasSurface(): Boolean

    @JvmStatic external fun Run(path: Array<String>, riivolution: Boolean)

    @JvmStatic external fun Run(
        path: Array<String>,
        riivolution: Boolean,
        savestatePath: String,
        deleteSavestate: Boolean,
    )

    @JvmStatic external fun RunNetPlay(
        path: Array<String>,
        riivolution: Boolean,
        bootSessionDataPointer: Long,
    )

    @JvmStatic external fun RunSystemMenu()

    @JvmStatic external fun ChangeDisc(path: String)

    @JvmStatic external fun PauseEmulation()

    @JvmStatic external fun UnPauseEmulation()

    @JvmStatic external fun StopEmulation()

    @JvmStatic external fun IsRunning(): Boolean

    @JvmStatic external fun IsRunningAndUnpaused(): Boolean

    @JvmStatic external fun IsUninitialized(): Boolean

    @JvmStatic external fun IsEmulatingWiiUnchecked(): Boolean

    @JvmStatic external fun GetCurrentTitleDescriptionUnchecked(): String

    @JvmStatic external fun GetCurrentGameIDUnchecked(): String

    @JvmStatic external fun SetIsBooting()

    @JvmStatic external fun SaveState(slot: Int, wait: Boolean)

    @JvmStatic external fun SaveStateAs(path: String, wait: Boolean)

    @JvmStatic external fun LoadState(slot: Int)

    @JvmStatic external fun LoadStateAs(path: String)

    @JvmStatic external fun GetUnixTimeOfStateSlot(slot: Int): Long

    @JvmStatic external fun SaveScreenShot()

    @JvmStatic external fun RefreshWiimotes()

    @JvmStatic external fun GetGitRevision(): String

    @JvmStatic external fun GetVersionString(): String

    @JvmStatic external fun DefaultCPUCore(): Int

    @JvmStatic external fun GetGameAspectRatio(): Float

    @JvmStatic external fun GetDefaultGraphicsBackendConfigName(): String
}
