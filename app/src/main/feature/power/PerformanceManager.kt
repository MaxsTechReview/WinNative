package com.winlator.cmod.feature.power

import java.io.File
import com.winlator.cmod.shared.util.RootManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.let

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

data class SystemInformation(
    var fanMode: Int?,
    var cpuGovernor: String?,
    var gpuFrequency: Int?,
    var policies: List<CpuPolicy>?,
    var performanceMode: Int?
)

object PerformanceManager {
    private const val IDLE_TIMEOUT_NS = 1500000000L
    private const val CPU_PATH = "/sys/devices/system/cpu"
    private const val GPU_PATH = "/sys/class/kgsl/kgsl-3d0"
    private const val POLICY_PATH = "/sys/devices/system/cpu/cpufreq"
    private const val CPU_BOOST_PATH = "/sys/devices/system/cpu/cpufreq/boost"
    private const val CPU_POLICY_BASE_PATH = "/sys/devices/system/cpu/cpufreq/"
    private val FAN_MODES = mapOf("QUIET" to 1, "SMART" to 4, "SPORT" to 5, "CUSTOM" to 6)
    private val PERFORMANCE_MODES = mapOf("Standard" to 0, "Performance" to 1, "High Performance" to 2)

    val isDeviceSupported
        get() = RootManager.isRooted

    var targetFPS: Float = 0f
    var fpsAvgFrameCount = 0
    var gpuFrequencyIndex: Int = 0
    var gpuFrequencies: MutableList<Int> = mutableListOf()
    var useAutoTargeting: Boolean = false

    private var ticks: Long = 0L
    private var fpsLastFrameNano = 0L
    private var fpsPastFrames: MutableList<Float> = mutableListOf()
    private var autoTargetPolicies: List<CpuPolicy>? = null

    val disabledCores = mutableListOf<Int>()
    val isFanSupported = checkForFanSupport()
    val isGpuSupported = checkForGpuSupport()
    private val defaultSystemInfo: SystemInformation? = getSystemInformation()

    fun checkForGpuSupport(): Boolean {
        if (!isDeviceSupported) return false
        val gpuDirectorySymlink = File(GPU_PATH)
        if (!gpuDirectorySymlink.isDirectory) return false
        val frequencies = RootManager.readSysfsFile("${gpuDirectorySymlink.canonicalPath}/gpu_available_frequencies")
        if (frequencies.isNullOrEmpty()) return false
        frequencies.split(" ").reversed().forEach { it ->
            runCatching {
                val freq = (it.toLong() / 1_000_000).toInt()
                gpuFrequencies.add(freq)
            }.getOrDefault(0)
        }
        return gpuFrequencies.size >= 2
    }

    fun checkForFanSupport(): Boolean {
        if (!isDeviceSupported) return false
        val response = RootManager.executeAsRoot("settings get system fan_mode") ?: return false
        return response.toIntOrNull() != null
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
            defaultSystemInfo?.policies = getCpuPolicies()
    }

    fun getSystemInformation(): SystemInformation? {
        if (!isDeviceSupported) return null
        if (defaultSystemInfo != null) return defaultSystemInfo
        return SystemInformation(
            fanMode = getFanMode(),
            cpuGovernor = getCpuGovernor(),
            gpuFrequency = getGpuFrequency(),
            policies = getCpuPolicies(),
            performanceMode = getPerformanceMode()
        )
    }

    fun getPerformanceMode(): Int? {
        if (!isDeviceSupported) return null
        val response = RootManager.executeAsRoot("settings get system performance_mode") ?: return null
        return response.toIntOrNull()
    }

    fun getGpuFrequency(): Int? {
        if (!isDeviceSupported || !isGpuSupported) return null
        val response = RootManager.readSysfsFile("${GPU_PATH}/max_clock_mhz") ?: return null
        return response.toIntOrNull()
    }

    fun getFanMode(): Int? {
        if (!isDeviceSupported || !isFanSupported) return null
        val response = RootManager.executeAsRoot("settings get system fan_mode") ?: return null
        return response.toIntOrNull()
    }

    fun getSupportedPerformanceModes(): List<String>? {
        if (!isDeviceSupported || !isGpuSupported) return null
        return PERFORMANCE_MODES.map { it.key }
    }

    fun getSupportedFanModes(): List<String>? {
        if (!isDeviceSupported || !isFanSupported) return null
        return FAN_MODES.map { it.key }
    }

    fun getDefaultSystemPolicies(): List<CpuPolicy>? {
        if (!isDeviceSupported) return null
        return defaultSystemInfo?.policies
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

    private fun calculateFPS(): Float {
        val nowNano = System.nanoTime()
        if (fpsLastFrameNano == 0L || nowNano - fpsLastFrameNano > IDLE_TIMEOUT_NS) {
            fpsLastFrameNano = nowNano
            return 0f
        }
        val fps = 1000000000.0f / (nowNano - fpsLastFrameNano)
        fpsLastFrameNano = nowNano
        return fps
    }

    fun onTick() {
        if (!isDeviceSupported || targetFPS == 0f || !useAutoTargeting) return
        ticks++
        if (ticks.toFloat() == targetFPS*60f) {
            val policies = getCpuPolicies() ?: return
            for (policy in policies) {
                policy.enabled = true
                policy.boostState = true
                if (policy.availableFrequencies.isNullOrEmpty()) continue
                policy.maxFrequency = policy.availableFrequencies.first()
                policy.minFrequency = policy.availableFrequencies.first()
            }
            if (isGpuSupported && gpuFrequencies.isNotEmpty() && gpuFrequencies.size > 2)
                setGpuFrequency(gpuFrequencies.first(), gpuFrequencies.first())
            autoTargetPolicies = policies
            setCpuCorePolicies(policies)
            return
        }

        var fps = calculateFPS()
        if (fps == 0f) return

        if (ticks%targetFPS==0f && !autoTargetPolicies.isNullOrEmpty()) {
            fpsPastFrames.add(fps)
            if (fpsPastFrames.size < fpsAvgFrameCount) return
            fps = fpsPastFrames.sum() / fpsPastFrames.size
            var shouldUpdate = false
            if (fps > targetFPS*1.1) {
                for (policy in autoTargetPolicies) {
                    if (policy.availableFrequencies.isNullOrEmpty()) continue
                    val coreHasBoost = policy.availableFrequencies.last() != policy.boostFrequency
                    if (coreHasBoost && policy.maxFrequency == policy.boostFrequency) {
                        policy.maxFrequency = policy.availableFrequencies.last()
                        shouldUpdate = true
                    }
                    else if (policy.maxFrequency == policy.boostFrequency){
                        policy.maxFrequency = policy.availableFrequencies.takeLast(2).first()
                        shouldUpdate = true
                    } else {
                        val maxIndex = policy.availableFrequencies.indexOf(policy.maxFrequency)
                        if (maxIndex > 1) {
                            policy.maxFrequency = policy.availableFrequencies[maxIndex - 1]
                            shouldUpdate = true
                        }
                    }
                    if (coreHasBoost && policy.maxFrequency == policy.boostFrequency) {
                        policy.minFrequency = policy.availableFrequencies.last()
                        shouldUpdate = true
                    } else {
                        val maxIndex = policy.availableFrequencies.indexOf(policy.maxFrequency)
                        if (maxIndex-1 <= 0) continue
                        policy.minFrequency = policy.availableFrequencies[maxIndex-1]
                        shouldUpdate = true
                    }
                }
                if (isGpuSupported && gpuFrequencyIndex > 1)
                    gpuFrequencyIndex -= 1

            }else if (fps < targetFPS*0.98) {
                for (policy in autoTargetPolicies) {
                    if (policy.availableFrequencies.isNullOrEmpty()) continue
                    val maxIndex = policy.availableFrequencies.indexOf(policy.maxFrequency)
                    if (maxIndex == -1) {
                        policy.minFrequency = policy.availableFrequencies.last()
                        shouldUpdate = true
                    } else {
                        if (maxIndex + 1 >= policy.availableFrequencies.size && policy.boostFrequency != null)
                            policy.maxFrequency = policy.boostFrequency
                        else
                            policy.maxFrequency = policy.availableFrequencies[maxIndex + 1]
                        policy.minFrequency = policy.availableFrequencies[maxIndex]
                        shouldUpdate = true
                    }
                }
                if (isGpuSupported && gpuFrequencyIndex+1 < gpuFrequencies.size)
                    gpuFrequencyIndex+=1
            }
            fpsPastFrames.clear()
            if (shouldUpdate) {
                CoroutineScope(Dispatchers.IO).launch {
                    if (isGpuSupported && gpuFrequencies.isNotEmpty())
                        setGpuFrequency(gpuFrequencies[gpuFrequencyIndex-1], gpuFrequencies[gpuFrequencyIndex])
                    autoTargetPolicies?.let {
                        setCpuCorePolicies(it)
                    }
                }
            }
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

    fun setGpuFrequency(gpuMin: Int, gpuMax: Int, lockfile: Boolean=true): Boolean? {
        if (!isDeviceSupported || !isGpuSupported) return null
        if (gpuFrequencies.isEmpty() || gpuFrequencies.size < 2) return null
        if (!gpuFrequencies.contains(gpuMin) || !gpuFrequencies.contains(gpuMax)) return false
        if (lockfile) gpuFrequencyIndex = gpuFrequencies.indexOf(gpuMax)
        RootManager.writeSysfsFile("${GPU_PATH}/max_pwrlevel", "0", lockfile)
        RootManager.writeSysfsFile("${GPU_PATH}/max_clock_mhz", gpuMax.toString(), lockfile)
        RootManager.writeSysfsFile("${GPU_PATH}/min_clock_mhz", gpuMin.toString(), lockfile)
        return true
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

    fun setCpuCoresOnlineState(cpuCores: List<Int>, state: Boolean): Boolean? {
        if (!isDeviceSupported) return null
        if (cpuCores.isEmpty()) return false
        for (cpuCore in cpuCores) {
            val response = RootManager.writeSysfsFile(
                "${CPU_PATH}/cpu${cpuCore}/online",
                if (state) "1" else "0"
            )
            if (response) {
                if (state)
                    disabledCores.remove(cpuCore)
                else
                    disabledCores.add(cpuCore)
            }
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

    fun setCpuPolicyFrequency(policyPath: String, minFrequency: Long, maxFrequency: Long): Boolean? {
        if (!isDeviceSupported) return null
        if (!RootManager.writeSysfsFile("${policyPath}/scaling_min_freq", minFrequency.toString())) return false
        if (!RootManager.writeSysfsFile("${policyPath}/scaling_max_freq", maxFrequency.toString())) return false
        return true
    }

    fun setCpuCorePolicies(cpuCorePolicies: List<CpuPolicy>, setBoostFrequency: Boolean=false): Boolean? {
        if (!isDeviceSupported) return null
        for (cpuPolicy in cpuCorePolicies) {
            setCpuBoostState(cpuPolicy.boostState)
            if (cpuPolicy.enabled) {
                setCpuCoresOnlineState(cpuPolicy.cpuCores, true)
                if (setBoostFrequency && cpuPolicy.boostState && cpuPolicy.boostFrequency != null && cpuPolicy.boostFrequency.toInt() != 0)
                    setCpuPolicyFrequency("${POLICY_PATH}/${cpuPolicy.policyName}", cpuPolicy.minFrequency, cpuPolicy.boostFrequency)
                else
                    setCpuPolicyFrequency("${POLICY_PATH}/${cpuPolicy.policyName}", cpuPolicy.minFrequency, cpuPolicy.maxFrequency)
            } else
                setCpuCoresOnlineState(cpuPolicy.cpuCores, false)
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

    fun setPerformanceMode(performanceMode: Int): Boolean? {
        if (!isDeviceSupported || !isGpuSupported) return null
        return RootManager.executeAsRoot("settings put system performance_mode ${performanceMode}") != null
    }

    fun setPerformanceMode(performanceMode: String): Boolean? {
        if (!isDeviceSupported || !isGpuSupported) return null
        val mode = PERFORMANCE_MODES[performanceMode] ?: return null
        return RootManager.executeAsRoot("settings put system performance_mode ${mode}") != null
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

    fun setSystemInformation(systemInfo: SystemInformation, lockfile: Boolean=true, reset: Boolean=false) {
        if (!isDeviceSupported) return
        systemInfo.fanMode?.let { setFanMode(it) }
        systemInfo.cpuGovernor?.let { setAllCpuCoreGovernor(it) }
        systemInfo.gpuFrequency?.let { setGpuFrequency(gpuFrequencies.first(), it, lockfile) }
        systemInfo.policies?.let { setCpuCorePolicies(it, reset) }
        systemInfo.performanceMode?.let { setPerformanceMode(it) }
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
        ticks = 0
        gpuFrequencyIndex = 0
        autoTargetPolicies = null
        defaultSystemInfo?.let { setSystemInformation(it, false, true) }
        return true
    }
}