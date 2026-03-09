package com.winlator.cmod.steam.utils

import android.content.Context
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import java.io.File
import timber.log.Timber

import com.winlator.cmod.contents.ContentsManager
import org.json.JSONObject

object ContainerUtils {
    fun getContainerId(appId: String): String {
        return appId
    }

    /**
     * Extracts the game ID from a container ID string
     */
    fun extractGameIdFromContainerId(containerId: String): Int {
        // Remove duplicate suffix like (1), (2) if present
        val idWithoutSuffix = if (containerId.contains("(")) {
            containerId.substringBefore("(")
        } else {
            containerId
        }

        // Split by underscores and find the last numeric part
        val parts = idWithoutSuffix.split("_")
        // The last part should be the numeric ID
        val lastPart = parts.lastOrNull() ?: throw IllegalArgumentException("Invalid container ID format: $containerId")

        return try {
            lastPart.toInt()
        } catch (e: NumberFormatException) {
            Timber.d("extractGameIdFromContainerId: Non-numeric ID '$lastPart' -> hashCode=${lastPart.hashCode()}")
            lastPart.hashCode()
        }
    }

    fun hasContainer(context: Context, containerId: String): Boolean {
        val containerManager = ContainerManager(context)
        return containerManager.getContainerById(extractGameIdFromContainerId(containerId)) != null
    }

    fun getContainer(context: Context, containerId: String): Container? {
        val containerManager = ContainerManager(context)
        return containerManager.getContainerById(extractGameIdFromContainerId(containerId))
    }

    fun getOrCreateContainer(context: Context, appId: String): Container {
        val containerManager = ContainerManager(context)
        val containerName = getContainerId(appId)
        
        // Try to find an existing container by name (e.g., STEAM_1234)
        for (container in containerManager.containers) {
            if (container.name == containerName) return container
        }

        // If not found, create a new one
        val data = JSONObject()
        try {
            data.put("name", containerName)
            // Default settings for Steam games
            data.put("wineVersion", "Main") // Use default wine version
            data.put("screenSize", "1280x720")
            data.put("graphicsDriver", "Turnip")
            data.put("dxwrapper", "DXVK")
            data.put("audioDriver", "pulseaudio")
            data.put("wincomponents", "directx,vcrun2022")
        } catch (e: Exception) {}

        val contentsManager = ContentsManager(context)
        return containerManager.createContainer(data, contentsManager) 
            ?: throw IllegalStateException("Container creation failed")
    }

    fun getADrivePath(drives: String): String? {
        for (drive in Container.drivesIterator(drives)) {
            if (drive[0] == "A") {
                return drive[1]
            }
        }
        return null
    }

    fun deleteContainer(context: Context, containerId: String) {
        val containerManager = ContainerManager(context)
        val container = containerManager.getContainerById(extractGameIdFromContainerId(containerId))
        if (container != null) {
            containerManager.removeContainerAsync(container) {}
        }
    }
}
