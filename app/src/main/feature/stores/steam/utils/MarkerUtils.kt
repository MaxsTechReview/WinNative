package com.winlator.cmod.feature.stores.steam.utils
import com.winlator.cmod.feature.stores.steam.enums.Marker
import java.io.File
import timber.log.Timber

object MarkerUtils {
    fun hasMarker(dirPath: String, marker: Marker): Boolean {
        val file = File(dirPath, marker.fileName)
        return file.exists()
    }

    fun addMarker(dirPath: String, marker: Marker): Boolean {
        return try {
            val dir = File(dirPath)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, marker.fileName)
            if (!file.exists()) {
                file.createNewFile()
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to add marker ${marker.name} at $dirPath")
            false
        }
    }

    fun removeMarker(dirPath: String, marker: Marker): Boolean {
        return try {
            val file = File(dirPath, marker.fileName)
            if (file.exists()) {
                file.delete()
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove marker ${marker.name} from $dirPath")
            false
        }
    }
}
