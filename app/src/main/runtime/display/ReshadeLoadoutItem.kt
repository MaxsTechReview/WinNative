package com.winlator.cmod.runtime.display

import com.winlator.cmod.runtime.reshade.ReshadeManager

// values keys: "<uniform>", or "<uniform>_<c>" per channel for COLOR params
data class ReshadeLoadoutItem(
    val name: String,
    val enabled: Boolean,
    val params: List<ReshadeManager.ReshadeParam>,
    val values: Map<String, Float>,
)
