package com.winlator.cmod.feature.stores.gog.service
import org.json.JSONObject
import timber.log.Timber
import java.io.File

object GOGManifestUtils {
    const val MANIFEST_FILE_NAME = "_gog_manifest.json"
    private const val KEY_SCRIPT_INTERPRETER = "scriptInterpreter"
    private const val KEY_INSTALLED_DLC_IDS = "installedDlcIds"

    fun readLocalManifest(installDir: File): JSONObject? {
        val manifestFile = File(installDir, MANIFEST_FILE_NAME)
        if (!manifestFile.exists()) return null
        return try {
            JSONObject(manifestFile.readText())
        } catch (e: Exception) {
            Timber.tag("GOG").w(e, "Failed to parse _gog_manifest.json")
            null
        }
    }

    fun needsScriptInterpreter(installDir: File): Boolean {
        val root = readLocalManifest(installDir) ?: return false
        return root.optBoolean(KEY_SCRIPT_INTERPRETER, false)
    }

    fun getInstalledDlcIds(installDir: File): Set<String> {
        val root = readLocalManifest(installDir) ?: return emptySet()
        val idsArray = root.optJSONArray(KEY_INSTALLED_DLC_IDS) ?: return emptySet()
        val ids = mutableSetOf<String>()
        for (i in 0 until idsArray.length()) {
            idsArray.optString(i, "").takeIf { it.isNotBlank() }?.let(ids::add)
        }
        return ids
    }
}
