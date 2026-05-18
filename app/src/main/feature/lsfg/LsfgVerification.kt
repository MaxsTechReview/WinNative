package com.winlator.cmod.feature.lsfg

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.winlator.cmod.feature.stores.steam.service.SteamService
import java.io.File

/**
 * Persists the user-uploaded `Lossless.dll` and a one-shot "verified" flag.
 * Once a Steam account that owns Lossless Scaling (app 993090) has uploaded
 * the DLL, LSFG remains available offline forever after.
 */
object LsfgVerification {
    const val LOSSLESS_SCALING_APP_ID = 993090

    private const val PREFS_NAME = "lsfg_verification"
    private const val KEY_VERIFIED = "lsfg_verified"
    private const val KEY_DLL_FILENAME = "lsfg_dll_filename"
    private const val DLL_FILENAME = "Lossless.dll"

    sealed class Result {
        object Ok : Result()
        object NotSignedIn : Result()
        object NotOwned : Result()
        data class IoFailure(val message: String) : Result()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun storageDir(context: Context): File =
        File(context.applicationContext.filesDir, "lsfg").apply { if (!exists()) mkdirs() }

    fun dllFile(context: Context): File = File(storageDir(context), DLL_FILENAME)

    fun isVerified(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VERIFIED, false) && dllFile(context).isFile

    /**
     * Copies the DLL from [uri] into private storage and verifies that the
     * currently signed-in Steam account owns Lossless Scaling. Result is
     * cached permanently on success — Steam can be offline thereafter.
     */
    fun verifyAndStore(context: Context, uri: Uri): Result {
        if (!SteamService.isLoggedIn) return Result.NotSignedIn
        val ownsApp = SteamService.getAppInfoOf(LOSSLESS_SCALING_APP_ID) != null
        if (!ownsApp) return Result.NotOwned

        val dest = dllFile(context)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return Result.IoFailure("Cannot open DLL")
                dest.outputStream().use { input.copyTo(it) }
            }
        } catch (e: Exception) {
            return Result.IoFailure(e.message ?: "copy failed")
        }

        prefs(context).edit {
            putBoolean(KEY_VERIFIED, true)
            putString(KEY_DLL_FILENAME, DLL_FILENAME)
        }
        return Result.Ok
    }

    fun clear(context: Context) {
        prefs(context).edit { clear() }
        dllFile(context).delete()
    }
}
