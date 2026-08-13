package com.armsx2.config

import com.armsx2.ShaderParams
import com.armsx2.config.Settings.Companion.emitSink
import com.armsx2.config.Settings.Companion.merge
import kr.co.iefriends.pcsx2.NativeApp
import org.json.JSONArray
import org.json.JSONObject

data class Dev9HostMapping(
    val url: String = "",
    val ip: String = "0.0.0.0",
    val enabled: Boolean = true,
)

private fun shaderChainParamsToJson(value: Map<String, Map<String, Float>>): JSONObject =
    JSONObject().apply {
        value.forEach { (preset, params) ->
            if (preset.isNotEmpty() && params.isNotEmpty()) {
                put(preset, JSONObject().apply {
                    params.forEach { (name, v) -> put(name, v.toDouble()) }
                })
            }
        }
    }

private fun shaderChainParamsFromJson(json: JSONObject?): Map<String, Map<String, Float>> {
    if (json == null) return emptyMap()
    return buildMap {
        json.keys().forEach { preset ->
            val params = json.optJSONObject(preset) ?: return@forEach
            val values = buildMap<String, Float> {
                params.keys().forEach { name -> put(name, params.optDouble(name, 0.0).toFloat()) }
            }
            if (values.isNotEmpty()) put(preset, values)
        }
    }
}

data class Settings(
    val eeCycleRate: Int = 0,
    val eeCycleSkip: Int = 0,
    val eeClampMode: Int = 1,
    val vuClampMode: Int = 1,
    val mtvu: Boolean = true,
    val vu1Instant: Boolean = true,
    val vuFlagHack: Boolean = true,
    val fastCDVD: Boolean = false,
    val intcStat: Boolean = true,
    val waitLoop: Boolean = true,
    val vuNeonFusions: Boolean = true,
    val vuDeferredWrites: Boolean = false,
    val vuSkipStallSim: Boolean = false,

    val frameLimitEnable: Boolean = true,
    val nominalSpeedPercent: Int = 100,
    val fpsLimit: Int = 0,
    val frameSkip: Int = 0,

    val audioVolume: Int = 100,
    val audioMuted: Boolean = false,
    val audioSwapChannels: Boolean = false,
    val audioTimeStretch: Boolean = true,
    val audioBufferMs: Int = 50,
    val audioOutputLatencyMs: Int = 20,
    val audioFastForwardVolume: Int = 100,
    val spu2NeonReverb: Boolean = false,

    val enablePatches: Boolean = true,
    val enableCheats: Boolean = false,
    val enableWideScreenPatches: Boolean = false,
    val enableNoInterlacingPatches: Boolean = false,
    val enableFastBoot: Boolean = false,
    val hostFs: Boolean = false,
    val enableGameFixes: Boolean = true,
    val gamefixSoftwareRendererFmv: Boolean = false,
    val gamefixSkipMpeg: Boolean = false,
    val gamefixEETiming: Boolean = false,
    val gamefixInstantDma: Boolean = false,
    val gamefixBlitInternalFps: Boolean = false,
    val gamefixFpuMul: Boolean = false,
    val gamefixOphFlag: Boolean = false,
    val gamefixGifFifo: Boolean = false,
    val gamefixDmaBusy: Boolean = false,
    val gamefixVif1Stall: Boolean = false,
    val gamefixIbit: Boolean = false,
    val gamefixFullVu0Sync: Boolean = false,
    val gamefixVuAddSub: Boolean = false,
    val gamefixVuOverflow: Boolean = false,
    val gamefixXgkick: Boolean = false,
    val gamefixGoemonTlb: Boolean = false,
    val gamefixVuSync: Boolean = false,
    val skipDuplicateFrames: Boolean = true,
    val eeFpuRoundMode: Int = 3,
    val vu0RoundMode: Int = 3,
    val vu1RoundMode: Int = 3,

    val screenOffsets: Boolean = false,
    val showOverscan: Boolean = false,
    val antiBlur: Boolean = true,
    val disableInterlaceOffset: Boolean = false,
    val syncToHostRefresh: Boolean = false,
    val disableFramebufferFetch: Boolean = false,
    val hwRov: Boolean = false,
    val hwAa1: Boolean = false,
    val hwAat: Boolean = false,
    val adrenoFbFetch: Boolean = true,
    val forceMaliFbFetch: Boolean = false,
    val useAngleOpenGL: Boolean = false,
    val overrideTextureBarriers: Int = -1,
    val disableVertexShaderExpand: Boolean = false,
    val useBlitSwapChain: Boolean = false,
    val disableShaderCache: Boolean = false,
    val hwAccurateAlphaTest: Boolean = false,

    val skipDrawStart: Int = 0,
    val skipDrawEnd: Int = 0,
    val spinGpuReadbacks: Boolean = false,
    val spinCpuReadbacks: Boolean = false,
    val integerScaling: Boolean = false,
    val dithering: Int = 2,
    val vsyncQueueSize: Int = 2,
    val autoFlushSw: Boolean = true,
    val mipmapSw: Boolean = true,
    val swThreads: Int = 4,
    val swThreadsHeight: Int = 4,

    val aspectRatio: Int = 1,
    val fmvAspectRatio: Int = 0,
    val renderer: String = "auto",
    val upscaleFloat: Float = 1.0f,
    val framerateNtsc: Float = 59.94f,
    val frameratePal: Float = 50.00f,
    val deinterlaceMode: Int = 0,

    val dev9EthEnable: Boolean = false,
    val dev9EthApi: String = "Sockets",
    val dev9EthDevice: String = "Auto",
    val dev9EthLogDhcp: Boolean = false,
    val dev9EthLogDns: Boolean = false,
    val dev9InterceptDhcp: Boolean = false,
    val dev9Ps2Ip: String = "0.0.0.0",
    val dev9Mask: String = "0.0.0.0",
    val dev9Gateway: String = "0.0.0.0",
    val dev9Dns1: String = "0.0.0.0",
    val dev9Dns2: String = "0.0.0.0",
    val dev9AutoMask: Boolean = true,
    val dev9AutoGateway: Boolean = true,
    val dev9ModeDns1: String = "Auto",
    val dev9ModeDns2: String = "Auto",
    val dev9EthHosts: List<Dev9HostMapping> = emptyList(),
    val dev9HddEnable: Boolean = false,
    val dev9HddFile: String = "DEV9hdd.raw",

    val memoryCardSlot1Enabled: Boolean = true,
    val memoryCardSlot1Filename: String = "mcd001.ps2",
    val biosFilename: String = "",
    val memoryCardSlot2Enabled: Boolean = true,
    val memoryCardSlot2Filename: String = "mcd002.ps2",

    val usbKeyboard: Boolean = false,

    val recEE: Boolean = true,
    val recIOP: Boolean = true,
    val recVU0: Boolean = true,
    val recVU1: Boolean = true,
    val enableFastmem: Boolean = true,

    val useMacEE: Boolean = true,
    val useMacIOP: Boolean = true,
    val useMacVU0: Boolean = true,
    val useMacVU1: Boolean = true,

    val vu1InlineFmacStall: Boolean = false,
    val vu1CrossBlockPState: Boolean = false,
    val vu1InlineDrainTestPipes: Boolean = false,
    val vu1FmacInstanceRouting: Boolean = false,

    val hwMipmap: Boolean = true,
    val accurateBlendingUnit: Int = 1,
    val textureFiltering: Int = 2,
    val displayBilinear: Int = 1,
    val texturePreloading: Int = 2,
    val hardwareDownloadMode: Int = 0,
    val tvShader: Int = 0,
    val shadeBoost: Boolean = false,
    val shadeBoostBrightness: Int = 50,
    val shadeBoostContrast: Int = 50,
    val shadeBoostSaturation: Int = 50,
    val shadeBoostGamma: Int = 50,
    val fxaa: Boolean = false,
    val shaderChainEnabled: Boolean = false,
    val shaderChainPreset: String = "",
    val shaderChainParams: Map<String, Map<String, Float>> = emptyMap(),
    val casMode: Int = 0,
    val casSharpness: Int = 50,
    val loadTextureReplacements: Boolean = false,
    val loadTextureReplacementsAsync: Boolean = true,
    val precacheTextureReplacements: Boolean = false,
    val dumpReplaceableTextures: Boolean = false,
    val osdShowTextureReplacements: Boolean = false,
    val osdShowFps: Boolean = false,
    val osdScale: Int = 100,
    val vsyncEnable: Boolean = false,
    val osdShowVps: Boolean = false,
    val osdShowSpeed: Boolean = false,
    val osdShowCpu: Boolean = false,
    val osdShowGpu: Boolean = false,
    val osdShowResolution: Boolean = false,
    val osdShowGsStats: Boolean = false,
    val osdShowFrameTimes: Boolean = false,
    val osdShowHardwareInfo: Boolean = false,
    val osdShowMessages: Boolean = true,
    val osdShowGpuStats: Boolean = false,
    val osdShowVersion: Boolean = false,
    val osdShowSettings: Boolean = false,
    val osdShowInputs: Boolean = false,
    val autoFlush: Int = 0,
    val halfPixelOffset: Int = 0,
    val limit24BitDepth: Int = 0,
    val manualUserHacks: Boolean = false,
    val textureInsideRt: Int = 0,
    val nativeScaling: Int = 0,
    val roundSprite: Int = 0,
    val bilinearUpscale: Int = 0,
    val gpuTargetClut: Int = 0,
    val cpuSpriteRenderBw: Int = 0,
    val cpuSpriteRenderLevel: Int = 0,
    val alignSprite: Boolean = false,
    val mergeSprite: Boolean = false,
    val forceEvenSpritePosition: Boolean = false,
    val unscaledPaletteDraw: Boolean = false,
    val textureOffsetX: Int = 0,
    val textureOffsetY: Int = 0,
    val gpuPaletteConversion: Boolean = false,
    val cpuFramebufferConversion: Boolean = false,
    val readTargetsWhenClosing: Boolean = false,
    val disableDepthEmulation: Boolean = false,
    val disablePartialInvalidation: Boolean = false,
    val disableSafeFeatures: Boolean = false,
    val disableRenderFixes: Boolean = false,
    val preloadFrameData: Boolean = false,
    val estimateTextureRegion: Boolean = false,
    val drawBuffering: Boolean = false,
    val cpuClutRender: Int = 0,
    val triFilter: Int = -1,
    val maxAnisotropy: Int = 0,
    val gpuProfile: Int = 0,
) {
    private fun put(section: String, key: String, type: String, value: String) {
        val sink = emitSink
        if (sink != null) sink(section, key, type, value)
        else NativeApp.setSetting(section, key, type, value)
    }

    fun applyTo() {
        put("EmuCore/Speedhacks", "EECycleRate", "int", eeCycleRate.toString())
        put("EmuCore/Speedhacks", "EECycleSkip", "int", eeCycleSkip.toString())
        put("EmuCore/CPU/Recompiler", "fpuOverflow", "bool", (eeClampMode >= 1).toString())
        put("EmuCore/CPU/Recompiler", "fpuExtraOverflow", "bool", (eeClampMode >= 2).toString())
        put("EmuCore/CPU/Recompiler", "fpuFullMode", "bool", (eeClampMode >= 3).toString())
        for (vu in arrayOf("vu0", "vu1")) {
            put("EmuCore/CPU/Recompiler", "${vu}Overflow", "bool", (vuClampMode >= 1).toString())
            put("EmuCore/CPU/Recompiler", "${vu}ExtraOverflow", "bool", (vuClampMode >= 2).toString())
            put("EmuCore/CPU/Recompiler", "${vu}SignOverflow", "bool", (vuClampMode >= 3).toString())
        }
        put("EmuCore/Speedhacks", "vuThread", "bool", mtvu.toString())
        put("EmuCore/Speedhacks", "vu1Instant", "bool", vu1Instant.toString())
        put("EmuCore/Speedhacks", "vuFlagHack", "bool", vuFlagHack.toString())
        put("EmuCore/Speedhacks", "fastCDVD", "bool", fastCDVD.toString())
        put("EmuCore/Speedhacks", "IntcStat", "bool", intcStat.toString())
        put("EmuCore/Speedhacks", "WaitLoop", "bool", waitLoop.toString())
        put("EmuCore/Speedhacks", "vuNeonFusions", "bool", vuNeonFusions.toString())
        put("EmuCore/Speedhacks", "vuDeferredWrites", "bool", vuDeferredWrites.toString())
        put("EmuCore/Speedhacks", "vuSkipStallSim", "bool", vuSkipStallSim.toString())
        put("EmuCore/GS", "FrameLimitEnable", "bool", frameLimitEnable.toString())
        if (emitSink == null) NativeApp.speedhackLimitermode(if (frameLimitEnable) 0 else 3)
        put("Framerate", "NominalScalar", "float",
            (nominalSpeedPercent.coerceIn(10, 1000) / 100f).toString())
        if (emitSink == null) NativeApp.setNominalSpeed(nominalSpeedPercent.coerceIn(10, 1000))
        if (emitSink == null) NativeApp.setFpsCap(fpsLimit.coerceIn(0, 1000))
        if (emitSink == null) NativeApp.setFrameSkip(frameSkip.coerceIn(0, 5))
        if (emitSink == null) NativeApp.setAudioVolume(audioVolume.coerceIn(0, 200))
        if (emitSink == null) NativeApp.setAudioMuted(audioMuted)
        if (emitSink == null) NativeApp.setAudioSwapChannels(audioSwapChannels)
        put("SPU2/Output", "SyncMode", "string", if (audioTimeStretch) "TimeStretch" else "Disabled")
        put("SPU2/Output", "BufferMS", "int", audioBufferMs.coerceIn(10, 200).toString())
        put("SPU2/Output", "OutputLatencyMS", "int", audioOutputLatencyMs.coerceIn(5, 200).toString())
        put("SPU2/Output", "FastForwardVolume", "int", audioFastForwardVolume.coerceIn(0, 200).toString())
        put("SPU2", "NeonReverbSIMD", "bool", spu2NeonReverb.toString())
        put("EmuCore", "EnablePatches", "bool", enablePatches.toString())
        put("EmuCore", "EnableCheats", "bool", enableCheats.toString())
        put("EmuCore", "EnableWideScreenPatches", "bool", enableWideScreenPatches.toString())
        put("EmuCore", "EnableNoInterlacingPatches", "bool", enableNoInterlacingPatches.toString())
        put("EmuCore", "EnableFastBoot", "bool", enableFastBoot.toString())
        put("EmuCore", "HostFs", "bool", hostFs.toString())
        put("EmuCore", "EnableGameFixes", "bool", enableGameFixes.toString())
        put("EmuCore/Gamefixes", "SoftwareRendererFMVHack", "bool", gamefixSoftwareRendererFmv.toString())
        put("EmuCore/Gamefixes", "SkipMPEGHack", "bool", gamefixSkipMpeg.toString())
        put("EmuCore/Gamefixes", "EETimingHack", "bool", gamefixEETiming.toString())
        put("EmuCore/Gamefixes", "InstantDMAHack", "bool", gamefixInstantDma.toString())
        put("EmuCore/Gamefixes", "BlitInternalFPSHack", "bool", gamefixBlitInternalFps.toString())
        put("EmuCore/Gamefixes", "FpuMulHack", "bool", gamefixFpuMul.toString())
        put("EmuCore/Gamefixes", "OPHFlagHack", "bool", gamefixOphFlag.toString())
        put("EmuCore/Gamefixes", "GIFFIFOHack", "bool", gamefixGifFifo.toString())
        put("EmuCore/Gamefixes", "DMABusyHack", "bool", gamefixDmaBusy.toString())
        put("EmuCore/Gamefixes", "VIF1StallHack", "bool", gamefixVif1Stall.toString())
        put("EmuCore/Gamefixes", "IbitHack", "bool", gamefixIbit.toString())
        put("EmuCore/Gamefixes", "FullVU0SyncHack", "bool", gamefixFullVu0Sync.toString())
        put("EmuCore/Gamefixes", "VuAddSubHack", "bool", gamefixVuAddSub.toString())
        put("EmuCore/Gamefixes", "VUOverflowHack", "bool", gamefixVuOverflow.toString())
        put("EmuCore/Gamefixes", "XgKickHack", "bool", gamefixXgkick.toString())
        put("EmuCore/Gamefixes", "GoemonTlbHack", "bool", gamefixGoemonTlb.toString())
        put("EmuCore/Gamefixes", "VUSyncHack", "bool", gamefixVuSync.toString())
        put("EmuCore/GS", "SkipDuplicateFrames", "bool", skipDuplicateFrames.toString())
        put("EmuCore/CPU", "FPU.Roundmode", "int", eeFpuRoundMode.coerceIn(0, 3).toString())
        put("EmuCore/CPU", "VU0.Roundmode", "int", vu0RoundMode.coerceIn(0, 3).toString())
        put("EmuCore/CPU", "VU1.Roundmode", "int", vu1RoundMode.coerceIn(0, 3).toString())
        put("DEV9/Eth", "EthEnable", "bool", dev9EthEnable.toString())
        put("DEV9/Eth", "EthApi", "string", dev9EthApi)
        put("DEV9/Eth", "EthDevice", "string", dev9EthDevice.ifEmpty { "Auto" })
        put("DEV9/Eth", "EthLogDHCP", "bool", dev9EthLogDhcp.toString())
        put("DEV9/Eth", "EthLogDNS", "bool", dev9EthLogDns.toString())
        put("DEV9/Eth", "InterceptDHCP", "bool", dev9InterceptDhcp.toString())
        put("DEV9/Eth", "PS2IP", "string", dev9Ps2Ip.ifEmpty { "0.0.0.0" })
        put("DEV9/Eth", "Mask", "string", dev9Mask.ifEmpty { "0.0.0.0" })
        put("DEV9/Eth", "Gateway", "string", dev9Gateway.ifEmpty { "0.0.0.0" })
        put("DEV9/Eth", "DNS1", "string", dev9Dns1.ifEmpty { "0.0.0.0" })
        put("DEV9/Eth", "DNS2", "string", dev9Dns2.ifEmpty { "0.0.0.0" })
        put("DEV9/Eth", "AutoMask", "bool", dev9AutoMask.toString())
        put("DEV9/Eth", "AutoGateway", "bool", dev9AutoGateway.toString())
        put("DEV9/Eth", "ModeDNS1", "string", dev9ModeDns1.ifEmpty { "Auto" })
        put("DEV9/Eth", "ModeDNS2", "string", dev9ModeDns2.ifEmpty { "Auto" })
        put("DEV9/Eth/Hosts", "Count", "int", dev9EthHosts.size.toString())
        dev9EthHosts.forEachIndexed { i, h ->
            put("DEV9/Eth/Hosts/Host$i", "Url", "string", h.url)
            put("DEV9/Eth/Hosts/Host$i", "Desc", "string", "ARMSX2")
            put("DEV9/Eth/Hosts/Host$i", "Address", "string", h.ip.ifEmpty { "0.0.0.0" })
            put("DEV9/Eth/Hosts/Host$i", "Enabled", "bool", h.enabled.toString())
        }
        put("DEV9/Hdd", "HddEnable", "bool", dev9HddEnable.toString())
        put("DEV9/Hdd", "HddFile", "string", dev9HddFile.ifEmpty { "DEV9hdd.raw" })
        put("MemoryCards", "Slot1_Enable", "bool", memoryCardSlot1Enabled.toString())
        put("MemoryCards", "Slot1_Filename", "string", memoryCardSlot1Filename.ifEmpty { "mcd001.ps2" })
        put("MemoryCards", "Slot2_Enable", "bool", memoryCardSlot2Enabled.toString())
        put("MemoryCards", "Slot2_Filename", "string", memoryCardSlot2Filename.ifEmpty { "mcd002.ps2" })
        put("USB1", "Type", "string", if (usbKeyboard) "hidkbd" else "None")
        put("EmuCore/CPU/Recompiler", "EnableEE", "bool", recEE.toString())
        put("EmuCore/CPU/Recompiler", "EnableIOP", "bool", recIOP.toString())
        put("EmuCore/CPU/Recompiler", "EnableVU0", "bool", recVU0.toString())
        put("EmuCore/CPU/Recompiler", "EnableVU1", "bool", recVU1.toString())
        put("EmuCore/CPU/Recompiler", "EnableFastmem", "bool", enableFastmem.toString())
        put("EmuCore/CPU/Recompiler", "UseMacEE", "bool", "true")
        put("EmuCore/CPU/Recompiler", "UseMacIOP", "bool", "true")
        put("EmuCore/CPU/Recompiler", "UseMacVU0", "bool", "true")
        put("EmuCore/CPU/Recompiler", "UseMacVU1", "bool", "true")
        put("EmuCore/CPU/Recompiler", "Vu1InlineFmacStall", "bool", vu1InlineFmacStall.toString())
        put("EmuCore/CPU/Recompiler", "Vu1CrossBlockPState", "bool", vu1CrossBlockPState.toString())
        put("EmuCore/CPU/Recompiler", "Vu1InlineDrainTestPipes", "bool", vu1InlineDrainTestPipes.toString())
        put("EmuCore/CPU/Recompiler", "Vu1FmacInstanceRouting", "bool", vu1FmacInstanceRouting.toString())
        writeGsToNative()
        if (emitSink != null) return
        NativeApp.setAspectRatio(aspectRatio.coerceIn(0, 4))
        NativeApp.setFmvAspectRatio(fmvAspectRatio.coerceIn(0, 4))
        NativeApp.renderTvShader(tvShader.coerceIn(0, 7))
        NativeApp.renderShadeBoost(
            shadeBoost,
            shadeBoostBrightness.coerceIn(1, 100),
            shadeBoostContrast.coerceIn(1, 100),
            shadeBoostSaturation.coerceIn(1, 100),
            shadeBoostGamma.coerceIn(1, 100),
        )
        NativeApp.osdShowFPS(osdShowFps)
        NativeApp.osdSetScale(osdScale.toFloat())
        NativeApp.osdShowVPS(osdShowVps)
        NativeApp.osdShowSpeed(osdShowSpeed)
        NativeApp.osdShowCPU(osdShowCpu)
        NativeApp.osdShowGPU(osdShowGpu)
        NativeApp.osdShowResolution(osdShowResolution)
        NativeApp.osdShowGSStats(osdShowGsStats)
        NativeApp.osdShowFrameTimes(osdShowFrameTimes)
        NativeApp.osdShowHardwareInfo(osdShowHardwareInfo)
        NativeApp.osdShowMessages(osdShowMessages)
        NativeApp.osdShowGpuStats(osdShowGpuStats)
        NativeApp.osdShowVersion(osdShowVersion)
        NativeApp.osdShowSettings(osdShowSettings)
        NativeApp.osdShowInputs(osdShowInputs)
        NativeApp.usbSetKeyboardEnabled(0, usbKeyboard)
        NativeApp.commitSettings()
    }

    fun readFromIni(ini: Map<String, String>): Settings {
        fun boolAt(key: String): Boolean? = ini[key]?.let { it == "true" || it == "1" }
        fun intAt(key: String): Int? = ini[key]?.toIntOrNull()
        fun floatAt(key: String): Float? = ini[key]?.toFloatOrNull()
        fun strAt(key: String): String? = ini[key]

        val eeClamp = run {
            val fo = boolAt("EmuCore/CPU/Recompiler/fpuOverflow")
            val fe = boolAt("EmuCore/CPU/Recompiler/fpuExtraOverflow")
            val ff = boolAt("EmuCore/CPU/Recompiler/fpuFullMode")
            if (fo == null && fe == null && ff == null) this.eeClampMode
            else if (ff == true) 3 else if (fe == true) 2 else if (fo == true) 1 else 0
        }
        val vuClamp = run {
            val o = boolAt("EmuCore/CPU/Recompiler/vu0Overflow")
            val e = boolAt("EmuCore/CPU/Recompiler/vu0ExtraOverflow")
            val sgn = boolAt("EmuCore/CPU/Recompiler/vu0SignOverflow")
            if (o == null && e == null && sgn == null) this.vuClampMode
            else if (sgn == true) 3 else if (e == true) 2 else if (o == true) 1 else 0
        }

        val recoveredRenderer = when (intAt("EmuCore/GS/Renderer")) {
            -1 -> "auto"
            12 -> "opengl"
            13 -> "software"
            14 -> "vulkan"
            else -> this.renderer
        }

        return this.copy(
            renderer = recoveredRenderer,
            upscaleFloat = floatAt("EmuCore/GS/upscale_multiplier") ?: this.upscaleFloat,
            eeCycleRate = intAt("EmuCore/Speedhacks/EECycleRate") ?: this.eeCycleRate,
            eeCycleSkip = intAt("EmuCore/Speedhacks/EECycleSkip") ?: this.eeCycleSkip,
            eeClampMode = eeClamp,
            vuClampMode = vuClamp,
            mtvu = boolAt("EmuCore/Speedhacks/vuThread") ?: this.mtvu,
            vu1Instant = boolAt("EmuCore/Speedhacks/vu1Instant") ?: this.vu1Instant,
            vuFlagHack = boolAt("EmuCore/Speedhacks/vuFlagHack") ?: this.vuFlagHack,
            fastCDVD = boolAt("EmuCore/Speedhacks/fastCDVD") ?: this.fastCDVD,
            intcStat = boolAt("EmuCore/Speedhacks/IntcStat") ?: this.intcStat,
            waitLoop = boolAt("EmuCore/Speedhacks/WaitLoop") ?: this.waitLoop,
            vuNeonFusions = boolAt("EmuCore/Speedhacks/vuNeonFusions") ?: this.vuNeonFusions,
            vuDeferredWrites = boolAt("EmuCore/Speedhacks/vuDeferredWrites") ?: this.vuDeferredWrites,
            vuSkipStallSim = boolAt("EmuCore/Speedhacks/vuSkipStallSim") ?: this.vuSkipStallSim,
            frameLimitEnable = boolAt("EmuCore/GS/FrameLimitEnable") ?: this.frameLimitEnable,
            nominalSpeedPercent = floatAt("Framerate/NominalScalar")?.let { Math.round(it * 100f) }
                ?: this.nominalSpeedPercent,
            audioTimeStretch = strAt("SPU2/Output/SyncMode")?.let { it == "TimeStretch" } ?: this.audioTimeStretch,
            audioBufferMs = intAt("SPU2/Output/BufferMS") ?: this.audioBufferMs,
            audioOutputLatencyMs = intAt("SPU2/Output/OutputLatencyMS") ?: this.audioOutputLatencyMs,
            audioFastForwardVolume = intAt("SPU2/Output/FastForwardVolume") ?: this.audioFastForwardVolume,
            spu2NeonReverb = boolAt("SPU2/NeonReverbSIMD") ?: this.spu2NeonReverb,
            enablePatches = boolAt("EmuCore/EnablePatches") ?: this.enablePatches,
            enableCheats = boolAt("EmuCore/EnableCheats") ?: this.enableCheats,
            enableWideScreenPatches = boolAt("EmuCore/EnableWideScreenPatches") ?: this.enableWideScreenPatches,
            enableNoInterlacingPatches = boolAt("EmuCore/EnableNoInterlacingPatches") ?: this.enableNoInterlacingPatches,
            enableFastBoot = boolAt("EmuCore/EnableFastBoot") ?: this.enableFastBoot,
            hostFs = boolAt("EmuCore/HostFs") ?: this.hostFs,
            enableGameFixes = boolAt("EmuCore/EnableGameFixes") ?: this.enableGameFixes,
            gamefixSoftwareRendererFmv = boolAt("EmuCore/Gamefixes/SoftwareRendererFMVHack") ?: this.gamefixSoftwareRendererFmv,
            gamefixSkipMpeg = boolAt("EmuCore/Gamefixes/SkipMPEGHack") ?: this.gamefixSkipMpeg,
            gamefixEETiming = boolAt("EmuCore/Gamefixes/EETimingHack") ?: this.gamefixEETiming,
            gamefixInstantDma = boolAt("EmuCore/Gamefixes/InstantDMAHack") ?: this.gamefixInstantDma,
            gamefixBlitInternalFps = boolAt("EmuCore/Gamefixes/BlitInternalFPSHack") ?: this.gamefixBlitInternalFps,
            gamefixFpuMul = boolAt("EmuCore/Gamefixes/FpuMulHack") ?: this.gamefixFpuMul,
            gamefixOphFlag = boolAt("EmuCore/Gamefixes/OPHFlagHack") ?: this.gamefixOphFlag,
            gamefixGifFifo = boolAt("EmuCore/Gamefixes/GIFFIFOHack") ?: this.gamefixGifFifo,
            gamefixDmaBusy = boolAt("EmuCore/Gamefixes/DMABusyHack") ?: this.gamefixDmaBusy,
            gamefixVif1Stall = boolAt("EmuCore/Gamefixes/VIF1StallHack") ?: this.gamefixVif1Stall,
            gamefixIbit = boolAt("EmuCore/Gamefixes/IbitHack") ?: this.gamefixIbit,
            gamefixFullVu0Sync = boolAt("EmuCore/Gamefixes/FullVU0SyncHack") ?: this.gamefixFullVu0Sync,
            gamefixVuAddSub = boolAt("EmuCore/Gamefixes/VuAddSubHack") ?: this.gamefixVuAddSub,
            gamefixVuOverflow = boolAt("EmuCore/Gamefixes/VUOverflowHack") ?: this.gamefixVuOverflow,
            gamefixXgkick = boolAt("EmuCore/Gamefixes/XgKickHack") ?: this.gamefixXgkick,
            gamefixGoemonTlb = boolAt("EmuCore/Gamefixes/GoemonTlbHack") ?: this.gamefixGoemonTlb,
            gamefixVuSync = boolAt("EmuCore/Gamefixes/VUSyncHack") ?: this.gamefixVuSync,
            skipDuplicateFrames = boolAt("EmuCore/GS/SkipDuplicateFrames") ?: this.skipDuplicateFrames,
            eeFpuRoundMode = intAt("EmuCore/CPU/FPU.Roundmode") ?: this.eeFpuRoundMode,
            vu0RoundMode = intAt("EmuCore/CPU/VU0.Roundmode") ?: this.vu0RoundMode,
            vu1RoundMode = intAt("EmuCore/CPU/VU1.Roundmode") ?: this.vu1RoundMode,
            dev9EthEnable = boolAt("DEV9/Eth/EthEnable") ?: this.dev9EthEnable,
            dev9EthApi = strAt("DEV9/Eth/EthApi") ?: this.dev9EthApi,
            dev9EthDevice = strAt("DEV9/Eth/EthDevice") ?: this.dev9EthDevice,
            dev9EthLogDhcp = boolAt("DEV9/Eth/EthLogDHCP") ?: this.dev9EthLogDhcp,
            dev9EthLogDns = boolAt("DEV9/Eth/EthLogDNS") ?: this.dev9EthLogDns,
            dev9InterceptDhcp = boolAt("DEV9/Eth/InterceptDHCP") ?: this.dev9InterceptDhcp,
            dev9Ps2Ip = strAt("DEV9/Eth/PS2IP") ?: this.dev9Ps2Ip,
            dev9Mask = strAt("DEV9/Eth/Mask") ?: this.dev9Mask,
            dev9Gateway = strAt("DEV9/Eth/Gateway") ?: this.dev9Gateway,
            dev9Dns1 = strAt("DEV9/Eth/DNS1") ?: this.dev9Dns1,
            dev9Dns2 = strAt("DEV9/Eth/DNS2") ?: this.dev9Dns2,
            dev9AutoMask = boolAt("DEV9/Eth/AutoMask") ?: this.dev9AutoMask,
            dev9AutoGateway = boolAt("DEV9/Eth/AutoGateway") ?: this.dev9AutoGateway,
            dev9ModeDns1 = strAt("DEV9/Eth/ModeDNS1") ?: this.dev9ModeDns1,
            dev9ModeDns2 = strAt("DEV9/Eth/ModeDNS2") ?: this.dev9ModeDns2,
            dev9EthHosts = run {
                val count = intAt("DEV9/Eth/Hosts/Count") ?: return@run this.dev9EthHosts
                (0 until count).mapNotNull { idx ->
                    val url = ini["DEV9/Eth/Hosts/Host$idx/Url"] ?: return@mapNotNull null
                    Dev9HostMapping(
                        url = url,
                        ip = (ini["DEV9/Eth/Hosts/Host$idx/Address"] ?: "0.0.0.0").ifEmpty { "0.0.0.0" },
                        enabled = boolAt("DEV9/Eth/Hosts/Host$idx/Enabled") ?: true,
                    )
                }.filter { it.url.isNotBlank() }
            },
            dev9HddEnable = boolAt("DEV9/Hdd/HddEnable") ?: this.dev9HddEnable,
            dev9HddFile = strAt("DEV9/Hdd/HddFile") ?: this.dev9HddFile,
            memoryCardSlot1Enabled = boolAt("MemoryCards/Slot1_Enable") ?: this.memoryCardSlot1Enabled,
            memoryCardSlot1Filename = strAt("MemoryCards/Slot1_Filename") ?: this.memoryCardSlot1Filename,
            memoryCardSlot2Enabled = boolAt("MemoryCards/Slot2_Enable") ?: this.memoryCardSlot2Enabled,
            memoryCardSlot2Filename = strAt("MemoryCards/Slot2_Filename") ?: this.memoryCardSlot2Filename,
            usbKeyboard = strAt("USB1/Type")?.let { it == "hidkbd" } ?: this.usbKeyboard,
            recEE = boolAt("EmuCore/CPU/Recompiler/EnableEE") ?: this.recEE,
            recIOP = boolAt("EmuCore/CPU/Recompiler/EnableIOP") ?: this.recIOP,
            recVU0 = boolAt("EmuCore/CPU/Recompiler/EnableVU0") ?: this.recVU0,
            recVU1 = boolAt("EmuCore/CPU/Recompiler/EnableVU1") ?: this.recVU1,
            enableFastmem = boolAt("EmuCore/CPU/Recompiler/EnableFastmem") ?: this.enableFastmem,
            useMacEE = true,
            useMacIOP = true,
            useMacVU0 = true,
            useMacVU1 = true,
            vu1InlineFmacStall = boolAt("EmuCore/CPU/Recompiler/Vu1InlineFmacStall") ?: this.vu1InlineFmacStall,
            vu1CrossBlockPState = boolAt("EmuCore/CPU/Recompiler/Vu1CrossBlockPState") ?: this.vu1CrossBlockPState,
            vu1InlineDrainTestPipes = boolAt("EmuCore/CPU/Recompiler/Vu1InlineDrainTestPipes") ?: this.vu1InlineDrainTestPipes,
            vu1FmacInstanceRouting = boolAt("EmuCore/CPU/Recompiler/Vu1FmacInstanceRouting") ?: this.vu1FmacInstanceRouting,
            aspectRatio = when (strAt("EmuCore/GS/AspectRatio")) {
                "Stretch" -> 0
                "Auto 4:3/3:2" -> 1
                "4:3" -> 2
                "16:9" -> 3
                "10:7" -> 4
                else -> this.aspectRatio
            },
            fmvAspectRatio = when (strAt("EmuCore/GS/FMVAspectRatioSwitch")) {
                "Off" -> 0
                "Auto 4:3/3:2" -> 1
                "4:3" -> 2
                "16:9" -> 3
                "10:7" -> 4
                else -> this.fmvAspectRatio
            },
            deinterlaceMode = intAt("EmuCore/GS/deinterlace_mode") ?: this.deinterlaceMode,
            framerateNtsc = floatAt("EmuCore/GS/FramerateNTSC") ?: this.framerateNtsc,
            frameratePal = floatAt("EmuCore/GS/FrameratePAL") ?: this.frameratePal,
            hwMipmap = boolAt("EmuCore/GS/hw_mipmap") ?: this.hwMipmap,
            accurateBlendingUnit = intAt("EmuCore/GS/accurate_blending_unit") ?: this.accurateBlendingUnit,
            textureFiltering = intAt("EmuCore/GS/filter") ?: this.textureFiltering,
            displayBilinear = intAt("EmuCore/GS/linear_present_mode") ?: this.displayBilinear,
            texturePreloading = intAt("EmuCore/GS/texture_preloading") ?: this.texturePreloading,
            hardwareDownloadMode = intAt("EmuCore/GS/HWDownloadMode") ?: this.hardwareDownloadMode,
            tvShader = intAt("EmuCore/GS/TVShader") ?: this.tvShader,
            shadeBoost = boolAt("EmuCore/GS/ShadeBoost") ?: this.shadeBoost,
            shadeBoostBrightness = intAt("EmuCore/GS/ShadeBoost_Brightness") ?: this.shadeBoostBrightness,
            shadeBoostContrast = intAt("EmuCore/GS/ShadeBoost_Contrast") ?: this.shadeBoostContrast,
            shadeBoostSaturation = intAt("EmuCore/GS/ShadeBoost_Saturation") ?: this.shadeBoostSaturation,
            shadeBoostGamma = intAt("EmuCore/GS/ShadeBoost_Gamma") ?: this.shadeBoostGamma,
            fxaa = boolAt("EmuCore/GS/fxaa") ?: this.fxaa,
            shaderChainEnabled = boolAt("EmuCore/GS/ShaderChainEnabled") ?: this.shaderChainEnabled,
            shaderChainPreset = strAt("EmuCore/GS/ShaderChainPreset") ?: this.shaderChainPreset,
            shaderChainParams = strAt("EmuCore/GS/ShaderChainParams")?.let { raw ->
                runCatching { shaderChainParamsFromJson(JSONObject(raw)) }.getOrNull()
            } ?: this.shaderChainParams,
            casMode = intAt("EmuCore/GS/CASMode") ?: this.casMode,
            casSharpness = intAt("EmuCore/GS/CASSharpness") ?: this.casSharpness,
            loadTextureReplacements = boolAt("EmuCore/GS/LoadTextureReplacements") ?: this.loadTextureReplacements,
            loadTextureReplacementsAsync = boolAt("EmuCore/GS/LoadTextureReplacementsAsync") ?: this.loadTextureReplacementsAsync,
            precacheTextureReplacements = boolAt("EmuCore/GS/PrecacheTextureReplacements") ?: this.precacheTextureReplacements,
            dumpReplaceableTextures = boolAt("EmuCore/GS/DumpReplaceableTextures") ?: this.dumpReplaceableTextures,
            osdShowTextureReplacements = boolAt("EmuCore/GS/OsdShowTextureReplacements") ?: this.osdShowTextureReplacements,
            osdShowFps = boolAt("EmuCore/GS/OsdShowFPS") ?: this.osdShowFps,
            osdScale = intAt("EmuCore/GS/OsdScale") ?: this.osdScale,
            vsyncEnable = boolAt("EmuCore/GS/VsyncEnable") ?: this.vsyncEnable,
            osdShowVps = boolAt("EmuCore/GS/OsdShowVPS") ?: this.osdShowVps,
            osdShowSpeed = boolAt("EmuCore/GS/OsdShowSpeed") ?: this.osdShowSpeed,
            osdShowCpu = boolAt("EmuCore/GS/OsdShowCPU") ?: this.osdShowCpu,
            osdShowGpu = boolAt("EmuCore/GS/OsdShowGPU") ?: this.osdShowGpu,
            osdShowResolution = boolAt("EmuCore/GS/OsdShowResolution") ?: this.osdShowResolution,
            osdShowGsStats = boolAt("EmuCore/GS/OsdShowGSStats") ?: this.osdShowGsStats,
            osdShowFrameTimes = boolAt("EmuCore/GS/OsdShowFrameTimes") ?: this.osdShowFrameTimes,
            osdShowHardwareInfo = boolAt("EmuCore/GS/OsdShowHardwareInfo") ?: this.osdShowHardwareInfo,
            osdShowMessages = intAt("EmuCore/GS/OsdMessagesPos")?.let { it != 0 } ?: this.osdShowMessages,
            osdShowGpuStats = boolAt("EmuCore/GS/OsdShowGPUStats") ?: this.osdShowGpuStats,
            osdShowVersion = boolAt("EmuCore/GS/OsdShowVersion") ?: this.osdShowVersion,
            osdShowSettings = boolAt("EmuCore/GS/OsdShowSettings") ?: this.osdShowSettings,
            osdShowInputs = boolAt("EmuCore/GS/OsdShowInputs") ?: this.osdShowInputs,
            screenOffsets = boolAt("EmuCore/GS/pcrtc_offsets") ?: this.screenOffsets,
            showOverscan = boolAt("EmuCore/GS/pcrtc_overscan") ?: this.showOverscan,
            antiBlur = boolAt("EmuCore/GS/pcrtc_antiblur") ?: this.antiBlur,
            disableInterlaceOffset = boolAt("EmuCore/GS/disable_interlace_offset") ?: this.disableInterlaceOffset,
            syncToHostRefresh = boolAt("EmuCore/GS/SyncToHostRefreshRate") ?: this.syncToHostRefresh,
            disableFramebufferFetch = boolAt("EmuCore/GS/DisableFramebufferFetch") ?: this.disableFramebufferFetch,
            hwRov = boolAt("EmuCore/GS/HWROV") ?: this.hwRov,
            hwAa1 = boolAt("EmuCore/GS/HWAA1") ?: this.hwAa1,
            adrenoFbFetch = boolAt("EmuCore/GS/EnableAdrenoFramebufferFetch") ?: this.adrenoFbFetch,
            forceMaliFbFetch = boolAt("EmuCore/GS/ForceMaliFramebufferFetch") ?: this.forceMaliFbFetch,
            useAngleOpenGL = boolAt("EmuCore/GS/AndroidUseAngleOpenGL") ?: this.useAngleOpenGL,
            overrideTextureBarriers = intAt("EmuCore/GS/OverrideTextureBarriers") ?: this.overrideTextureBarriers,
            disableVertexShaderExpand = boolAt("EmuCore/GS/DisableVertexShaderExpand") ?: this.disableVertexShaderExpand,
            useBlitSwapChain = boolAt("EmuCore/GS/UseBlitSwapChain") ?: this.useBlitSwapChain,
            disableShaderCache = boolAt("EmuCore/GS/DisableShaderCache") ?: this.disableShaderCache,
            hwAccurateAlphaTest = boolAt("EmuCore/GS/HWAccurateAlphaTest") ?: this.hwAccurateAlphaTest,
            drawBuffering = boolAt("EmuCore/GS/UserHacks_DrawBuffering") ?: this.drawBuffering,
            spinGpuReadbacks = boolAt("EmuCore/GS/HWSpinGPUForReadbacks") ?: this.spinGpuReadbacks,
            spinCpuReadbacks = boolAt("EmuCore/GS/HWSpinCPUForReadbacks") ?: this.spinCpuReadbacks,
            integerScaling = boolAt("EmuCore/GS/IntegerScaling") ?: this.integerScaling,
            dithering = intAt("EmuCore/GS/dithering_ps2") ?: this.dithering,
            vsyncQueueSize = intAt("EmuCore/GS/VsyncQueueSize") ?: this.vsyncQueueSize,
            autoFlushSw = boolAt("EmuCore/GS/autoflush_sw") ?: this.autoFlushSw,
            mipmapSw = boolAt("EmuCore/GS/mipmap") ?: this.mipmapSw,
            swThreads = intAt("EmuCore/GS/extrathreads") ?: this.swThreads,
            swThreadsHeight = intAt("EmuCore/GS/extrathreads_height") ?: this.swThreadsHeight,
            skipDrawStart = intAt("EmuCore/GS/UserHacks_SkipDraw_Start") ?: this.skipDrawStart,
            skipDrawEnd = intAt("EmuCore/GS/UserHacks_SkipDraw_End") ?: this.skipDrawEnd,
            manualUserHacks = boolAt("EmuCore/GS/UserHacks") ?: this.manualUserHacks,
            autoFlush = intAt("EmuCore/GS/UserHacks_AutoFlushLevel") ?: this.autoFlush,
            halfPixelOffset = intAt("EmuCore/GS/UserHacks_HalfPixelOffset") ?: this.halfPixelOffset,
            limit24BitDepth = intAt("EmuCore/GS/UserHacks_Limit24BitDepth") ?: this.limit24BitDepth,
            textureInsideRt = intAt("EmuCore/GS/UserHacks_TextureInsideRt") ?: this.textureInsideRt,
            nativeScaling = intAt("EmuCore/GS/UserHacks_native_scaling") ?: this.nativeScaling,
            roundSprite = intAt("EmuCore/GS/UserHacks_round_sprite_offset") ?: this.roundSprite,
            bilinearUpscale = intAt("EmuCore/GS/UserHacks_BilinearHack") ?: this.bilinearUpscale,
            gpuTargetClut = intAt("EmuCore/GS/UserHacks_GPUTargetCLUTMode") ?: this.gpuTargetClut,
            cpuSpriteRenderBw = intAt("EmuCore/GS/UserHacks_CPUSpriteRenderBW") ?: this.cpuSpriteRenderBw,
            cpuSpriteRenderLevel = intAt("EmuCore/GS/UserHacks_CPUSpriteRenderLevel") ?: this.cpuSpriteRenderLevel,
            cpuClutRender = intAt("EmuCore/GS/UserHacks_CPUCLUTRender") ?: this.cpuClutRender,
            alignSprite = boolAt("EmuCore/GS/UserHacks_align_sprite_X") ?: this.alignSprite,
            mergeSprite = boolAt("EmuCore/GS/UserHacks_merge_pp_sprite") ?: this.mergeSprite,
            forceEvenSpritePosition = boolAt("EmuCore/GS/UserHacks_ForceEvenSpritePosition") ?: this.forceEvenSpritePosition,
            unscaledPaletteDraw = boolAt("EmuCore/GS/UserHacks_NativePaletteDraw") ?: this.unscaledPaletteDraw,
            textureOffsetX = intAt("EmuCore/GS/UserHacks_TCOffsetX") ?: this.textureOffsetX,
            textureOffsetY = intAt("EmuCore/GS/UserHacks_TCOffsetY") ?: this.textureOffsetY,
            gpuPaletteConversion = boolAt("EmuCore/GS/paltex") ?: this.gpuPaletteConversion,
            cpuFramebufferConversion = boolAt("EmuCore/GS/UserHacks_CPU_FB_Conversion") ?: this.cpuFramebufferConversion,
            readTargetsWhenClosing = boolAt("EmuCore/GS/UserHacks_ReadTCOnClose") ?: this.readTargetsWhenClosing,
            disableDepthEmulation = boolAt("EmuCore/GS/UserHacks_DisableDepthSupport") ?: this.disableDepthEmulation,
            disablePartialInvalidation = boolAt("EmuCore/GS/UserHacks_DisablePartialInvalidation") ?: this.disablePartialInvalidation,
            disableSafeFeatures = boolAt("EmuCore/GS/UserHacks_Disable_Safe_Features") ?: this.disableSafeFeatures,
            disableRenderFixes = boolAt("EmuCore/GS/UserHacks_DisableRenderFixes") ?: this.disableRenderFixes,
            preloadFrameData = boolAt("EmuCore/GS/preload_frame_with_gs_data") ?: this.preloadFrameData,
            estimateTextureRegion = boolAt("EmuCore/GS/UserHacks_EstimateTextureRegion") ?: this.estimateTextureRegion,
            triFilter = intAt("EmuCore/GS/TriFilter") ?: this.triFilter,
            maxAnisotropy = intAt("EmuCore/GS/MaxAnisotropy") ?: this.maxAnisotropy,
            gpuProfile = when (strAt("EmuCore/GS/AndroidGpuProfileOverride")) {
                "mali" -> 1
                "adreno" -> 2
                "powervr" -> 3
                "xclipse" -> 4
                "auto" -> 0
                else -> this.gpuProfile
            },
        )
    }

    fun writeGameSettingsIni(global: Settings) {
        val baseline = HashMap<String, String>()
        emitSink = { section, key, _, value -> baseline["$section$key"] = value }
        try {
            global.applyTo()
        } finally {
            emitSink = null
        }
        if (!NativeApp.gameIniBeginWrite()) return
        emitSink = { section, key, _, value ->
            if (baseline["$section$key"] != value)
                NativeApp.gameIniPut(section, key, value)
        }
        try {
            applyTo()
        } finally {
            emitSink = null
        }
        NativeApp.gameIniCommitWrite()
    }

    private fun writeGsToNative() {
        val aspectRatioName = when (aspectRatio.coerceIn(0, 4)) {
            0 -> "Stretch"
            2 -> "4:3"
            3 -> "16:9"
            4 -> "10:7"
            else -> "Auto 4:3/3:2"
        }
        put("EmuCore/GS", "AspectRatio", "string", aspectRatioName)
        val fmvAspectRatioName = when (fmvAspectRatio.coerceIn(0, 4)) {
            1 -> "Auto 4:3/3:2"
            2 -> "4:3"
            3 -> "16:9"
            4 -> "10:7"
            else -> "Off"
        }
        put("EmuCore/GS", "FMVAspectRatioSwitch", "string", fmvAspectRatioName)
        put("EmuCore/GS", "deinterlace_mode", "int", deinterlaceMode.coerceIn(0, 9).toString())
        put("EmuCore/GS", "FramerateNTSC", "float", framerateNtsc.toString())
        put("EmuCore/GS", "FrameratePAL", "float", frameratePal.toString())
        put("EmuCore/GS", "hw_mipmap", "bool", hwMipmap.toString())
        put("EmuCore/GS", "accurate_blending_unit", "int", accurateBlendingUnit.toString())
        put("EmuCore/GS", "filter", "int", textureFiltering.toString())
        put("EmuCore/GS", "linear_present_mode", "int", displayBilinear.coerceIn(0, 2).toString())
        put("EmuCore/GS", "texture_preloading", "int", texturePreloading.toString())
        put("EmuCore/GS", "HWDownloadMode", "int", hardwareDownloadMode.coerceIn(0, 4).toString())
        put("EmuCore/GS", "TVShader", "int", tvShader.coerceIn(0, 7).toString())
        put("EmuCore/GS", "ShadeBoost", "bool", shadeBoost.toString())
        put("EmuCore/GS", "ShadeBoost_Brightness", "int", shadeBoostBrightness.coerceIn(1, 100).toString())
        put("EmuCore/GS", "ShadeBoost_Contrast", "int", shadeBoostContrast.coerceIn(1, 100).toString())
        put("EmuCore/GS", "ShadeBoost_Saturation", "int", shadeBoostSaturation.coerceIn(1, 100).toString())
        put("EmuCore/GS", "ShadeBoost_Gamma", "int", shadeBoostGamma.coerceIn(1, 100).toString())
        put("EmuCore/GS", "fxaa", "bool", fxaa.toString())
        put("EmuCore/GS", "ShaderChainEnabled", "bool", shaderChainEnabled.toString())
        put("EmuCore/GS", "ShaderChainPreset", "string", shaderChainPreset)
        put("EmuCore/GS", "ShaderChainParams", "string", shaderChainParamsToJson(shaderChainParams).toString())
        if (emitSink == null)
            ShaderParams.push(shaderChainPreset, shaderChainParams[shaderChainPreset].orEmpty())
        put("EmuCore/GS", "CASMode", "int", casMode.coerceIn(0, 2).toString())
        put("EmuCore/GS", "CASSharpness", "int", casSharpness.coerceIn(0, 100).toString())
        put("EmuCore/GS", "LoadTextureReplacements", "bool", loadTextureReplacements.toString())
        put("EmuCore/GS", "LoadTextureReplacementsAsync", "bool", loadTextureReplacementsAsync.toString())
        put("EmuCore/GS", "PrecacheTextureReplacements", "bool", precacheTextureReplacements.toString())
        put("EmuCore/GS", "DumpReplaceableTextures", "bool", dumpReplaceableTextures.toString())
        put("EmuCore/GS", "OsdShowTextureReplacements", "bool", osdShowTextureReplacements.toString())
        put("EmuCore/GS", "OsdShowFPS", "bool", osdShowFps.toString())
        put("EmuCore/GS", "OsdScale", "int", osdScale.coerceIn(25, 500).toString())
        put("EmuCore/GS", "VsyncEnable", "bool", vsyncEnable.toString())
        put("EmuCore/GS", "OsdShowVPS", "bool", osdShowVps.toString())
        put("EmuCore/GS", "OsdShowSpeed", "bool", osdShowSpeed.toString())
        put("EmuCore/GS", "OsdShowCPU", "bool", osdShowCpu.toString())
        put("EmuCore/GS", "OsdShowGPU", "bool", osdShowGpu.toString())
        put("EmuCore/GS", "OsdShowResolution", "bool", osdShowResolution.toString())
        put("EmuCore/GS", "OsdShowGSStats", "bool", osdShowGsStats.toString())
        put("EmuCore/GS", "OsdShowFrameTimes", "bool", osdShowFrameTimes.toString())
        put("EmuCore/GS", "OsdShowHardwareInfo", "bool", osdShowHardwareInfo.toString())
        put("EmuCore/GS", "OsdMessagesPos", "int", if (osdShowMessages) "1" else "0")
        put("EmuCore/GS", "OsdShowGPUStats", "bool", osdShowGpuStats.toString())
        put("EmuCore/GS", "OsdShowVersion", "bool", osdShowVersion.toString())
        put("EmuCore/GS", "OsdShowSettings", "bool", osdShowSettings.toString())
        put("EmuCore/GS", "OsdShowInputs", "bool", osdShowInputs.toString())
        put("EmuCore/GS", "pcrtc_offsets", "bool", screenOffsets.toString())
        put("EmuCore/GS", "pcrtc_overscan", "bool", showOverscan.toString())
        put("EmuCore/GS", "pcrtc_antiblur", "bool", antiBlur.toString())
        put("EmuCore/GS", "disable_interlace_offset", "bool", disableInterlaceOffset.toString())
        put("EmuCore/GS", "SyncToHostRefreshRate", "bool", syncToHostRefresh.toString())
        put("EmuCore/GS", "DisableFramebufferFetch", "bool", disableFramebufferFetch.toString())
        put("EmuCore/GS", "HWROV", "bool", hwRov.toString())
        put("EmuCore/GS", "HWAA1", "bool", hwAa1.toString())
        put("EmuCore/GS", "EnableAdrenoFramebufferFetch", "bool", adrenoFbFetch.toString())
        put("EmuCore/GS", "ForceMaliFramebufferFetch", "bool", forceMaliFbFetch.toString())
        put("EmuCore/GS", "AndroidUseAngleOpenGL", "bool", useAngleOpenGL.toString())
        put("EmuCore/GS", "OverrideTextureBarriers", "int", overrideTextureBarriers.coerceIn(-1, 1).toString())
        put("EmuCore/GS", "DisableVertexShaderExpand", "bool", disableVertexShaderExpand.toString())
        put("EmuCore/GS", "UseBlitSwapChain", "bool", useBlitSwapChain.toString())
        put("EmuCore/GS", "DisableShaderCache", "bool", disableShaderCache.toString())
        put("EmuCore/GS", "HWAccurateAlphaTest", "bool", hwAccurateAlphaTest.toString())
        put("EmuCore/GS", "UserHacks_DrawBuffering", "bool", drawBuffering.toString())
        put("EmuCore/GS", "HWSpinGPUForReadbacks", "bool", spinGpuReadbacks.toString())
        put("EmuCore/GS", "HWSpinCPUForReadbacks", "bool", spinCpuReadbacks.toString())
        put("EmuCore/GS", "IntegerScaling", "bool", integerScaling.toString())
        put("EmuCore/GS", "dithering_ps2", "int", dithering.coerceIn(0, 3).toString())
        put("EmuCore/GS", "VsyncQueueSize", "int", vsyncQueueSize.coerceIn(0, 3).toString())
        put("EmuCore/GS", "autoflush_sw", "bool", autoFlushSw.toString())
        put("EmuCore/GS", "mipmap", "bool", mipmapSw.toString())
        put("EmuCore/GS", "extrathreads", "int", swThreads.coerceIn(0, 10).toString())
        put("EmuCore/GS", "extrathreads_height", "int", swThreadsHeight.coerceIn(0, 8).toString())
        put("EmuCore/GS", "UserHacks_SkipDraw_Start", "int", skipDrawStart.coerceAtLeast(0).toString())
        put("EmuCore/GS", "UserHacks_SkipDraw_End", "int", skipDrawEnd.coerceAtLeast(0).toString())
        put("EmuCore/GS", "UserHacks", "bool", anyUserHackEnabled().toString())
        put("EmuCore/GS", "UserHacks_AutoFlushLevel", "int", autoFlush.coerceIn(0, 2).toString())
        put("EmuCore/GS", "UserHacks_HalfPixelOffset", "int", halfPixelOffset.coerceIn(0, 5).toString())
        put("EmuCore/GS", "UserHacks_Limit24BitDepth", "int", limit24BitDepth.coerceIn(0, 2).toString())
        put("EmuCore/GS", "UserHacks_TextureInsideRt", "int", textureInsideRt.coerceIn(0, 2).toString())
        put("EmuCore/GS", "UserHacks_native_scaling", "int", nativeScaling.coerceIn(0, 4).toString())
        put("EmuCore/GS", "UserHacks_round_sprite_offset", "int", roundSprite.coerceIn(0, 2).toString())
        put("EmuCore/GS", "UserHacks_BilinearHack", "int", bilinearUpscale.coerceIn(0, 3).toString())
        put("EmuCore/GS", "UserHacks_GPUTargetCLUTMode", "int", gpuTargetClut.coerceIn(0, 2).toString())
        put("EmuCore/GS", "UserHacks_CPUSpriteRenderBW", "int", cpuSpriteRenderBw.coerceIn(0, 3).toString())
        put("EmuCore/GS", "UserHacks_CPUSpriteRenderLevel", "int", cpuSpriteRenderLevel.coerceIn(0, 5).toString())
        put("EmuCore/GS", "UserHacks_CPUCLUTRender", "int", cpuClutRender.coerceIn(0, 2).toString())
        put("EmuCore/GS", "UserHacks_align_sprite_X", "bool", alignSprite.toString())
        put("EmuCore/GS", "UserHacks_merge_pp_sprite", "bool", mergeSprite.toString())
        put("EmuCore/GS", "UserHacks_ForceEvenSpritePosition", "bool", forceEvenSpritePosition.toString())
        put("EmuCore/GS", "UserHacks_NativePaletteDraw", "bool", unscaledPaletteDraw.toString())
        put("EmuCore/GS", "UserHacks_TCOffsetX", "int", textureOffsetX.coerceIn(0, 10000).toString())
        put("EmuCore/GS", "UserHacks_TCOffsetY", "int", textureOffsetY.coerceIn(0, 10000).toString())
        put("EmuCore/GS", "paltex", "bool", gpuPaletteConversion.toString())
        put("EmuCore/GS", "UserHacks_CPU_FB_Conversion", "bool", cpuFramebufferConversion.toString())
        put("EmuCore/GS", "UserHacks_ReadTCOnClose", "bool", readTargetsWhenClosing.toString())
        put("EmuCore/GS", "UserHacks_DisableDepthSupport", "bool", disableDepthEmulation.toString())
        put("EmuCore/GS", "UserHacks_DisablePartialInvalidation", "bool", disablePartialInvalidation.toString())
        put("EmuCore/GS", "UserHacks_Disable_Safe_Features", "bool", disableSafeFeatures.toString())
        put("EmuCore/GS", "UserHacks_DisableRenderFixes", "bool", disableRenderFixes.toString())
        put("EmuCore/GS", "preload_frame_with_gs_data", "bool", preloadFrameData.toString())
        put("EmuCore/GS", "UserHacks_EstimateTextureRegion", "bool", estimateTextureRegion.toString())
        put("EmuCore/GS", "TriFilter", "int", triFilter.toString())
        put("EmuCore/GS", "MaxAnisotropy", "int", maxAnisotropy.toString())
        val gpuProfileStr = when (gpuProfile) {
            1 -> "mali"
            2 -> "adreno"
            3 -> "powervr"
            4 -> "xclipse"
            else -> "auto"
        }
        put("EmuCore/GS", "AndroidGpuProfileOverride", "string", gpuProfileStr)
    }

    private fun anyUserHackEnabled(): Boolean =
        manualUserHacks ||
            autoFlush != 0 || halfPixelOffset != 0 || limit24BitDepth != 0 ||
            textureInsideRt != 0 || nativeScaling != 0 || roundSprite != 0 ||
            bilinearUpscale != 0 || gpuTargetClut != 0 || cpuSpriteRenderBw != 0 ||
            cpuSpriteRenderLevel != 0 || cpuClutRender != 0 ||
            textureOffsetX != 0 || textureOffsetY != 0 ||
            alignSprite || mergeSprite || forceEvenSpritePosition || unscaledPaletteDraw ||
            gpuPaletteConversion || cpuFramebufferConversion || readTargetsWhenClosing ||
            disableDepthEmulation || disablePartialInvalidation || disableSafeFeatures ||
            disableRenderFixes || preloadFrameData || estimateTextureRegion || drawBuffering ||
            skipDrawStart != 0 || skipDrawEnd != 0

    fun applyGsLive(): Boolean {
        writeGsToNative()
        return NativeApp.applyGSSettingsLive()
    }

    fun gsDiffersFrom(other: Settings): Boolean =
        deinterlaceMode != other.deinterlaceMode ||
            textureFiltering != other.textureFiltering ||
            displayBilinear != other.displayBilinear ||
            texturePreloading != other.texturePreloading ||
            hardwareDownloadMode != other.hardwareDownloadMode ||
            tvShader != other.tvShader ||
            shadeBoost != other.shadeBoost ||
            shadeBoostBrightness != other.shadeBoostBrightness ||
            shadeBoostContrast != other.shadeBoostContrast ||
            shadeBoostSaturation != other.shadeBoostSaturation ||
            shadeBoostGamma != other.shadeBoostGamma ||
            fxaa != other.fxaa ||
            casMode != other.casMode ||
            casSharpness != other.casSharpness ||
            accurateBlendingUnit != other.accurateBlendingUnit ||
            hwMipmap != other.hwMipmap ||
            triFilter != other.triFilter ||
            maxAnisotropy != other.maxAnisotropy ||
            manualUserHacks != other.manualUserHacks ||
            autoFlush != other.autoFlush ||
            halfPixelOffset != other.halfPixelOffset ||
            limit24BitDepth != other.limit24BitDepth ||
            textureInsideRt != other.textureInsideRt ||
            nativeScaling != other.nativeScaling ||
            roundSprite != other.roundSprite ||
            bilinearUpscale != other.bilinearUpscale ||
            gpuTargetClut != other.gpuTargetClut ||
            cpuSpriteRenderBw != other.cpuSpriteRenderBw ||
            cpuSpriteRenderLevel != other.cpuSpriteRenderLevel ||
            cpuClutRender != other.cpuClutRender ||
            alignSprite != other.alignSprite ||
            mergeSprite != other.mergeSprite ||
            forceEvenSpritePosition != other.forceEvenSpritePosition ||
            unscaledPaletteDraw != other.unscaledPaletteDraw ||
            textureOffsetX != other.textureOffsetX ||
            textureOffsetY != other.textureOffsetY ||
            gpuPaletteConversion != other.gpuPaletteConversion ||
            cpuFramebufferConversion != other.cpuFramebufferConversion ||
            readTargetsWhenClosing != other.readTargetsWhenClosing ||
            disableDepthEmulation != other.disableDepthEmulation ||
            disablePartialInvalidation != other.disablePartialInvalidation ||
            disableSafeFeatures != other.disableSafeFeatures ||
            disableRenderFixes != other.disableRenderFixes ||
            preloadFrameData != other.preloadFrameData ||
            estimateTextureRegion != other.estimateTextureRegion ||
            hwAccurateAlphaTest != other.hwAccurateAlphaTest ||
            drawBuffering != other.drawBuffering ||
            loadTextureReplacements != other.loadTextureReplacements ||
            loadTextureReplacementsAsync != other.loadTextureReplacementsAsync ||
            precacheTextureReplacements != other.precacheTextureReplacements ||
            dumpReplaceableTextures != other.dumpReplaceableTextures ||
            osdShowTextureReplacements != other.osdShowTextureReplacements

    fun toJson(): JSONObject = JSONObject().apply {
        put("eeCycleRate", eeCycleRate)
        put("eeCycleSkip", eeCycleSkip)
        put("eeClampMode", eeClampMode)
        put("vuClampMode", vuClampMode)
        put("mtvu", mtvu)
        put("vu1Instant", vu1Instant)
        put("vuFlagHack", vuFlagHack)
        put("fastCDVD", fastCDVD)
        put("intcStat", intcStat)
        put("waitLoop", waitLoop)
        put("vuNeonFusions", vuNeonFusions)
        put("vuDeferredWrites", vuDeferredWrites)
        put("vuSkipStallSim", vuSkipStallSim)
        put("frameLimitEnable", frameLimitEnable)
        put("nominalSpeedPercent", nominalSpeedPercent)
        put("fpsLimit", fpsLimit)
        put("frameSkip", frameSkip)
        put("audioVolume", audioVolume)
        put("audioMuted", audioMuted)
        put("audioSwapChannels", audioSwapChannels)
        put("audioTimeStretch", audioTimeStretch)
        put("audioBufferMs", audioBufferMs)
        put("audioOutputLatencyMs", audioOutputLatencyMs)
        put("audioFastForwardVolume", audioFastForwardVolume)
        put("spu2NeonReverb", spu2NeonReverb)
        put("renderer", renderer)
        put("upscaleFloat", upscaleFloat.toDouble())
        put("framerateNtsc", framerateNtsc.toDouble())
        put("frameratePal", frameratePal.toDouble())
        put("enablePatches", enablePatches)
        put("enableCheats", enableCheats)
        put("enableWideScreenPatches", enableWideScreenPatches)
        put("enableNoInterlacingPatches", enableNoInterlacingPatches)
        put("enableFastBoot", enableFastBoot)
        put("hostFs", hostFs)
        put("enableGameFixes", enableGameFixes)
        put("gamefixSoftwareRendererFmv", gamefixSoftwareRendererFmv)
        put("gamefixSkipMpeg", gamefixSkipMpeg)
        put("gamefixEETiming", gamefixEETiming)
        put("gamefixInstantDma", gamefixInstantDma)
        put("gamefixBlitInternalFps", gamefixBlitInternalFps)
        put("gamefixFpuMul", gamefixFpuMul)
        put("gamefixOphFlag", gamefixOphFlag)
        put("gamefixGifFifo", gamefixGifFifo)
        put("gamefixDmaBusy", gamefixDmaBusy)
        put("gamefixVif1Stall", gamefixVif1Stall)
        put("gamefixIbit", gamefixIbit)
        put("gamefixFullVu0Sync", gamefixFullVu0Sync)
        put("gamefixVuAddSub", gamefixVuAddSub)
        put("gamefixVuOverflow", gamefixVuOverflow)
        put("gamefixXgkick", gamefixXgkick)
        put("gamefixGoemonTlb", gamefixGoemonTlb)
        put("gamefixVuSync", gamefixVuSync)
        put("skipDuplicateFrames", skipDuplicateFrames)
        put("eeFpuRoundMode", eeFpuRoundMode)
        put("vu0RoundMode", vu0RoundMode)
        put("vu1RoundMode", vu1RoundMode)
        put("screenOffsets", screenOffsets)
        put("showOverscan", showOverscan)
        put("antiBlur", antiBlur)
        put("disableInterlaceOffset", disableInterlaceOffset)
        put("syncToHostRefresh", syncToHostRefresh)
        put("disableFramebufferFetch", disableFramebufferFetch)
        put("hwRov", hwRov)
        put("hwAa1", hwAa1)
        put("adrenoFbFetch", adrenoFbFetch)
        put("forceMaliFbFetch", forceMaliFbFetch)
        put("useAngleOpenGL", useAngleOpenGL)
        put("overrideTextureBarriers", overrideTextureBarriers)
        put("disableVertexShaderExpand", disableVertexShaderExpand)
        put("useBlitSwapChain", useBlitSwapChain)
        put("disableShaderCache", disableShaderCache)
        put("hwAccurateAlphaTest", hwAccurateAlphaTest)
        put("skipDrawStart", skipDrawStart)
        put("skipDrawEnd", skipDrawEnd)
        put("spinGpuReadbacks", spinGpuReadbacks)
        put("spinCpuReadbacks", spinCpuReadbacks)
        put("integerScaling", integerScaling)
        put("dithering", dithering)
        put("vsyncQueueSize", vsyncQueueSize)
        put("autoFlushSw", autoFlushSw)
        put("mipmapSw", mipmapSw)
        put("swThreads", swThreads)
        put("swThreadsHeight", swThreadsHeight)
        put("aspectRatio", aspectRatio)
        put("fmvAspectRatio", fmvAspectRatio)
        put("deinterlaceMode", deinterlaceMode)
        put("dev9EthEnable", dev9EthEnable)
        put("dev9EthApi", dev9EthApi)
        put("dev9EthDevice", dev9EthDevice)
        put("dev9EthLogDhcp", dev9EthLogDhcp)
        put("dev9EthLogDns", dev9EthLogDns)
        put("dev9InterceptDhcp", dev9InterceptDhcp)
        put("dev9Ps2Ip", dev9Ps2Ip)
        put("dev9Mask", dev9Mask)
        put("dev9Gateway", dev9Gateway)
        put("dev9Dns1", dev9Dns1)
        put("dev9Dns2", dev9Dns2)
        put("dev9AutoMask", dev9AutoMask)
        put("dev9AutoGateway", dev9AutoGateway)
        put("dev9ModeDns1", dev9ModeDns1)
        put("dev9ModeDns2", dev9ModeDns2)
        put("dev9EthHosts", JSONArray().apply {
            dev9EthHosts.forEach { h ->
                put(JSONObject().apply {
                    put("url", h.url)
                    put("ip", h.ip)
                    put("enabled", h.enabled)
                })
            }
        })
        put("dev9HddEnable", dev9HddEnable)
        put("dev9HddFile", dev9HddFile)
        put("memoryCardSlot1Enabled", memoryCardSlot1Enabled)
        put("memoryCardSlot1Filename", memoryCardSlot1Filename)
        put("biosFilename", biosFilename)
        put("memoryCardSlot2Enabled", memoryCardSlot2Enabled)
        put("memoryCardSlot2Filename", memoryCardSlot2Filename)
        put("usbKeyboard", usbKeyboard)
        put("recEE", recEE)
        put("recIOP", recIOP)
        put("recVU0", recVU0)
        put("recVU1", recVU1)
        put("enableFastmem", enableFastmem)
        put("vu1InlineFmacStall", vu1InlineFmacStall)
        put("vu1CrossBlockPState", vu1CrossBlockPState)
        put("vu1InlineDrainTestPipes", vu1InlineDrainTestPipes)
        put("vu1FmacInstanceRouting", vu1FmacInstanceRouting)
        put("hwMipmap", hwMipmap)
        put("accurateBlendingUnit", accurateBlendingUnit)
        put("textureFiltering", textureFiltering)
        put("displayBilinear", displayBilinear)
        put("texturePreloading", texturePreloading)
        put("hardwareDownloadMode", hardwareDownloadMode)
        put("tvShader", tvShader)
        put("shadeBoost", shadeBoost)
        put("shadeBoostBrightness", shadeBoostBrightness)
        put("shadeBoostContrast", shadeBoostContrast)
        put("shadeBoostSaturation", shadeBoostSaturation)
        put("shadeBoostGamma", shadeBoostGamma)
        put("fxaa", fxaa)
        put("shaderChainEnabled", shaderChainEnabled)
        put("shaderChainPreset", shaderChainPreset)
        put("shaderChainParams", shaderChainParamsToJson(shaderChainParams))
        put("casMode", casMode)
        put("casSharpness", casSharpness)
        put("loadTextureReplacements", loadTextureReplacements)
        put("loadTextureReplacementsAsync", loadTextureReplacementsAsync)
        put("precacheTextureReplacements", precacheTextureReplacements)
        put("dumpReplaceableTextures", dumpReplaceableTextures)
        put("osdShowTextureReplacements", osdShowTextureReplacements)
        put("osdShowFps", osdShowFps)
        put("osdScale", osdScale)
        put("vsyncEnable", vsyncEnable)
        put("osdShowVps", osdShowVps)
        put("osdShowSpeed", osdShowSpeed)
        put("osdShowCpu", osdShowCpu)
        put("osdShowGpu", osdShowGpu)
        put("osdShowResolution", osdShowResolution)
        put("osdShowGsStats", osdShowGsStats)
        put("osdShowFrameTimes", osdShowFrameTimes)
        put("osdShowHardwareInfo", osdShowHardwareInfo)
        put("osdShowMessages", osdShowMessages)
        put("osdShowGpuStats", osdShowGpuStats)
        put("osdShowVersion", osdShowVersion)
        put("osdShowSettings", osdShowSettings)
        put("osdShowInputs", osdShowInputs)
        put("autoFlush", autoFlush)
        put("halfPixelOffset", halfPixelOffset)
        put("limit24BitDepth", limit24BitDepth)
        put("manualUserHacks", manualUserHacks)
        put("textureInsideRt", textureInsideRt)
        put("nativeScaling", nativeScaling)
        put("roundSprite", roundSprite)
        put("bilinearUpscale", bilinearUpscale)
        put("gpuTargetClut", gpuTargetClut)
        put("cpuSpriteRenderBw", cpuSpriteRenderBw)
        put("cpuSpriteRenderLevel", cpuSpriteRenderLevel)
        put("alignSprite", alignSprite)
        put("mergeSprite", mergeSprite)
        put("forceEvenSpritePosition", forceEvenSpritePosition)
        put("unscaledPaletteDraw", unscaledPaletteDraw)
        put("textureOffsetX", textureOffsetX)
        put("textureOffsetY", textureOffsetY)
        put("gpuPaletteConversion", gpuPaletteConversion)
        put("cpuFramebufferConversion", cpuFramebufferConversion)
        put("readTargetsWhenClosing", readTargetsWhenClosing)
        put("disableDepthEmulation", disableDepthEmulation)
        put("disablePartialInvalidation", disablePartialInvalidation)
        put("disableSafeFeatures", disableSafeFeatures)
        put("disableRenderFixes", disableRenderFixes)
        put("preloadFrameData", preloadFrameData)
        put("estimateTextureRegion", estimateTextureRegion)
        put("drawBuffering", drawBuffering)
        put("cpuClutRender", cpuClutRender)
        put("triFilter", triFilter)
        put("maxAnisotropy", maxAnisotropy)
        put("gpuProfile", gpuProfile)
    }

    companion object {
        @JvmStatic
        internal var emitSink: ((String, String, String, String) -> Unit)? = null

        fun lowEndPreset(base: Settings, mtvu: Boolean): Settings = base.copy(
            accurateBlendingUnit = 0,
            upscaleFloat = 1.0f,
            hwMipmap = false,
            gpuPaletteConversion = false,
            texturePreloading = 1,
            hwRov = false,
            eeCycleSkip = 1,
            mtvu = mtvu,
        )

        fun fromJson(json: JSONObject): Settings {
            val def = Settings()
            return Settings(
                eeCycleRate = json.optInt("eeCycleRate", def.eeCycleRate),
                eeCycleSkip = json.optInt("eeCycleSkip", def.eeCycleSkip),
                eeClampMode = json.optInt("eeClampMode", def.eeClampMode),
                vuClampMode = json.optInt("vuClampMode", def.vuClampMode),
                mtvu = json.optBoolean("mtvu", def.mtvu),
                vu1Instant = json.optBoolean("vu1Instant", def.vu1Instant),
                vuFlagHack = json.optBoolean("vuFlagHack", def.vuFlagHack),
                fastCDVD = json.optBoolean("fastCDVD", def.fastCDVD),
                intcStat = json.optBoolean("intcStat", def.intcStat),
                waitLoop = json.optBoolean("waitLoop", def.waitLoop),
                vuNeonFusions = json.optBoolean("vuNeonFusions", def.vuNeonFusions),
                vuDeferredWrites = json.optBoolean("vuDeferredWrites", def.vuDeferredWrites),
                vuSkipStallSim = json.optBoolean("vuSkipStallSim", def.vuSkipStallSim),
                frameLimitEnable = json.optBoolean("frameLimitEnable", def.frameLimitEnable),
                nominalSpeedPercent = json.optInt("nominalSpeedPercent", def.nominalSpeedPercent),
                fpsLimit = json.optInt("fpsLimit", def.fpsLimit),
                frameSkip = json.optInt("frameSkip", def.frameSkip),
                audioVolume = json.optInt("audioVolume", def.audioVolume),
                audioMuted = json.optBoolean("audioMuted", def.audioMuted),
                audioSwapChannels = json.optBoolean("audioSwapChannels", def.audioSwapChannels),
                audioTimeStretch = json.optBoolean("audioTimeStretch", def.audioTimeStretch),
                audioBufferMs = json.optInt("audioBufferMs", def.audioBufferMs),
                audioOutputLatencyMs = json.optInt("audioOutputLatencyMs", def.audioOutputLatencyMs),
                audioFastForwardVolume = json.optInt("audioFastForwardVolume", def.audioFastForwardVolume),
                spu2NeonReverb = json.optBoolean("spu2NeonReverb", def.spu2NeonReverb),
                renderer = json.optString("renderer", def.renderer),
                upscaleFloat = json.optDouble("upscaleFloat", def.upscaleFloat.toDouble()).toFloat(),
                framerateNtsc = json.optDouble("framerateNtsc", def.framerateNtsc.toDouble()).toFloat(),
                frameratePal = json.optDouble("frameratePal", def.frameratePal.toDouble()).toFloat(),
                enablePatches = json.optBoolean("enablePatches", def.enablePatches),
                enableCheats = json.optBoolean("enableCheats", def.enableCheats),
                enableWideScreenPatches = json.optBoolean("enableWideScreenPatches", def.enableWideScreenPatches),
                enableNoInterlacingPatches = json.optBoolean("enableNoInterlacingPatches", def.enableNoInterlacingPatches),
                enableFastBoot = json.optBoolean("enableFastBoot", def.enableFastBoot),
                hostFs = json.optBoolean("hostFs", def.hostFs),
                enableGameFixes = json.optBoolean("enableGameFixes", def.enableGameFixes),
                gamefixSoftwareRendererFmv = json.optBoolean("gamefixSoftwareRendererFmv", def.gamefixSoftwareRendererFmv),
                gamefixSkipMpeg = json.optBoolean("gamefixSkipMpeg", def.gamefixSkipMpeg),
                gamefixEETiming = json.optBoolean("gamefixEETiming", def.gamefixEETiming),
                gamefixInstantDma = json.optBoolean("gamefixInstantDma", def.gamefixInstantDma),
                gamefixBlitInternalFps = json.optBoolean("gamefixBlitInternalFps", def.gamefixBlitInternalFps),
                gamefixFpuMul = json.optBoolean("gamefixFpuMul", def.gamefixFpuMul),
                gamefixOphFlag = json.optBoolean("gamefixOphFlag", def.gamefixOphFlag),
                gamefixGifFifo = json.optBoolean("gamefixGifFifo", def.gamefixGifFifo),
                gamefixDmaBusy = json.optBoolean("gamefixDmaBusy", def.gamefixDmaBusy),
                gamefixVif1Stall = json.optBoolean("gamefixVif1Stall", def.gamefixVif1Stall),
                gamefixIbit = json.optBoolean("gamefixIbit", def.gamefixIbit),
                gamefixFullVu0Sync = json.optBoolean("gamefixFullVu0Sync", def.gamefixFullVu0Sync),
                gamefixVuAddSub = json.optBoolean("gamefixVuAddSub", def.gamefixVuAddSub),
                gamefixVuOverflow = json.optBoolean("gamefixVuOverflow", def.gamefixVuOverflow),
                gamefixXgkick = json.optBoolean("gamefixXgkick", def.gamefixXgkick),
                gamefixGoemonTlb = json.optBoolean("gamefixGoemonTlb", def.gamefixGoemonTlb),
                gamefixVuSync = json.optBoolean("gamefixVuSync", def.gamefixVuSync),
                skipDuplicateFrames = json.optBoolean("skipDuplicateFrames", def.skipDuplicateFrames),
                eeFpuRoundMode = json.optInt("eeFpuRoundMode", def.eeFpuRoundMode),
                vu0RoundMode = json.optInt("vu0RoundMode", def.vu0RoundMode),
                vu1RoundMode = json.optInt("vu1RoundMode", def.vu1RoundMode),
                screenOffsets = json.optBoolean("screenOffsets", def.screenOffsets),
                showOverscan = json.optBoolean("showOverscan", def.showOverscan),
                antiBlur = json.optBoolean("antiBlur", def.antiBlur),
                disableInterlaceOffset = json.optBoolean("disableInterlaceOffset", def.disableInterlaceOffset),
                syncToHostRefresh = json.optBoolean("syncToHostRefresh", def.syncToHostRefresh),
                disableFramebufferFetch = json.optBoolean("disableFramebufferFetch", def.disableFramebufferFetch),
                hwRov = json.optBoolean("hwRov", def.hwRov),
                hwAa1 = json.optBoolean("hwAa1", def.hwAa1),
                hwAat = false,
                adrenoFbFetch = json.optBoolean("adrenoFbFetch", def.adrenoFbFetch),
                forceMaliFbFetch = json.optBoolean("forceMaliFbFetch", def.forceMaliFbFetch),
                useAngleOpenGL = json.optBoolean("useAngleOpenGL", def.useAngleOpenGL),
                overrideTextureBarriers = json.optInt("overrideTextureBarriers", def.overrideTextureBarriers),
                disableVertexShaderExpand = json.optBoolean("disableVertexShaderExpand", def.disableVertexShaderExpand),
                useBlitSwapChain = json.optBoolean("useBlitSwapChain", def.useBlitSwapChain),
                disableShaderCache = json.optBoolean("disableShaderCache", def.disableShaderCache),
                hwAccurateAlphaTest = json.optBoolean(
                    "hwAccurateAlphaTest",
                    json.optBoolean("hwAat", def.hwAccurateAlphaTest),
                ),
                skipDrawStart = json.optInt("skipDrawStart", def.skipDrawStart),
                skipDrawEnd = json.optInt("skipDrawEnd", def.skipDrawEnd),
                spinGpuReadbacks = json.optBoolean("spinGpuReadbacks", def.spinGpuReadbacks),
                spinCpuReadbacks = json.optBoolean("spinCpuReadbacks", def.spinCpuReadbacks),
                integerScaling = json.optBoolean("integerScaling", def.integerScaling),
                dithering = json.optInt("dithering", def.dithering),
                vsyncQueueSize = json.optInt("vsyncQueueSize", def.vsyncQueueSize),
                autoFlushSw = json.optBoolean("autoFlushSw", def.autoFlushSw),
                mipmapSw = json.optBoolean("mipmapSw", def.mipmapSw),
                swThreads = json.optInt("swThreads", def.swThreads),
                swThreadsHeight = json.optInt("swThreadsHeight", def.swThreadsHeight),
                aspectRatio = json.optInt("aspectRatio", def.aspectRatio),
                fmvAspectRatio = json.optInt("fmvAspectRatio", def.fmvAspectRatio),
                deinterlaceMode = json.optInt("deinterlaceMode", def.deinterlaceMode),
                dev9EthEnable = json.optBoolean("dev9EthEnable", def.dev9EthEnable),
                dev9EthApi = json.optString("dev9EthApi", def.dev9EthApi).ifEmpty { def.dev9EthApi },
                dev9EthDevice = json.optString("dev9EthDevice", def.dev9EthDevice).ifEmpty { def.dev9EthDevice },
                dev9EthLogDhcp = json.optBoolean("dev9EthLogDhcp", def.dev9EthLogDhcp),
                dev9EthLogDns = json.optBoolean("dev9EthLogDns", def.dev9EthLogDns),
                dev9InterceptDhcp = json.optBoolean("dev9InterceptDhcp", def.dev9InterceptDhcp),
                dev9Ps2Ip = json.optString("dev9Ps2Ip", def.dev9Ps2Ip).ifEmpty { def.dev9Ps2Ip },
                dev9Mask = json.optString("dev9Mask", def.dev9Mask).ifEmpty { def.dev9Mask },
                dev9Gateway = json.optString("dev9Gateway", def.dev9Gateway).ifEmpty { def.dev9Gateway },
                dev9Dns1 = json.optString("dev9Dns1", def.dev9Dns1).ifEmpty { def.dev9Dns1 },
                dev9Dns2 = json.optString("dev9Dns2", def.dev9Dns2).ifEmpty { def.dev9Dns2 },
                dev9AutoMask = json.optBoolean("dev9AutoMask", def.dev9AutoMask),
                dev9AutoGateway = json.optBoolean("dev9AutoGateway", def.dev9AutoGateway),
                dev9ModeDns1 = json.optString("dev9ModeDns1", def.dev9ModeDns1).ifEmpty { def.dev9ModeDns1 },
                dev9ModeDns2 = json.optString("dev9ModeDns2", def.dev9ModeDns2).ifEmpty { def.dev9ModeDns2 },
                dev9EthHosts = json.optJSONArray("dev9EthHosts")?.let { arr ->
                    (0 until arr.length()).mapNotNull { idx ->
                        arr.optJSONObject(idx)?.let { o ->
                            Dev9HostMapping(
                                url = o.optString("url", ""),
                                ip = o.optString("ip", "0.0.0.0").ifEmpty { "0.0.0.0" },
                                enabled = o.optBoolean("enabled", true),
                            )
                        }
                    }.filter { it.url.isNotBlank() }
                } ?: def.dev9EthHosts,
                dev9HddEnable = json.optBoolean("dev9HddEnable", def.dev9HddEnable),
                dev9HddFile = json.optString("dev9HddFile", def.dev9HddFile).ifEmpty { def.dev9HddFile },
                memoryCardSlot1Enabled = json.optBoolean("memoryCardSlot1Enabled", def.memoryCardSlot1Enabled),
                memoryCardSlot1Filename = json.optString("memoryCardSlot1Filename", def.memoryCardSlot1Filename).ifEmpty { def.memoryCardSlot1Filename },
                biosFilename = json.optString("biosFilename", def.biosFilename),
                memoryCardSlot2Enabled = json.optBoolean("memoryCardSlot2Enabled", def.memoryCardSlot2Enabled),
                memoryCardSlot2Filename = json.optString("memoryCardSlot2Filename", def.memoryCardSlot2Filename).ifEmpty { def.memoryCardSlot2Filename },
                usbKeyboard = json.optBoolean("usbKeyboard", def.usbKeyboard),
                recEE = json.optBoolean("recEE", def.recEE),
                recIOP = json.optBoolean("recIOP", def.recIOP),
                recVU0 = json.optBoolean("recVU0", def.recVU0),
                recVU1 = json.optBoolean("recVU1", def.recVU1),
                enableFastmem = json.optBoolean("enableFastmem", def.enableFastmem),
                useMacEE = true,
                useMacIOP = true,
                useMacVU0 = true,
                useMacVU1 = true,
                vu1InlineFmacStall = json.optBoolean("vu1InlineFmacStall", def.vu1InlineFmacStall),
                vu1CrossBlockPState = json.optBoolean("vu1CrossBlockPState", def.vu1CrossBlockPState),
                vu1InlineDrainTestPipes = json.optBoolean("vu1InlineDrainTestPipes", def.vu1InlineDrainTestPipes),
                vu1FmacInstanceRouting = json.optBoolean("vu1FmacInstanceRouting", def.vu1FmacInstanceRouting),
                hwMipmap = json.optBoolean("hwMipmap", def.hwMipmap),
                accurateBlendingUnit = json.optInt("accurateBlendingUnit", def.accurateBlendingUnit),
                textureFiltering = json.optInt("textureFiltering", def.textureFiltering),
                displayBilinear = json.optInt("displayBilinear", def.displayBilinear),
                texturePreloading = json.optInt("texturePreloading", def.texturePreloading),
                hardwareDownloadMode = json.optInt("hardwareDownloadMode", def.hardwareDownloadMode),
                tvShader = json.optInt("tvShader", def.tvShader),
                shadeBoost = json.optBoolean("shadeBoost", def.shadeBoost),
                shadeBoostBrightness = json.optInt("shadeBoostBrightness", def.shadeBoostBrightness),
                shadeBoostContrast = json.optInt("shadeBoostContrast", def.shadeBoostContrast),
                shadeBoostSaturation = json.optInt("shadeBoostSaturation", def.shadeBoostSaturation),
                shadeBoostGamma = json.optInt("shadeBoostGamma", def.shadeBoostGamma),
                fxaa = json.optBoolean("fxaa", def.fxaa),
                shaderChainEnabled = json.optBoolean("shaderChainEnabled", def.shaderChainEnabled),
                shaderChainPreset = json.optString("shaderChainPreset", def.shaderChainPreset),
                shaderChainParams = json.optJSONObject("shaderChainParams")
                    ?.let { shaderChainParamsFromJson(it) } ?: def.shaderChainParams,
                casMode = json.optInt("casMode", def.casMode),
                casSharpness = json.optInt("casSharpness", def.casSharpness),
                loadTextureReplacements = json.optBoolean("loadTextureReplacements", def.loadTextureReplacements),
                loadTextureReplacementsAsync = json.optBoolean("loadTextureReplacementsAsync", def.loadTextureReplacementsAsync),
                precacheTextureReplacements = json.optBoolean("precacheTextureReplacements", def.precacheTextureReplacements),
                dumpReplaceableTextures = json.optBoolean("dumpReplaceableTextures", def.dumpReplaceableTextures),
                osdShowTextureReplacements = json.optBoolean("osdShowTextureReplacements", def.osdShowTextureReplacements),
                osdShowFps = json.optBoolean("osdShowFps", def.osdShowFps),
                osdScale = json.optInt("osdScale", def.osdScale),
                vsyncEnable = json.optBoolean("vsyncEnable", def.vsyncEnable),
                osdShowVps = json.optBoolean("osdShowVps", def.osdShowVps),
                osdShowSpeed = json.optBoolean("osdShowSpeed", def.osdShowSpeed),
                osdShowCpu = json.optBoolean("osdShowCpu", def.osdShowCpu),
                osdShowGpu = json.optBoolean("osdShowGpu", def.osdShowGpu),
                osdShowResolution = json.optBoolean("osdShowResolution", def.osdShowResolution),
                osdShowGsStats = json.optBoolean("osdShowGsStats", def.osdShowGsStats),
                osdShowFrameTimes = json.optBoolean("osdShowFrameTimes", def.osdShowFrameTimes),
                osdShowHardwareInfo = json.optBoolean("osdShowHardwareInfo", def.osdShowHardwareInfo),
                osdShowMessages = json.optBoolean("osdShowMessages", def.osdShowMessages),
                osdShowGpuStats = json.optBoolean("osdShowGpuStats", def.osdShowGpuStats),
                osdShowVersion = json.optBoolean("osdShowVersion", def.osdShowVersion),
                osdShowSettings = json.optBoolean("osdShowSettings", def.osdShowSettings),
                osdShowInputs = json.optBoolean("osdShowInputs", def.osdShowInputs),
                autoFlush = json.optInt("autoFlush", def.autoFlush),
                halfPixelOffset = json.optInt("halfPixelOffset", def.halfPixelOffset),
                limit24BitDepth = json.optInt("limit24BitDepth", def.limit24BitDepth),
                manualUserHacks = json.optBoolean("manualUserHacks", def.manualUserHacks),
                textureInsideRt = json.optInt("textureInsideRt", def.textureInsideRt),
                nativeScaling = json.optInt("nativeScaling", def.nativeScaling),
                roundSprite = json.optInt("roundSprite", def.roundSprite),
                bilinearUpscale = json.optInt("bilinearUpscale", def.bilinearUpscale),
                gpuTargetClut = json.optInt("gpuTargetClut", def.gpuTargetClut),
                cpuSpriteRenderBw = json.optInt("cpuSpriteRenderBw", def.cpuSpriteRenderBw),
                cpuSpriteRenderLevel = json.optInt("cpuSpriteRenderLevel", def.cpuSpriteRenderLevel),
                alignSprite = json.optBoolean("alignSprite", def.alignSprite),
                mergeSprite = json.optBoolean("mergeSprite", def.mergeSprite),
                forceEvenSpritePosition = json.optBoolean("forceEvenSpritePosition", def.forceEvenSpritePosition),
                unscaledPaletteDraw = json.optBoolean("unscaledPaletteDraw", def.unscaledPaletteDraw),
                textureOffsetX = json.optInt("textureOffsetX", def.textureOffsetX),
                textureOffsetY = json.optInt("textureOffsetY", def.textureOffsetY),
                gpuPaletteConversion = json.optBoolean("gpuPaletteConversion", def.gpuPaletteConversion),
                cpuFramebufferConversion = json.optBoolean("cpuFramebufferConversion", def.cpuFramebufferConversion),
                readTargetsWhenClosing = json.optBoolean("readTargetsWhenClosing", def.readTargetsWhenClosing),
                disableDepthEmulation = json.optBoolean("disableDepthEmulation", def.disableDepthEmulation),
                disablePartialInvalidation = json.optBoolean("disablePartialInvalidation", def.disablePartialInvalidation),
                disableSafeFeatures = json.optBoolean("disableSafeFeatures", def.disableSafeFeatures),
                disableRenderFixes = json.optBoolean("disableRenderFixes", def.disableRenderFixes),
                preloadFrameData = json.optBoolean("preloadFrameData", def.preloadFrameData),
                estimateTextureRegion = json.optBoolean("estimateTextureRegion", def.estimateTextureRegion),
                drawBuffering = json.optBoolean("drawBuffering", def.drawBuffering),
                cpuClutRender = json.optInt("cpuClutRender", def.cpuClutRender),
                triFilter = json.optInt("triFilter", def.triFilter),
                maxAnisotropy = json.optInt("maxAnisotropy", def.maxAnisotropy),
                gpuProfile = json.optInt("gpuProfile", def.gpuProfile),
            )
        }

        fun diff(base: Settings, current: Settings): JSONObject {
            val j = JSONObject()
            if (current.eeCycleRate         != base.eeCycleRate)         j.put("eeCycleRate", current.eeCycleRate)
            if (current.eeCycleSkip         != base.eeCycleSkip)         j.put("eeCycleSkip", current.eeCycleSkip)
            if (current.eeClampMode         != base.eeClampMode)         j.put("eeClampMode", current.eeClampMode)
            if (current.vuClampMode         != base.vuClampMode)         j.put("vuClampMode", current.vuClampMode)
            if (current.mtvu                != base.mtvu)                j.put("mtvu", current.mtvu)
            if (current.vu1Instant          != base.vu1Instant)          j.put("vu1Instant", current.vu1Instant)
            if (current.vuFlagHack          != base.vuFlagHack)          j.put("vuFlagHack", current.vuFlagHack)
            if (current.fastCDVD            != base.fastCDVD)            j.put("fastCDVD", current.fastCDVD)
            if (current.intcStat            != base.intcStat)            j.put("intcStat", current.intcStat)
            if (current.waitLoop            != base.waitLoop)            j.put("waitLoop", current.waitLoop)
            if (current.vuNeonFusions       != base.vuNeonFusions)       j.put("vuNeonFusions", current.vuNeonFusions)
            if (current.vuDeferredWrites    != base.vuDeferredWrites)    j.put("vuDeferredWrites", current.vuDeferredWrites)
            if (current.vuSkipStallSim      != base.vuSkipStallSim)      j.put("vuSkipStallSim", current.vuSkipStallSim)
            if (current.frameLimitEnable    != base.frameLimitEnable)    j.put("frameLimitEnable", current.frameLimitEnable)
            if (current.nominalSpeedPercent != base.nominalSpeedPercent) j.put("nominalSpeedPercent", current.nominalSpeedPercent)
            if (current.fpsLimit            != base.fpsLimit)            j.put("fpsLimit", current.fpsLimit)
            if (current.frameSkip != base.frameSkip) j.put("frameSkip", current.frameSkip)
            if (current.audioVolume != base.audioVolume) j.put("audioVolume", current.audioVolume)
            if (current.audioMuted != base.audioMuted) j.put("audioMuted", current.audioMuted)
            if (current.audioSwapChannels != base.audioSwapChannels) j.put("audioSwapChannels", current.audioSwapChannels)
            if (current.audioTimeStretch != base.audioTimeStretch) j.put("audioTimeStretch", current.audioTimeStretch)
            if (current.audioBufferMs != base.audioBufferMs) j.put("audioBufferMs", current.audioBufferMs)
            if (current.audioOutputLatencyMs != base.audioOutputLatencyMs) j.put("audioOutputLatencyMs", current.audioOutputLatencyMs)
            if (current.audioFastForwardVolume != base.audioFastForwardVolume) j.put("audioFastForwardVolume", current.audioFastForwardVolume)
            if (current.spu2NeonReverb != base.spu2NeonReverb) j.put("spu2NeonReverb", current.spu2NeonReverb)
            if (current.renderer != base.renderer) j.put("renderer", current.renderer)
            if (current.upscaleFloat != base.upscaleFloat) j.put("upscaleFloat", current.upscaleFloat.toDouble())
            if (current.framerateNtsc != base.framerateNtsc) j.put("framerateNtsc", current.framerateNtsc.toDouble())
            if (current.frameratePal != base.frameratePal) j.put("frameratePal", current.frameratePal.toDouble())
            if (current.enablePatches != base.enablePatches) j.put("enablePatches", current.enablePatches)
            if (current.enableCheats != base.enableCheats) j.put("enableCheats", current.enableCheats)
            if (current.enableWideScreenPatches != base.enableWideScreenPatches) j.put("enableWideScreenPatches", current.enableWideScreenPatches)
            if (current.enableNoInterlacingPatches != base.enableNoInterlacingPatches) j.put("enableNoInterlacingPatches", current.enableNoInterlacingPatches)
            if (current.enableFastBoot != base.enableFastBoot) j.put("enableFastBoot", current.enableFastBoot)
            if (current.hostFs != base.hostFs) j.put("hostFs", current.hostFs)
            if (current.enableGameFixes != base.enableGameFixes) j.put("enableGameFixes", current.enableGameFixes)
            if (current.gamefixSoftwareRendererFmv != base.gamefixSoftwareRendererFmv) j.put("gamefixSoftwareRendererFmv", current.gamefixSoftwareRendererFmv)
            if (current.gamefixSkipMpeg != base.gamefixSkipMpeg) j.put("gamefixSkipMpeg", current.gamefixSkipMpeg)
            if (current.gamefixEETiming != base.gamefixEETiming) j.put("gamefixEETiming", current.gamefixEETiming)
            if (current.gamefixInstantDma != base.gamefixInstantDma) j.put("gamefixInstantDma", current.gamefixInstantDma)
            if (current.gamefixBlitInternalFps != base.gamefixBlitInternalFps) j.put("gamefixBlitInternalFps", current.gamefixBlitInternalFps)
            if (current.gamefixFpuMul        != base.gamefixFpuMul)        j.put("gamefixFpuMul", current.gamefixFpuMul)
            if (current.gamefixOphFlag       != base.gamefixOphFlag)       j.put("gamefixOphFlag", current.gamefixOphFlag)
            if (current.gamefixGifFifo       != base.gamefixGifFifo)       j.put("gamefixGifFifo", current.gamefixGifFifo)
            if (current.gamefixDmaBusy       != base.gamefixDmaBusy)       j.put("gamefixDmaBusy", current.gamefixDmaBusy)
            if (current.gamefixVif1Stall     != base.gamefixVif1Stall)     j.put("gamefixVif1Stall", current.gamefixVif1Stall)
            if (current.gamefixIbit          != base.gamefixIbit)          j.put("gamefixIbit", current.gamefixIbit)
            if (current.gamefixFullVu0Sync   != base.gamefixFullVu0Sync)   j.put("gamefixFullVu0Sync", current.gamefixFullVu0Sync)
            if (current.gamefixVuAddSub      != base.gamefixVuAddSub)      j.put("gamefixVuAddSub", current.gamefixVuAddSub)
            if (current.gamefixVuOverflow    != base.gamefixVuOverflow)    j.put("gamefixVuOverflow", current.gamefixVuOverflow)
            if (current.gamefixXgkick        != base.gamefixXgkick)        j.put("gamefixXgkick", current.gamefixXgkick)
            if (current.gamefixGoemonTlb     != base.gamefixGoemonTlb)     j.put("gamefixGoemonTlb", current.gamefixGoemonTlb)
            if (current.gamefixVuSync        != base.gamefixVuSync)        j.put("gamefixVuSync", current.gamefixVuSync)
            if (current.skipDuplicateFrames  != base.skipDuplicateFrames)  j.put("skipDuplicateFrames", current.skipDuplicateFrames)
            if (current.eeFpuRoundMode       != base.eeFpuRoundMode)       j.put("eeFpuRoundMode", current.eeFpuRoundMode)
            if (current.vu0RoundMode         != base.vu0RoundMode)         j.put("vu0RoundMode", current.vu0RoundMode)
            if (current.vu1RoundMode         != base.vu1RoundMode)         j.put("vu1RoundMode", current.vu1RoundMode)
            if (current.screenOffsets        != base.screenOffsets)        j.put("screenOffsets", current.screenOffsets)
            if (current.showOverscan         != base.showOverscan)         j.put("showOverscan", current.showOverscan)
            if (current.antiBlur             != base.antiBlur)             j.put("antiBlur", current.antiBlur)
            if (current.disableInterlaceOffset != base.disableInterlaceOffset) j.put("disableInterlaceOffset", current.disableInterlaceOffset)
            if (current.syncToHostRefresh    != base.syncToHostRefresh)    j.put("syncToHostRefresh", current.syncToHostRefresh)
            if (current.disableFramebufferFetch != base.disableFramebufferFetch) j.put("disableFramebufferFetch", current.disableFramebufferFetch)
            if (current.hwRov != base.hwRov) j.put("hwRov", current.hwRov)
            if (current.hwAa1 != base.hwAa1) j.put("hwAa1", current.hwAa1)
            if (current.adrenoFbFetch != base.adrenoFbFetch) j.put("adrenoFbFetch", current.adrenoFbFetch)
            if (current.forceMaliFbFetch != base.forceMaliFbFetch) j.put("forceMaliFbFetch", current.forceMaliFbFetch)
            if (current.useAngleOpenGL != base.useAngleOpenGL) j.put("useAngleOpenGL", current.useAngleOpenGL)
            if (current.overrideTextureBarriers != base.overrideTextureBarriers) j.put("overrideTextureBarriers", current.overrideTextureBarriers)
            if (current.disableVertexShaderExpand != base.disableVertexShaderExpand) j.put("disableVertexShaderExpand", current.disableVertexShaderExpand)
            if (current.useBlitSwapChain     != base.useBlitSwapChain)     j.put("useBlitSwapChain", current.useBlitSwapChain)
            if (current.disableShaderCache   != base.disableShaderCache)   j.put("disableShaderCache", current.disableShaderCache)
            if (current.hwAccurateAlphaTest  != base.hwAccurateAlphaTest)  j.put("hwAccurateAlphaTest", current.hwAccurateAlphaTest)
            if (current.skipDrawStart        != base.skipDrawStart)        j.put("skipDrawStart", current.skipDrawStart)
            if (current.skipDrawEnd          != base.skipDrawEnd)          j.put("skipDrawEnd", current.skipDrawEnd)
            if (current.spinGpuReadbacks     != base.spinGpuReadbacks)     j.put("spinGpuReadbacks", current.spinGpuReadbacks)
            if (current.spinCpuReadbacks     != base.spinCpuReadbacks)     j.put("spinCpuReadbacks", current.spinCpuReadbacks)
            if (current.integerScaling       != base.integerScaling)       j.put("integerScaling", current.integerScaling)
            if (current.dithering            != base.dithering)            j.put("dithering", current.dithering)
            if (current.vsyncQueueSize       != base.vsyncQueueSize)       j.put("vsyncQueueSize", current.vsyncQueueSize)
            if (current.autoFlushSw          != base.autoFlushSw)          j.put("autoFlushSw", current.autoFlushSw)
            if (current.mipmapSw             != base.mipmapSw)             j.put("mipmapSw", current.mipmapSw)
            if (current.swThreads            != base.swThreads)            j.put("swThreads", current.swThreads)
            if (current.swThreadsHeight      != base.swThreadsHeight)      j.put("swThreadsHeight", current.swThreadsHeight)
            if (current.aspectRatio         != base.aspectRatio)         j.put("aspectRatio", current.aspectRatio)
            if (current.fmvAspectRatio      != base.fmvAspectRatio)      j.put("fmvAspectRatio", current.fmvAspectRatio)
            if (current.deinterlaceMode     != base.deinterlaceMode)     j.put("deinterlaceMode", current.deinterlaceMode)
            if (current.dev9EthEnable       != base.dev9EthEnable)       j.put("dev9EthEnable", current.dev9EthEnable)
            if (current.dev9EthApi          != base.dev9EthApi)          j.put("dev9EthApi", current.dev9EthApi)
            if (current.dev9EthDevice       != base.dev9EthDevice)       j.put("dev9EthDevice", current.dev9EthDevice)
            if (current.dev9EthLogDhcp      != base.dev9EthLogDhcp)      j.put("dev9EthLogDhcp", current.dev9EthLogDhcp)
            if (current.dev9EthLogDns       != base.dev9EthLogDns)       j.put("dev9EthLogDns", current.dev9EthLogDns)
            if (current.dev9InterceptDhcp   != base.dev9InterceptDhcp)   j.put("dev9InterceptDhcp", current.dev9InterceptDhcp)
            if (current.dev9Ps2Ip           != base.dev9Ps2Ip)           j.put("dev9Ps2Ip", current.dev9Ps2Ip)
            if (current.dev9Mask            != base.dev9Mask)            j.put("dev9Mask", current.dev9Mask)
            if (current.dev9Gateway         != base.dev9Gateway)         j.put("dev9Gateway", current.dev9Gateway)
            if (current.dev9Dns1            != base.dev9Dns1)            j.put("dev9Dns1", current.dev9Dns1)
            if (current.dev9Dns2            != base.dev9Dns2)            j.put("dev9Dns2", current.dev9Dns2)
            if (current.dev9AutoMask        != base.dev9AutoMask)        j.put("dev9AutoMask", current.dev9AutoMask)
            if (current.dev9AutoGateway     != base.dev9AutoGateway)     j.put("dev9AutoGateway", current.dev9AutoGateway)
            if (current.dev9ModeDns1        != base.dev9ModeDns1)        j.put("dev9ModeDns1", current.dev9ModeDns1)
            if (current.dev9ModeDns2        != base.dev9ModeDns2)        j.put("dev9ModeDns2", current.dev9ModeDns2)
            if (current.dev9EthHosts        != base.dev9EthHosts) {
                j.put("dev9EthHosts", JSONArray().apply {
                    current.dev9EthHosts.forEach { host ->
                        put(JSONObject().apply {
                            put("url", host.url)
                            put("ip", host.ip)
                            put("enabled", host.enabled)
                        })
                    }
                })
            }
            if (current.dev9HddEnable       != base.dev9HddEnable)       j.put("dev9HddEnable", current.dev9HddEnable)
            if (current.dev9HddFile         != base.dev9HddFile)         j.put("dev9HddFile", current.dev9HddFile)
            if (current.memoryCardSlot1Enabled != base.memoryCardSlot1Enabled) j.put("memoryCardSlot1Enabled", current.memoryCardSlot1Enabled)
            if (current.memoryCardSlot1Filename != base.memoryCardSlot1Filename) j.put("memoryCardSlot1Filename", current.memoryCardSlot1Filename)
            if (current.biosFilename != base.biosFilename) j.put("biosFilename", current.biosFilename)
            if (current.memoryCardSlot2Enabled != base.memoryCardSlot2Enabled) j.put("memoryCardSlot2Enabled", current.memoryCardSlot2Enabled)
            if (current.memoryCardSlot2Filename != base.memoryCardSlot2Filename) j.put("memoryCardSlot2Filename", current.memoryCardSlot2Filename)
            if (current.usbKeyboard         != base.usbKeyboard)         j.put("usbKeyboard", current.usbKeyboard)
            if (current.recEE               != base.recEE)               j.put("recEE", current.recEE)
            if (current.recIOP              != base.recIOP)              j.put("recIOP", current.recIOP)
            if (current.recVU0              != base.recVU0)              j.put("recVU0", current.recVU0)
            if (current.recVU1              != base.recVU1)              j.put("recVU1", current.recVU1)
            if (current.enableFastmem       != base.enableFastmem)       j.put("enableFastmem", current.enableFastmem)
            if (current.vu1InlineFmacStall  != base.vu1InlineFmacStall)  j.put("vu1InlineFmacStall", current.vu1InlineFmacStall)
            if (current.vu1CrossBlockPState != base.vu1CrossBlockPState) j.put("vu1CrossBlockPState", current.vu1CrossBlockPState)
            if (current.vu1InlineDrainTestPipes != base.vu1InlineDrainTestPipes) j.put("vu1InlineDrainTestPipes", current.vu1InlineDrainTestPipes)
            if (current.vu1FmacInstanceRouting != base.vu1FmacInstanceRouting) j.put("vu1FmacInstanceRouting", current.vu1FmacInstanceRouting)
            if (current.hwMipmap            != base.hwMipmap)            j.put("hwMipmap", current.hwMipmap)
            if (current.accurateBlendingUnit!= base.accurateBlendingUnit)j.put("accurateBlendingUnit", current.accurateBlendingUnit)
            if (current.textureFiltering    != base.textureFiltering)    j.put("textureFiltering", current.textureFiltering)
            if (current.displayBilinear     != base.displayBilinear)     j.put("displayBilinear", current.displayBilinear)
            if (current.texturePreloading   != base.texturePreloading)   j.put("texturePreloading", current.texturePreloading)
            if (current.hardwareDownloadMode!= base.hardwareDownloadMode)j.put("hardwareDownloadMode", current.hardwareDownloadMode)
            if (current.tvShader            != base.tvShader)            j.put("tvShader", current.tvShader)
            if (current.shadeBoost          != base.shadeBoost)          j.put("shadeBoost", current.shadeBoost)
            if (current.shadeBoostBrightness != base.shadeBoostBrightness) j.put("shadeBoostBrightness", current.shadeBoostBrightness)
            if (current.shadeBoostContrast  != base.shadeBoostContrast)  j.put("shadeBoostContrast", current.shadeBoostContrast)
            if (current.shadeBoostSaturation != base.shadeBoostSaturation) j.put("shadeBoostSaturation", current.shadeBoostSaturation)
            if (current.shadeBoostGamma     != base.shadeBoostGamma)     j.put("shadeBoostGamma", current.shadeBoostGamma)
            if (current.fxaa                != base.fxaa)                j.put("fxaa", current.fxaa)
            if (current.shaderChainEnabled  != base.shaderChainEnabled)  j.put("shaderChainEnabled", current.shaderChainEnabled)
            if (current.shaderChainPreset   != base.shaderChainPreset)   j.put("shaderChainPreset", current.shaderChainPreset)
            if (current.shaderChainParams   != base.shaderChainParams)   j.put("shaderChainParams", shaderChainParamsToJson(current.shaderChainParams))
            if (current.casMode             != base.casMode)             j.put("casMode", current.casMode)
            if (current.casSharpness        != base.casSharpness)        j.put("casSharpness", current.casSharpness)
            if (current.loadTextureReplacements != base.loadTextureReplacements) j.put("loadTextureReplacements", current.loadTextureReplacements)
            if (current.loadTextureReplacementsAsync != base.loadTextureReplacementsAsync) j.put("loadTextureReplacementsAsync", current.loadTextureReplacementsAsync)
            if (current.precacheTextureReplacements != base.precacheTextureReplacements) j.put("precacheTextureReplacements", current.precacheTextureReplacements)
            if (current.dumpReplaceableTextures != base.dumpReplaceableTextures) j.put("dumpReplaceableTextures", current.dumpReplaceableTextures)
            if (current.osdShowTextureReplacements != base.osdShowTextureReplacements) j.put("osdShowTextureReplacements", current.osdShowTextureReplacements)
            if (current.osdShowFps != base.osdShowFps) j.put("osdShowFps", current.osdShowFps)
            if (current.osdScale != base.osdScale) j.put("osdScale", current.osdScale)
            if (current.vsyncEnable != base.vsyncEnable) j.put("vsyncEnable", current.vsyncEnable)
            if (current.osdShowVps != base.osdShowVps) j.put("osdShowVps", current.osdShowVps)
            if (current.osdShowSpeed != base.osdShowSpeed) j.put("osdShowSpeed", current.osdShowSpeed)
            if (current.osdShowCpu != base.osdShowCpu) j.put("osdShowCpu", current.osdShowCpu)
            if (current.osdShowGpu != base.osdShowGpu) j.put("osdShowGpu", current.osdShowGpu)
            if (current.osdShowResolution != base.osdShowResolution) j.put("osdShowResolution", current.osdShowResolution)
            if (current.osdShowGsStats != base.osdShowGsStats) j.put("osdShowGsStats", current.osdShowGsStats)
            if (current.osdShowFrameTimes != base.osdShowFrameTimes) j.put("osdShowFrameTimes", current.osdShowFrameTimes)
            if (current.osdShowHardwareInfo != base.osdShowHardwareInfo) j.put("osdShowHardwareInfo", current.osdShowHardwareInfo)
            if (current.osdShowMessages != base.osdShowMessages) j.put("osdShowMessages", current.osdShowMessages)
            if (current.osdShowGpuStats != base.osdShowGpuStats) j.put("osdShowGpuStats", current.osdShowGpuStats)
            if (current.osdShowVersion != base.osdShowVersion) j.put("osdShowVersion", current.osdShowVersion)
            if (current.osdShowSettings != base.osdShowSettings) j.put("osdShowSettings", current.osdShowSettings)
            if (current.osdShowInputs != base.osdShowInputs) j.put("osdShowInputs", current.osdShowInputs)
            if (current.autoFlush           != base.autoFlush)           j.put("autoFlush", current.autoFlush)
            if (current.halfPixelOffset     != base.halfPixelOffset)     j.put("halfPixelOffset", current.halfPixelOffset)
            if (current.limit24BitDepth     != base.limit24BitDepth)     j.put("limit24BitDepth", current.limit24BitDepth)
            if (current.manualUserHacks     != base.manualUserHacks)     j.put("manualUserHacks", current.manualUserHacks)
            if (current.textureInsideRt     != base.textureInsideRt)     j.put("textureInsideRt", current.textureInsideRt)
            if (current.nativeScaling       != base.nativeScaling)       j.put("nativeScaling", current.nativeScaling)
            if (current.roundSprite         != base.roundSprite)         j.put("roundSprite", current.roundSprite)
            if (current.bilinearUpscale     != base.bilinearUpscale)     j.put("bilinearUpscale", current.bilinearUpscale)
            if (current.gpuTargetClut       != base.gpuTargetClut)       j.put("gpuTargetClut", current.gpuTargetClut)
            if (current.cpuSpriteRenderBw   != base.cpuSpriteRenderBw)   j.put("cpuSpriteRenderBw", current.cpuSpriteRenderBw)
            if (current.cpuSpriteRenderLevel != base.cpuSpriteRenderLevel) j.put("cpuSpriteRenderLevel", current.cpuSpriteRenderLevel)
            if (current.alignSprite         != base.alignSprite)         j.put("alignSprite", current.alignSprite)
            if (current.mergeSprite         != base.mergeSprite)         j.put("mergeSprite", current.mergeSprite)
            if (current.forceEvenSpritePosition != base.forceEvenSpritePosition) j.put("forceEvenSpritePosition", current.forceEvenSpritePosition)
            if (current.unscaledPaletteDraw != base.unscaledPaletteDraw) j.put("unscaledPaletteDraw", current.unscaledPaletteDraw)
            if (current.textureOffsetX      != base.textureOffsetX)      j.put("textureOffsetX", current.textureOffsetX)
            if (current.textureOffsetY      != base.textureOffsetY)      j.put("textureOffsetY", current.textureOffsetY)
            if (current.gpuPaletteConversion != base.gpuPaletteConversion) j.put("gpuPaletteConversion", current.gpuPaletteConversion)
            if (current.cpuFramebufferConversion != base.cpuFramebufferConversion) j.put("cpuFramebufferConversion", current.cpuFramebufferConversion)
            if (current.readTargetsWhenClosing != base.readTargetsWhenClosing) j.put("readTargetsWhenClosing", current.readTargetsWhenClosing)
            if (current.disableDepthEmulation != base.disableDepthEmulation) j.put("disableDepthEmulation", current.disableDepthEmulation)
            if (current.disablePartialInvalidation != base.disablePartialInvalidation) j.put("disablePartialInvalidation", current.disablePartialInvalidation)
            if (current.disableSafeFeatures != base.disableSafeFeatures) j.put("disableSafeFeatures", current.disableSafeFeatures)
            if (current.disableRenderFixes  != base.disableRenderFixes)  j.put("disableRenderFixes", current.disableRenderFixes)
            if (current.preloadFrameData    != base.preloadFrameData)    j.put("preloadFrameData", current.preloadFrameData)
            if (current.estimateTextureRegion != base.estimateTextureRegion) j.put("estimateTextureRegion", current.estimateTextureRegion)
            if (current.drawBuffering        != base.drawBuffering)        j.put("drawBuffering", current.drawBuffering)
            if (current.cpuClutRender       != base.cpuClutRender)       j.put("cpuClutRender", current.cpuClutRender)
            if (current.triFilter           != base.triFilter)           j.put("triFilter", current.triFilter)
            if (current.maxAnisotropy       != base.maxAnisotropy)       j.put("maxAnisotropy", current.maxAnisotropy)
            if (current.gpuProfile          != base.gpuProfile)          j.put("gpuProfile", current.gpuProfile)
            return j
        }

        fun merge(base: Settings, overrides: JSONObject): Settings = Settings(
            eeCycleRate = if (overrides.has("eeCycleRate")) overrides.getInt("eeCycleRate") else base.eeCycleRate,
            eeCycleSkip = if (overrides.has("eeCycleSkip")) overrides.getInt("eeCycleSkip") else base.eeCycleSkip,
            eeClampMode = if (overrides.has("eeClampMode")) overrides.getInt("eeClampMode") else base.eeClampMode,
            vuClampMode = if (overrides.has("vuClampMode")) overrides.getInt("vuClampMode") else base.vuClampMode,
            mtvu = if (overrides.has("mtvu")) overrides.getBoolean("mtvu") else base.mtvu,
            vu1Instant = if (overrides.has("vu1Instant")) overrides.getBoolean("vu1Instant") else base.vu1Instant,
            vuFlagHack = if (overrides.has("vuFlagHack")) overrides.getBoolean("vuFlagHack") else base.vuFlagHack,
            fastCDVD = if (overrides.has("fastCDVD")) overrides.getBoolean("fastCDVD") else base.fastCDVD,
            intcStat = if (overrides.has("intcStat")) overrides.getBoolean("intcStat") else base.intcStat,
            waitLoop = if (overrides.has("waitLoop")) overrides.getBoolean("waitLoop") else base.waitLoop,
            vuNeonFusions = if (overrides.has("vuNeonFusions")) overrides.getBoolean("vuNeonFusions") else base.vuNeonFusions,
            vuDeferredWrites = if (overrides.has("vuDeferredWrites")) overrides.getBoolean("vuDeferredWrites") else base.vuDeferredWrites,
            vuSkipStallSim = if (overrides.has("vuSkipStallSim")) overrides.getBoolean("vuSkipStallSim") else base.vuSkipStallSim,
            frameLimitEnable = if (overrides.has("frameLimitEnable")) overrides.getBoolean("frameLimitEnable") else base.frameLimitEnable,
            nominalSpeedPercent = if (overrides.has("nominalSpeedPercent")) overrides.getInt("nominalSpeedPercent") else base.nominalSpeedPercent,
            fpsLimit = if (overrides.has("fpsLimit")) overrides.getInt("fpsLimit") else base.fpsLimit,
            frameSkip = if (overrides.has("frameSkip")) overrides.getInt("frameSkip") else base.frameSkip,
            audioVolume = if (overrides.has("audioVolume")) overrides.getInt("audioVolume") else base.audioVolume,
            audioMuted = if (overrides.has("audioMuted")) overrides.getBoolean("audioMuted") else base.audioMuted,
            audioSwapChannels = if (overrides.has("audioSwapChannels")) overrides.getBoolean("audioSwapChannels") else base.audioSwapChannels,
            audioTimeStretch = if (overrides.has("audioTimeStretch")) overrides.getBoolean("audioTimeStretch") else base.audioTimeStretch,
            audioBufferMs = if (overrides.has("audioBufferMs")) overrides.getInt("audioBufferMs") else base.audioBufferMs,
            audioOutputLatencyMs = if (overrides.has("audioOutputLatencyMs")) overrides.getInt("audioOutputLatencyMs") else base.audioOutputLatencyMs,
            audioFastForwardVolume = if (overrides.has("audioFastForwardVolume")) overrides.getInt("audioFastForwardVolume") else base.audioFastForwardVolume,
            spu2NeonReverb = if (overrides.has("spu2NeonReverb")) overrides.getBoolean("spu2NeonReverb") else base.spu2NeonReverb,
            renderer = if (overrides.has("renderer")) overrides.getString("renderer") else base.renderer,
            upscaleFloat = if (overrides.has("upscaleFloat")) overrides.getDouble("upscaleFloat").toFloat() else base.upscaleFloat,
            framerateNtsc = if (overrides.has("framerateNtsc")) overrides.getDouble("framerateNtsc").toFloat() else base.framerateNtsc,
            frameratePal = if (overrides.has("frameratePal")) overrides.getDouble("frameratePal").toFloat() else base.frameratePal,
            enablePatches = if (overrides.has("enablePatches")) overrides.getBoolean("enablePatches") else base.enablePatches,
            enableCheats = if (overrides.has("enableCheats")) overrides.getBoolean("enableCheats") else base.enableCheats,
            enableWideScreenPatches = if (overrides.has("enableWideScreenPatches")) overrides.getBoolean("enableWideScreenPatches") else base.enableWideScreenPatches,
            enableNoInterlacingPatches = if (overrides.has("enableNoInterlacingPatches")) overrides.getBoolean("enableNoInterlacingPatches") else base.enableNoInterlacingPatches,
            enableFastBoot = if (overrides.has("enableFastBoot")) overrides.getBoolean("enableFastBoot") else base.enableFastBoot,
            hostFs = if (overrides.has("hostFs")) overrides.getBoolean("hostFs") else base.hostFs,
            enableGameFixes = if (overrides.has("enableGameFixes")) overrides.getBoolean("enableGameFixes") else base.enableGameFixes,
            gamefixSoftwareRendererFmv = if (overrides.has("gamefixSoftwareRendererFmv")) overrides.getBoolean("gamefixSoftwareRendererFmv") else base.gamefixSoftwareRendererFmv,
            gamefixSkipMpeg = if (overrides.has("gamefixSkipMpeg")) overrides.getBoolean("gamefixSkipMpeg") else base.gamefixSkipMpeg,
            gamefixEETiming = if (overrides.has("gamefixEETiming")) overrides.getBoolean("gamefixEETiming") else base.gamefixEETiming,
            gamefixInstantDma = if (overrides.has("gamefixInstantDma")) overrides.getBoolean("gamefixInstantDma") else base.gamefixInstantDma,
            gamefixBlitInternalFps = if (overrides.has("gamefixBlitInternalFps")) overrides.getBoolean("gamefixBlitInternalFps") else base.gamefixBlitInternalFps,
            gamefixFpuMul = if (overrides.has("gamefixFpuMul")) overrides.getBoolean("gamefixFpuMul") else base.gamefixFpuMul,
            gamefixOphFlag = if (overrides.has("gamefixOphFlag")) overrides.getBoolean("gamefixOphFlag") else base.gamefixOphFlag,
            gamefixGifFifo = if (overrides.has("gamefixGifFifo")) overrides.getBoolean("gamefixGifFifo") else base.gamefixGifFifo,
            gamefixDmaBusy = if (overrides.has("gamefixDmaBusy")) overrides.getBoolean("gamefixDmaBusy") else base.gamefixDmaBusy,
            gamefixVif1Stall = if (overrides.has("gamefixVif1Stall")) overrides.getBoolean("gamefixVif1Stall") else base.gamefixVif1Stall,
            gamefixIbit = if (overrides.has("gamefixIbit")) overrides.getBoolean("gamefixIbit") else base.gamefixIbit,
            gamefixFullVu0Sync = if (overrides.has("gamefixFullVu0Sync")) overrides.getBoolean("gamefixFullVu0Sync") else base.gamefixFullVu0Sync,
            gamefixVuAddSub = if (overrides.has("gamefixVuAddSub")) overrides.getBoolean("gamefixVuAddSub") else base.gamefixVuAddSub,
            gamefixVuOverflow = if (overrides.has("gamefixVuOverflow")) overrides.getBoolean("gamefixVuOverflow") else base.gamefixVuOverflow,
            gamefixXgkick = if (overrides.has("gamefixXgkick")) overrides.getBoolean("gamefixXgkick") else base.gamefixXgkick,
            gamefixGoemonTlb = if (overrides.has("gamefixGoemonTlb")) overrides.getBoolean("gamefixGoemonTlb") else base.gamefixGoemonTlb,
            gamefixVuSync = if (overrides.has("gamefixVuSync")) overrides.getBoolean("gamefixVuSync") else base.gamefixVuSync,
            skipDuplicateFrames = if (overrides.has("skipDuplicateFrames")) overrides.getBoolean("skipDuplicateFrames") else base.skipDuplicateFrames,
            eeFpuRoundMode = if (overrides.has("eeFpuRoundMode")) overrides.getInt("eeFpuRoundMode") else base.eeFpuRoundMode,
            vu0RoundMode = if (overrides.has("vu0RoundMode")) overrides.getInt("vu0RoundMode") else base.vu0RoundMode,
            vu1RoundMode = if (overrides.has("vu1RoundMode")) overrides.getInt("vu1RoundMode") else base.vu1RoundMode,
            screenOffsets = if (overrides.has("screenOffsets")) overrides.getBoolean("screenOffsets") else base.screenOffsets,
            showOverscan = if (overrides.has("showOverscan")) overrides.getBoolean("showOverscan") else base.showOverscan,
            antiBlur = if (overrides.has("antiBlur")) overrides.getBoolean("antiBlur") else base.antiBlur,
            disableInterlaceOffset = if (overrides.has("disableInterlaceOffset")) overrides.getBoolean("disableInterlaceOffset") else base.disableInterlaceOffset,
            syncToHostRefresh = if (overrides.has("syncToHostRefresh")) overrides.getBoolean("syncToHostRefresh") else base.syncToHostRefresh,
            disableFramebufferFetch = if (overrides.has("disableFramebufferFetch")) overrides.getBoolean("disableFramebufferFetch") else base.disableFramebufferFetch,
            hwRov = if (overrides.has("hwRov")) overrides.getBoolean("hwRov") else base.hwRov,
            hwAa1 = if (overrides.has("hwAa1")) overrides.getBoolean("hwAa1") else base.hwAa1,
            hwAat = false,
            adrenoFbFetch = if (overrides.has("adrenoFbFetch")) overrides.getBoolean("adrenoFbFetch") else base.adrenoFbFetch,
            forceMaliFbFetch = if (overrides.has("forceMaliFbFetch")) overrides.getBoolean("forceMaliFbFetch") else base.forceMaliFbFetch,
            useAngleOpenGL = if (overrides.has("useAngleOpenGL")) overrides.getBoolean("useAngleOpenGL") else base.useAngleOpenGL,
            overrideTextureBarriers = if (overrides.has("overrideTextureBarriers")) overrides.getInt("overrideTextureBarriers") else base.overrideTextureBarriers,
            disableVertexShaderExpand = if (overrides.has("disableVertexShaderExpand")) overrides.getBoolean("disableVertexShaderExpand") else base.disableVertexShaderExpand,
            useBlitSwapChain = if (overrides.has("useBlitSwapChain")) overrides.getBoolean("useBlitSwapChain") else base.useBlitSwapChain,
            disableShaderCache = if (overrides.has("disableShaderCache")) overrides.getBoolean("disableShaderCache") else base.disableShaderCache,
            hwAccurateAlphaTest = when {
                overrides.has("hwAccurateAlphaTest") -> overrides.getBoolean("hwAccurateAlphaTest")
                overrides.has("hwAat") -> overrides.getBoolean("hwAat")
                else -> base.hwAccurateAlphaTest
            },
            skipDrawStart = if (overrides.has("skipDrawStart")) overrides.getInt("skipDrawStart") else base.skipDrawStart,
            skipDrawEnd = if (overrides.has("skipDrawEnd")) overrides.getInt("skipDrawEnd") else base.skipDrawEnd,
            spinGpuReadbacks = if (overrides.has("spinGpuReadbacks")) overrides.getBoolean("spinGpuReadbacks") else base.spinGpuReadbacks,
            spinCpuReadbacks = if (overrides.has("spinCpuReadbacks")) overrides.getBoolean("spinCpuReadbacks") else base.spinCpuReadbacks,
            integerScaling = if (overrides.has("integerScaling")) overrides.getBoolean("integerScaling") else base.integerScaling,
            dithering = if (overrides.has("dithering")) overrides.getInt("dithering") else base.dithering,
            vsyncQueueSize = if (overrides.has("vsyncQueueSize")) overrides.getInt("vsyncQueueSize") else base.vsyncQueueSize,
            autoFlushSw = if (overrides.has("autoFlushSw")) overrides.getBoolean("autoFlushSw") else base.autoFlushSw,
            mipmapSw = if (overrides.has("mipmapSw")) overrides.getBoolean("mipmapSw") else base.mipmapSw,
            swThreads = if (overrides.has("swThreads")) overrides.getInt("swThreads") else base.swThreads,
            swThreadsHeight = if (overrides.has("swThreadsHeight")) overrides.getInt("swThreadsHeight") else base.swThreadsHeight,
            aspectRatio = if (overrides.has("aspectRatio")) overrides.getInt("aspectRatio") else base.aspectRatio,
            fmvAspectRatio = if (overrides.has("fmvAspectRatio")) overrides.getInt("fmvAspectRatio") else base.fmvAspectRatio,
            deinterlaceMode = if (overrides.has("deinterlaceMode")) overrides.getInt("deinterlaceMode") else base.deinterlaceMode,
            dev9EthEnable = if (overrides.has("dev9EthEnable")) overrides.getBoolean("dev9EthEnable") else base.dev9EthEnable,
            dev9EthApi = if (overrides.has("dev9EthApi")) overrides.getString("dev9EthApi").ifEmpty { base.dev9EthApi } else base.dev9EthApi,
            dev9EthDevice = if (overrides.has("dev9EthDevice")) overrides.getString("dev9EthDevice").ifEmpty { base.dev9EthDevice } else base.dev9EthDevice,
            dev9EthLogDhcp = if (overrides.has("dev9EthLogDhcp")) overrides.getBoolean("dev9EthLogDhcp") else base.dev9EthLogDhcp,
            dev9EthLogDns = if (overrides.has("dev9EthLogDns")) overrides.getBoolean("dev9EthLogDns") else base.dev9EthLogDns,
            dev9InterceptDhcp = if (overrides.has("dev9InterceptDhcp")) overrides.getBoolean("dev9InterceptDhcp") else base.dev9InterceptDhcp,
            dev9Ps2Ip = if (overrides.has("dev9Ps2Ip")) overrides.getString("dev9Ps2Ip").ifEmpty { base.dev9Ps2Ip } else base.dev9Ps2Ip,
            dev9Mask = if (overrides.has("dev9Mask")) overrides.getString("dev9Mask").ifEmpty { base.dev9Mask } else base.dev9Mask,
            dev9Gateway = if (overrides.has("dev9Gateway")) overrides.getString("dev9Gateway").ifEmpty { base.dev9Gateway } else base.dev9Gateway,
            dev9Dns1 = if (overrides.has("dev9Dns1")) overrides.getString("dev9Dns1").ifEmpty { base.dev9Dns1 } else base.dev9Dns1,
            dev9Dns2 = if (overrides.has("dev9Dns2")) overrides.getString("dev9Dns2").ifEmpty { base.dev9Dns2 } else base.dev9Dns2,
            dev9AutoMask = if (overrides.has("dev9AutoMask")) overrides.getBoolean("dev9AutoMask") else base.dev9AutoMask,
            dev9AutoGateway = if (overrides.has("dev9AutoGateway")) overrides.getBoolean("dev9AutoGateway") else base.dev9AutoGateway,
            dev9ModeDns1 = if (overrides.has("dev9ModeDns1")) overrides.getString("dev9ModeDns1").ifEmpty { base.dev9ModeDns1 } else base.dev9ModeDns1,
            dev9ModeDns2 = if (overrides.has("dev9ModeDns2")) overrides.getString("dev9ModeDns2").ifEmpty { base.dev9ModeDns2 } else base.dev9ModeDns2,
            dev9EthHosts = if (overrides.has("dev9EthHosts")) {
                overrides.optJSONArray("dev9EthHosts")?.let { array ->
                    buildList {
                        repeat(array.length()) { index ->
                            array.optJSONObject(index)?.let { host ->
                                add(
                                    Dev9HostMapping(
                                        url = host.optString("url"),
                                        ip = host.optString("ip", "0.0.0.0"),
                                        enabled = host.optBoolean("enabled", true),
                                    ),
                                )
                            }
                        }
                    }
                } ?: base.dev9EthHosts
            } else base.dev9EthHosts,
            dev9HddEnable = if (overrides.has("dev9HddEnable")) overrides.getBoolean("dev9HddEnable") else base.dev9HddEnable,
            dev9HddFile = if (overrides.has("dev9HddFile")) overrides.getString("dev9HddFile").ifEmpty { base.dev9HddFile } else base.dev9HddFile,
            memoryCardSlot1Enabled = if (overrides.has("memoryCardSlot1Enabled")) overrides.getBoolean("memoryCardSlot1Enabled") else base.memoryCardSlot1Enabled,
            memoryCardSlot1Filename = if (overrides.has("memoryCardSlot1Filename")) overrides.getString("memoryCardSlot1Filename").ifEmpty { base.memoryCardSlot1Filename } else base.memoryCardSlot1Filename,
            biosFilename = if (overrides.has("biosFilename")) overrides.getString("biosFilename") else base.biosFilename,
            memoryCardSlot2Enabled = if (overrides.has("memoryCardSlot2Enabled")) overrides.getBoolean("memoryCardSlot2Enabled") else base.memoryCardSlot2Enabled,
            memoryCardSlot2Filename = if (overrides.has("memoryCardSlot2Filename")) overrides.getString("memoryCardSlot2Filename").ifEmpty { base.memoryCardSlot2Filename } else base.memoryCardSlot2Filename,
            usbKeyboard = if (overrides.has("usbKeyboard")) overrides.getBoolean("usbKeyboard") else base.usbKeyboard,
            recEE = if (overrides.has("recEE")) overrides.getBoolean("recEE") else base.recEE,
            recIOP = if (overrides.has("recIOP")) overrides.getBoolean("recIOP") else base.recIOP,
            recVU0 = if (overrides.has("recVU0")) overrides.getBoolean("recVU0") else base.recVU0,
            recVU1 = if (overrides.has("recVU1")) overrides.getBoolean("recVU1") else base.recVU1,
            enableFastmem = if (overrides.has("enableFastmem")) overrides.getBoolean("enableFastmem") else base.enableFastmem,
            useMacEE = true,
            useMacIOP = true,
            useMacVU0 = true,
            useMacVU1 = true,
            vu1InlineFmacStall = if (overrides.has("vu1InlineFmacStall")) overrides.getBoolean("vu1InlineFmacStall") else base.vu1InlineFmacStall,
            vu1CrossBlockPState = if (overrides.has("vu1CrossBlockPState")) overrides.getBoolean("vu1CrossBlockPState") else base.vu1CrossBlockPState,
            vu1InlineDrainTestPipes = if (overrides.has("vu1InlineDrainTestPipes")) overrides.getBoolean("vu1InlineDrainTestPipes") else base.vu1InlineDrainTestPipes,
            vu1FmacInstanceRouting = if (overrides.has("vu1FmacInstanceRouting")) overrides.getBoolean("vu1FmacInstanceRouting") else base.vu1FmacInstanceRouting,
            hwMipmap = if (overrides.has("hwMipmap")) overrides.getBoolean("hwMipmap") else base.hwMipmap,
            accurateBlendingUnit = if (overrides.has("accurateBlendingUnit")) overrides.getInt("accurateBlendingUnit") else base.accurateBlendingUnit,
            textureFiltering = if (overrides.has("textureFiltering")) overrides.getInt("textureFiltering") else base.textureFiltering,
            displayBilinear = if (overrides.has("displayBilinear")) overrides.getInt("displayBilinear") else base.displayBilinear,
            texturePreloading = if (overrides.has("texturePreloading")) overrides.getInt("texturePreloading") else base.texturePreloading,
            hardwareDownloadMode = if (overrides.has("hardwareDownloadMode")) overrides.getInt("hardwareDownloadMode") else base.hardwareDownloadMode,
            tvShader = if (overrides.has("tvShader")) overrides.getInt("tvShader") else base.tvShader,
            shadeBoost = if (overrides.has("shadeBoost")) overrides.getBoolean("shadeBoost") else base.shadeBoost,
            shadeBoostBrightness = if (overrides.has("shadeBoostBrightness")) overrides.getInt("shadeBoostBrightness") else base.shadeBoostBrightness,
            shadeBoostContrast = if (overrides.has("shadeBoostContrast")) overrides.getInt("shadeBoostContrast") else base.shadeBoostContrast,
            shadeBoostSaturation = if (overrides.has("shadeBoostSaturation")) overrides.getInt("shadeBoostSaturation") else base.shadeBoostSaturation,
            shadeBoostGamma = if (overrides.has("shadeBoostGamma")) overrides.getInt("shadeBoostGamma") else base.shadeBoostGamma,
            fxaa = if (overrides.has("fxaa")) overrides.getBoolean("fxaa") else base.fxaa,
            shaderChainEnabled = if (overrides.has("shaderChainEnabled")) overrides.getBoolean("shaderChainEnabled") else base.shaderChainEnabled,
            shaderChainPreset = if (overrides.has("shaderChainPreset")) overrides.getString("shaderChainPreset") else base.shaderChainPreset,
            shaderChainParams = if (overrides.has("shaderChainParams")) {
                shaderChainParamsFromJson(overrides.optJSONObject("shaderChainParams"))
            } else base.shaderChainParams,
            casMode = if (overrides.has("casMode")) overrides.getInt("casMode") else base.casMode,
            casSharpness = if (overrides.has("casSharpness")) overrides.getInt("casSharpness") else base.casSharpness,
            loadTextureReplacements = if (overrides.has("loadTextureReplacements")) overrides.getBoolean("loadTextureReplacements") else base.loadTextureReplacements,
            loadTextureReplacementsAsync = if (overrides.has("loadTextureReplacementsAsync")) overrides.getBoolean("loadTextureReplacementsAsync") else base.loadTextureReplacementsAsync,
            precacheTextureReplacements = if (overrides.has("precacheTextureReplacements")) overrides.getBoolean("precacheTextureReplacements") else base.precacheTextureReplacements,
            dumpReplaceableTextures = if (overrides.has("dumpReplaceableTextures")) overrides.getBoolean("dumpReplaceableTextures") else base.dumpReplaceableTextures,
            osdShowTextureReplacements = if (overrides.has("osdShowTextureReplacements")) overrides.getBoolean("osdShowTextureReplacements") else base.osdShowTextureReplacements,
            osdShowFps = if (overrides.has("osdShowFps")) overrides.getBoolean("osdShowFps") else base.osdShowFps,
            osdScale = if (overrides.has("osdScale")) overrides.getInt("osdScale") else base.osdScale,
            vsyncEnable = if (overrides.has("vsyncEnable")) overrides.getBoolean("vsyncEnable") else base.vsyncEnable,
            osdShowVps = if (overrides.has("osdShowVps")) overrides.getBoolean("osdShowVps") else base.osdShowVps,
            osdShowSpeed = if (overrides.has("osdShowSpeed")) overrides.getBoolean("osdShowSpeed") else base.osdShowSpeed,
            osdShowCpu = if (overrides.has("osdShowCpu")) overrides.getBoolean("osdShowCpu") else base.osdShowCpu,
            osdShowGpu = if (overrides.has("osdShowGpu")) overrides.getBoolean("osdShowGpu") else base.osdShowGpu,
            osdShowResolution = if (overrides.has("osdShowResolution")) overrides.getBoolean("osdShowResolution") else base.osdShowResolution,
            osdShowGsStats = if (overrides.has("osdShowGsStats")) overrides.getBoolean("osdShowGsStats") else base.osdShowGsStats,
            osdShowFrameTimes = if (overrides.has("osdShowFrameTimes")) overrides.getBoolean("osdShowFrameTimes") else base.osdShowFrameTimes,
            osdShowHardwareInfo = if (overrides.has("osdShowHardwareInfo")) overrides.getBoolean("osdShowHardwareInfo") else base.osdShowHardwareInfo,
            osdShowMessages = if (overrides.has("osdShowMessages")) overrides.getBoolean("osdShowMessages") else base.osdShowMessages,
            osdShowGpuStats = if (overrides.has("osdShowGpuStats")) overrides.getBoolean("osdShowGpuStats") else base.osdShowGpuStats,
            osdShowVersion = if (overrides.has("osdShowVersion")) overrides.getBoolean("osdShowVersion") else base.osdShowVersion,
            osdShowSettings = if (overrides.has("osdShowSettings")) overrides.getBoolean("osdShowSettings") else base.osdShowSettings,
            osdShowInputs = if (overrides.has("osdShowInputs")) overrides.getBoolean("osdShowInputs") else base.osdShowInputs,
            autoFlush = if (overrides.has("autoFlush")) overrides.getInt("autoFlush") else base.autoFlush,
            halfPixelOffset = if (overrides.has("halfPixelOffset")) overrides.getInt("halfPixelOffset") else base.halfPixelOffset,
            limit24BitDepth = if (overrides.has("limit24BitDepth")) overrides.getInt("limit24BitDepth") else base.limit24BitDepth,
            manualUserHacks = if (overrides.has("manualUserHacks")) overrides.getBoolean("manualUserHacks") else base.manualUserHacks,
            textureInsideRt = if (overrides.has("textureInsideRt")) overrides.getInt("textureInsideRt") else base.textureInsideRt,
            nativeScaling = if (overrides.has("nativeScaling")) overrides.getInt("nativeScaling") else base.nativeScaling,
            roundSprite = if (overrides.has("roundSprite")) overrides.getInt("roundSprite") else base.roundSprite,
            bilinearUpscale = if (overrides.has("bilinearUpscale")) overrides.getInt("bilinearUpscale") else base.bilinearUpscale,
            gpuTargetClut = if (overrides.has("gpuTargetClut")) overrides.getInt("gpuTargetClut") else base.gpuTargetClut,
            cpuSpriteRenderBw = if (overrides.has("cpuSpriteRenderBw")) overrides.getInt("cpuSpriteRenderBw") else base.cpuSpriteRenderBw,
            cpuSpriteRenderLevel = if (overrides.has("cpuSpriteRenderLevel")) overrides.getInt("cpuSpriteRenderLevel") else base.cpuSpriteRenderLevel,
            alignSprite = if (overrides.has("alignSprite")) overrides.getBoolean("alignSprite") else base.alignSprite,
            mergeSprite = if (overrides.has("mergeSprite")) overrides.getBoolean("mergeSprite") else base.mergeSprite,
            forceEvenSpritePosition = if (overrides.has("forceEvenSpritePosition")) overrides.getBoolean("forceEvenSpritePosition") else base.forceEvenSpritePosition,
            unscaledPaletteDraw = if (overrides.has("unscaledPaletteDraw")) overrides.getBoolean("unscaledPaletteDraw") else base.unscaledPaletteDraw,
            textureOffsetX = if (overrides.has("textureOffsetX")) overrides.getInt("textureOffsetX") else base.textureOffsetX,
            textureOffsetY = if (overrides.has("textureOffsetY")) overrides.getInt("textureOffsetY") else base.textureOffsetY,
            gpuPaletteConversion = if (overrides.has("gpuPaletteConversion")) overrides.getBoolean("gpuPaletteConversion") else base.gpuPaletteConversion,
            cpuFramebufferConversion = if (overrides.has("cpuFramebufferConversion")) overrides.getBoolean("cpuFramebufferConversion") else base.cpuFramebufferConversion,
            readTargetsWhenClosing = if (overrides.has("readTargetsWhenClosing")) overrides.getBoolean("readTargetsWhenClosing") else base.readTargetsWhenClosing,
            disableDepthEmulation = if (overrides.has("disableDepthEmulation")) overrides.getBoolean("disableDepthEmulation") else base.disableDepthEmulation,
            disablePartialInvalidation = if (overrides.has("disablePartialInvalidation")) overrides.getBoolean("disablePartialInvalidation") else base.disablePartialInvalidation,
            disableSafeFeatures = if (overrides.has("disableSafeFeatures")) overrides.getBoolean("disableSafeFeatures") else base.disableSafeFeatures,
            disableRenderFixes = if (overrides.has("disableRenderFixes")) overrides.getBoolean("disableRenderFixes") else base.disableRenderFixes,
            preloadFrameData = if (overrides.has("preloadFrameData")) overrides.getBoolean("preloadFrameData") else base.preloadFrameData,
            estimateTextureRegion = if (overrides.has("estimateTextureRegion")) overrides.getBoolean("estimateTextureRegion") else base.estimateTextureRegion,
            drawBuffering = if (overrides.has("drawBuffering")) overrides.getBoolean("drawBuffering") else base.drawBuffering,
            cpuClutRender = if (overrides.has("cpuClutRender")) overrides.getInt("cpuClutRender") else base.cpuClutRender,
            triFilter = if (overrides.has("triFilter")) overrides.getInt("triFilter") else base.triFilter,
            maxAnisotropy = if (overrides.has("maxAnisotropy")) overrides.getInt("maxAnisotropy") else base.maxAnisotropy,
            gpuProfile = if (overrides.has("gpuProfile")) overrides.getInt("gpuProfile") else base.gpuProfile,
        )
    }
}
