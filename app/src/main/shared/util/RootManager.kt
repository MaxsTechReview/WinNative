package com.winlator.cmod.shared.util

import android.os.IBinder
import android.os.Parcel
import com.topjohnwu.superuser.Shell
import java.nio.charset.Charset


object RootManager {
    var isRooted: Boolean = false
        get() = isDeviceRooted()
        private set

    private val binder: IBinder? = runCatching {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
        val rawBinder = getService.invoke(serviceManager, "PServerBinder") as IBinder
        rawBinder
    }.getOrNull()

    fun readSysfsFile(path: String): String? {
        if (!isRooted) return null
        val result = executeAsRoot("cat '$path'") ?: return null
        return result
    }

    fun writeSysfsFile(path: String, value: String, lockFile: Boolean=true): Boolean {
        return try {
            var command = "chmod 644 '$path'; echo '$value' > '$path';"
            if (lockFile)
                command+=" chmod 444 '$path'"
            val result = executeAsRoot(command)
            return result != null
        } catch (e: Exception) {
            false
        }
    }

    private fun decodeReply(reply: Parcel): String? {
        return reply.createByteArray()
            ?.toString(Charset.defaultCharset())
            ?.trim()
            ?.let { value -> if (value == "null") null else value }
    }

    fun executeAsRoot(cmd: String): String? {
        if (binder != null) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeStringArray(arrayOf(cmd, "1"))
                binder.transact(0, data, reply, 0)
                decodeReply(reply)
            } catch (throwable: Throwable) {
                return null
            } finally {
                data.recycle()
                reply.recycle()
            }
        } else if (Shell.isAppGrantedRoot() == true) {
            val result: Shell.Result = Shell.cmd(cmd).exec()
            if (!result.isSuccess) return null
            return result.out.toString()
        }
        return null
    }

    fun isDeviceRooted(): Boolean {
        return binder != null || Shell.isAppGrantedRoot() == true
    }
}