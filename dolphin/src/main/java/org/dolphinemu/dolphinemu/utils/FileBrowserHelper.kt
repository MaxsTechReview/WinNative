package org.dolphinemu.dolphinemu.utils

object FileBrowserHelper {
    @JvmStatic
    fun getExtension(fileName: String?, forceLowerCase: Boolean): String? {
        if (fileName == null) return null
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.length - 1) return null
        val ext = fileName.substring(dot + 1)
        return if (forceLowerCase) ext.lowercase() else ext
    }
}
