package com.winlator.cmod

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Swaps in a bare [Application] so Compose layout tests do not boot Hilt, Room or the native
 * runtime — the screens under test are pure UI.
 */
class StoreUiTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, Application::class.java.name, context)
}
