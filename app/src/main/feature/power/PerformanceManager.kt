package com.winlator.cmod.feature.power

import java.io.File
import com.winlator.cmod.shared.util.RootManager
import org.apache.commons.lang3.mutable.Mutable

data class CpuPolicy(
    var enabled: Boolean,
    val policyId: Int,
    val policyName: String,
    var boostState: Boolean,
    var minFrequency: Long,
    var maxFrequency: Long,
    val boostFrequency: Long?,
    val cpuCores: List<Int>,
    val availableFrequencies: MutableList<Long>?
)

object PerformanceManager {
    private const val CPU_PATH = "/sys/devices/system/cpu"
    private const val CPU_BOOST_PATH = "/sys/devices/system/cpu/cpufreq/boost"
    private const val CPU_POLICY_BASE_PATH = "/sys/devices/system/cpu/cpufreq/"

    private var defaultSystemPolicies: List<CpuPolicy>? = null
    private val defaultSystemGovernor = getCpuGovernor()

    private var defautlSystemFanMode: Int? = null

    private val FAN_MODES = mapOf<String, Int>("QUIET" to 1, "SMART" to 4, "SPORT" to 5, "CUSTOM" to 6)

    val allCpuGovernors  = getCpuGovernors()

    val disabledCores = mutableListOf<Int>()

    val isDeviceSupported
        get() = RootManager.isRooted

    val isFanSupported = checkForFanSupport()

    val isGpuSupported = false

    private var running = false

    fun checkForFanSupport(): Boolean {
        if (!isDeviceSupported) return false
        val response = RootManager.executeAsRoot("settings get system fan_mode") ?: return false
        val fanMode = response.toIntOrNull()
        defautlSystemFanMode = fanMode
        return fanMode != null
    }

    fun checkForPossibleCrash() {
        if (!isDeviceSupported) return
        val numberOfCpuCores = getNumberOfCpuCores() ?: return
        var resetDefaultSystemPolicies = false
        getCpuBoostState()?.let {
            if (!it)
                setCpuBoostState(true)
        }
        for (cpu in 0..<numberOfCpuCores) {
            val cpuFreqPath = "${CPU_PATH}/cpu${cpu}"
            val onlineState = getCpuCoreOnlineState(cpu)
            val minFrequency = runCatching { RootManager.readSysfsFile("${cpuFreqPath}/cpufreq/cpuinfo_min_freq")!!.toLong() }.getOrDefault(0)
            val maxFrequency = runCatching { RootManager.readSysfsFile("${cpuFreqPath}/cpufreq/cpuinfo_max_freq")!!.toLong() }.getOrDefault(0)
            val currentMaxFrequency = runCatching { RootManager.readSysfsFile("${cpuFreqPath}/cpufreq/scaling_max_freq")!!.toLong() }.getOrDefault(0)
            if (maxFrequency.toInt() != 0 && currentMaxFrequency != maxFrequency) {
                setCpuCoreFrequency(cpu, minFrequency, maxFrequency)
                resetDefaultSystemPolicies = true
            }

            if (onlineState != null && !onlineState) {
                // if cpu state is null something is very wrong
                setCpuCoreOnlineState(cpu, true)
                val minFrequency = runCatching { RootManager.readSysfsFile("${cpuFreqPath}/cpufreq/cpuinfo_min_freq")!!.toLong() }.getOrDefault(0)
                val maxFrequency = runCatching { RootManager.readSysfsFile("${cpuFreqPath}/cpufreq/cpuinfo_max_freq")!!.toLong() }.getOrDefault(0)
                if (minFrequency.toInt() != 0 && maxFrequency.toInt() != 0) {
                    setCpuCoreFrequency(cpu, minFrequency, maxFrequency)
                    resetDefaultSystemPolicies = true
                }
            }
        }
        if (resetDefaultSystemPolicies)
            defaultSystemPolicies = getCpuPolicies()
    }

    fun getSupportedFanModes(): List<String>? {
        if (!isDeviceSupported || !isFanSupported) return null
        return FAN_MODES.map { it.key }
    }

    fun getDefaultSystemPolicies(): List<CpuPolicy>? {
        if (!isDeviceSupported) return null
        return defaultSystemPolicies
    }

    fun getCpuBoostState(): Boolean? {
        if (!isDeviceSupported) return null
        return RootManager.readSysfsFile(CPU_BOOST_PATH) == "1"
    }

    fun getCpuGovernors(): List<String>? {
        if (!isDeviceSupported) return null
        val cpuPath = "${CPU_PATH}/cpu0/cpufreq/scaling_available_governors"
        return runCatching { RootManager.readSysfsFile(cpuPath)?.split(" ")?.map{it} }.getOrNull()
    }

    fun getCpuGovernor(cpu: Int=0): String? {
        if (!isDeviceSupported) return null
        return RootManager.readSysfsFile("${CPU_PATH}/cpu${cpu}/cpufreq/scaling_governor")
    }

    fun getNumberOfCpuCores(): Int? {
        if (!isDeviceSupported) return null
        val content = RootManager.readSysfsFile("${CPU_PATH}/present") ?: return null
        val parts = content.split("-")
        if (parts.size == 2)
            return runCatching { parts[1].toInt()+1 }.getOrNull()
        return null
    }

    fun getCpuPolicies(): List<CpuPolicy>? {
        if (!isDeviceSupported) return null
        val numberOfCpuCores = getNumberOfCpuCores() ?: return null
        val policies = mutableMapOf<String, MutableList<Int>>()
        for (cpu in 0..<numberOfCpuCores) {
            val cpuSymlink = "${CPU_PATH}/cpu${cpu}/cpufreq/scaling_governor"
            try {
                val policyDirectory = File(File(cpuSymlink).canonicalPath).parent ?: continue
                if (!policies.containsKey(policyDirectory))
                    policies[policyDirectory] = mutableListOf()
                policies[policyDirectory]?.add(cpu)
            }catch (e: Exception) {
                val policyDirectory = "/sys/devices/system/cpu/cpu${cpu}/cpufreq"
                if (!policies.containsKey(policyDirectory))
                    policies[policyDirectory] = mutableListOf()
                policies[policyDirectory]?.add(cpu)
            }
        }
        val boostState = RootManager.readSysfsFile(CPU_BOOST_PATH) == "1"
        return policies.entries.mapIndexed { index, (policyDirectory, cpuCoreList) ->
            val frequencies: MutableList<Long>? = RootManager.readSysfsFile("${policyDirectory}/scaling_available_frequencies")?.split(" ")?.map { it -> runCatching { it.toLong()}.getOrDefault(0) } as MutableList<Long>?
            val minFrequency = runCatching {  frequencies!!.first() }.getOrDefault(0)
            val maxFrequency = runCatching {  frequencies!!.last() }.getOrDefault(0)
            val boostFrequency = runCatching { RootManager.readSysfsFile("${policyDirectory}/cpuinfo_max_freq")!!.toLong() }.getOrDefault(0)
            //if (boostState && frequencies != null && maxFrequency > frequencies.last())
            //    frequencies.add(maxFrequency)
                //frequencies = frequencies + listOf<Long>(maxFrequency)
            CpuPolicy(
                enabled = true,
                policyId = index,
                policyName = File(policyDirectory).name,
                boostState = boostState,
                minFrequency = minFrequency,
                maxFrequency = maxFrequency,
                cpuCores = cpuCoreList,
                availableFrequencies = frequencies,
                boostFrequency = boostFrequency
            )
        }
    }

    fun getCpuCoreAvailableFrequencies(cpu: Int): List<Long>? {
        if (!isDeviceSupported) return null
        return RootManager.readSysfsFile("${CPU_PATH}/cpu${cpu}/scaling_available_frequencies")?.split(" ")?.map { it -> runCatching { it.toLong()}.getOrDefault(0) }
    }

    fun getCpuPolicyAvailableFrequencies(policyName: String): List<Long>? {
        if (!isDeviceSupported) return null
        return RootManager.readSysfsFile("${CPU_POLICY_BASE_PATH}/${policyName}/scaling_available_frequencies")?.split(" ")?.map { it -> runCatching { it.toLong()}.getOrDefault(0) }
    }

    fun getCpuCoreOnlineState(cpuCore: Int): Boolean? {
        if (!isDeviceSupported) return null
        val onlineState = RootManager.readSysfsFile("${CPU_PATH}/cpu${cpuCore}/online")
        return onlineState == "1"
    }

    fun setCpuCoreGovernor(cpuCore: Int, governor: String): Boolean? {
        if (!isDeviceSupported) return null
        val governorPath = "${CPU_PATH}/cpu${cpuCore}/cpufreq/scaling_governor"
        return RootManager.writeSysfsFile(governorPath, governor)
    }

    fun setAllCpuCoreGovernor(governor: String): Boolean? {
        if (!isDeviceSupported) return null
        val numberOfCpuCores = getNumberOfCpuCores() ?: return null
        for (cpu in 0..numberOfCpuCores)
            setCpuCoreGovernor(cpu, governor)
        return true
    }

    fun setCpuBoostState(state: Boolean): Boolean? {
        if (!isDeviceSupported) return null
        return RootManager.writeSysfsFile(CPU_BOOST_PATH, if(state) "1" else "0")
    }

    fun setCpuCoreOnlineState(cpuCore: Int, state: Boolean): Boolean? {
        if (!isDeviceSupported) return null
        val response = RootManager.writeSysfsFile("${CPU_PATH}/cpu${cpuCore}/online", if(state) "1" else "0")
        if (response) {
            if (state)
                disabledCores.remove(cpuCore)
            else
                disabledCores.add(cpuCore)
        }
        return true
    }

    fun setPolicyOnlineState(policy: CpuPolicy, state: Boolean): Boolean? {
        if (!isDeviceSupported) return null
        if (!policy.cpuCores.isNotEmpty()) return null
        policy.cpuCores.forEach { setCpuCoreOnlineState(it, state) }
        return true
    }

    fun setCpuCoreFrequency(cpuCore: Int, minFrequency: Long, maxFrequency: Long): Boolean? {
        if (!isDeviceSupported) return null
        val cpuFrequencyPath = "${CPU_PATH}/cpu${cpuCore}/cpufreq"
        if (!RootManager.writeSysfsFile("${cpuFrequencyPath}/scaling_min_freq", minFrequency.toString())) return false
        if (!RootManager.writeSysfsFile("${cpuFrequencyPath}/scaling_max_freq", maxFrequency.toString())) return false
        return true
    }

    fun setCpuCorePolicies(cpuCorePolicies: List<CpuPolicy>, setBoostFrequency: Boolean=false): Boolean? {
        if (!isDeviceSupported) return null
        for (cpuPolicy in cpuCorePolicies) {
            setCpuBoostState(cpuPolicy.boostState)
            for (cpuCore in cpuPolicy.cpuCores) {
                if (cpuPolicy.enabled)
                    if (setBoostFrequency && cpuPolicy.boostState && cpuPolicy.boostFrequency != null && cpuPolicy.boostFrequency.toInt() != 0)
                        setCpuCoreFrequency(cpuCore, cpuPolicy.minFrequency, cpuPolicy.boostFrequency)
                    else
                        setCpuCoreFrequency(cpuCore, cpuPolicy.minFrequency, cpuPolicy.maxFrequency)
                else
                    setCpuCoreOnlineState(cpuCore, false)
            }
        }
        return true
    }


    fun setCpuCorePolicies(rawCpuPolicy: String, setBoostFrequency: Boolean=false): Boolean? {
        if (!isDeviceSupported) return null
        val cpuCorePolicies = parseRawPoliciesFromString(rawCpuPolicy)
        if (cpuCorePolicies.isNullOrEmpty()) return null
        setCpuCorePolicies(cpuCorePolicies, setBoostFrequency)
        return true
    }

    fun setFanMode(fanMode: String): Boolean? {
        if (!isDeviceSupported || !isFanSupported) return null
        val mode = FAN_MODES[fanMode] ?: return null
        return RootManager.executeAsRoot("settings put system fan_mode ${mode}") != null
    }

    fun setFanMode(fanMode: Int): Boolean? {
        if (!isDeviceSupported || !isFanSupported) return null
        if (!FAN_MODES.containsValue(fanMode)) return null
        return RootManager.executeAsRoot("settings put system fan_mode ${fanMode}") != null
    }

    fun parseRawPoliciesFromString(rawPolicies: String): List<CpuPolicy>? {
        // Example policies string: "policyID-policyName:minFrequency-maxFrequency!start_core-end_core?policyOnlineState-boostState-boostfrequency," "0-policy0:1000-1111!3-7?1-1-9999,"
        if (!isDeviceSupported) return null
        val policies = mutableListOf<CpuPolicy>()
        for (policy in rawPolicies.split(",")) {
            try {
                val policyId = policy.split(":")[0].split("-")[0].toInt()
                val policyName = policy.split(":")[0].split("-")[1]
                val minFrequency = policy.split(":")[1].split("!")[0].split("-")[0].toLong()
                val maxFrequency = policy.split(":")[1].split("!")[0].split("-")[1].toLong()
                val cpuCoresRange = policy.split(":")[1].split("!")[1].split("?")[0].split("-")
                val cpuCoreState = policy.split(":")[1].split("!")[1].split("?")[1].split('-')[0].toBoolean()
                val boostState = policy.split(":")[1].split("!")[1].split("?")[1].split('-')[1].toBoolean()
                val boostFrequency = policy.split(":")[1].split("!")[1].split("?")[1].split('-')[2]

                val cpuCores = mutableListOf<Int>()
                (cpuCoresRange[0].toInt()..cpuCoresRange[1].toInt())
                    .forEach { cpu ->
                        cpuCores.add(cpu)
                    }

                policies.add(CpuPolicy(
                    enabled = cpuCoreState,
                    policyId = policyId,
                    policyName = policyName,
                    boostState = boostState,
                    minFrequency = minFrequency,
                    maxFrequency = maxFrequency,
                    boostFrequency = if (boostFrequency == "0") null else boostFrequency.toLong(),
                    cpuCores = cpuCores,
                    availableFrequencies = getCpuPolicyAvailableFrequencies(policyName) as MutableList<Long>?
                ))
            } catch (e: Exception) {}
        }
        return policies
    }

    fun policiesToString(policies: List<CpuPolicy>): String? {
        if (!isDeviceSupported) return null
        var stringPolicies = ""
        for (policy in policies) {
            stringPolicies += "${policy.policyId}-${policy.policyName}:${policy.minFrequency}-${policy.maxFrequency}!${policy.cpuCores.first()}-${policy.cpuCores.last()}?${policy.enabled}-${policy.boostState}-${policy.boostFrequency},"
        }
        return stringPolicies
    }

    fun formatFrequency(valueKhz: Int, boosted: Boolean = false): String {
        val base = when {
            valueKhz >= 1_000_000 -> String.format("%.2f GHz", valueKhz / 1_000_000f)
            valueKhz >= 1_000 -> String.format("%.0f MHz", valueKhz / 1_000f)
            else -> "$valueKhz kHz"
        }
        return if (boosted) "$base+" else base
    }

    fun resetSystemToDefault(): Boolean? {
        if (!isDeviceSupported) return null
        if (!defaultSystemGovernor.isNullOrEmpty())
            setAllCpuCoreGovernor(defaultSystemGovernor)
        if (isFanSupported && defautlSystemFanMode != null)
            setFanMode(defautlSystemFanMode!!)
        if (defaultSystemPolicies.isNullOrEmpty()) return null
        setCpuCorePolicies(defaultSystemPolicies!!, true)
        return true
    }
}