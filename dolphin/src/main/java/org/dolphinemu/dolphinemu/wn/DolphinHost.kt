package org.dolphinemu.dolphinemu.wn

import android.app.Activity

object DolphinHost {
    @Volatile
    var attachOverlay: ((Activity) -> Unit)? = null

    @Volatile
    var onStageSaves: ((Activity) -> Unit)? = null

    @Volatile
    var onNetplayStatus: ((hosting: Boolean, hostCode: String?, members: List<String>) -> Unit)? = null
}
