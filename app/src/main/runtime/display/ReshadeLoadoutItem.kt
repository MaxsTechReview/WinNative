package com.winlator.cmod.runtime.display

import com.winlator.cmod.runtime.reshade.ReshadeManager

/**
 * One effect in the in-game ReShade loadout: its name, whether it's active (per-effect enabled), the
 * uniforms reflected from its .fx, and the current value-map (seedValues key scheme — "<uniform>" or
 * "<uniform>_<c>" for COLOR). The activity seeds a List<ReshadeLoadoutItem> into XServerDrawerState via
 * withReshadeState; the pane renders + edits it and hands changes back through the loadout callbacks.
 *
 * Java-accessed (getName/getEnabled/getParams/getValues) from XServerDisplayActivity.
 */
data class ReshadeLoadoutItem(
    val name: String,
    val enabled: Boolean,
    val params: List<ReshadeManager.ReshadeParam>,
    val values: Map<String, Float>,
)
