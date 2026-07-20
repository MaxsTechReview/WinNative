package com.winlator.cmod.runtime.reshade;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// Multi-effect ReShade loadout — the parse/serialize/migration layer shared by the Java activity (conf
// writer + live seam) and the Kotlin editors/drawer. A "loadout" is an ordered list of effects (chain
// order) each with an enabled flag; a mode governs how many can be active at once:
//   solo  = exactly one active (radio; for A/B comparing looks)
//   stack = any subset active, layered (checkboxes)
// Stored in Container/shortcut extras as `reshadeLoadout` (JSON array) + `reshadeMode`, with the
// per-effect uniform overrides nested under `reshadeParams` ({"<effect>":{"<uniform>":v}}).
//
// BACK-COMPAT: pre-loadout saves carry the single `reshadeEffect` string + a FLAT `reshadeParams`
// ({"<uniform>":v}). When the loadout array is absent, parse() synthesizes a one-entry solo loadout
// from the legacy effect and paramsForEffect() treats the flat params as that one effect's — so old
// profiles keep working and upgrade forward the next time they're saved.
public class ReshadeLoadout {
    public static final String MODE_SOLO = "solo";
    public static final String MODE_STACK = "stack";

    // Max effects compiled into one vkBasalt chain. Every entry is compiled at launch (a large loadout
    // lengthens the on-device shader compile), so we cap the list to keep launches responsive; the
    // editors soft-warn near it and the conf writer hard-truncates defensively.
    public static final int MAX_EFFECTS = 6;

    public static class Entry {
        public final String name;   // the effect's drop-in subfolder name (its selectable id)
        public boolean enabled;     // active (in the chain AND presenting) vs bypassed

        public Entry(String name, boolean enabled) {
            this.name = name;
            this.enabled = enabled;
        }
    }

    private static boolean isRealEffect(String name) {
        return name != null && !name.isEmpty() && !name.equalsIgnoreCase("None");
    }

    public static String normalizeMode(String mode) {
        return MODE_STACK.equalsIgnoreCase(mode) ? MODE_STACK : MODE_SOLO;
    }

    // Resolve the loadout list. Prefers the new reshadeLoadout JSON array; migrates a legacy single
    // reshadeEffect into a one-entry (enabled) loadout when the array is absent/blank/unparseable.
    public static List<Entry> parse(String loadoutJson, String legacyEffect) {
        ArrayList<Entry> out = new ArrayList<>();
        if (loadoutJson != null && !loadoutJson.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(loadoutJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String name = o.optString("name", "");
                    if (!isRealEffect(name)) continue;
                    out.add(new Entry(name, o.optBoolean("enabled", true)));
                }
                return out;
            } catch (JSONException ignored) {
                // fall through to legacy migration
            }
        }
        if (isRealEffect(legacyEffect)) out.add(new Entry(legacyEffect, true));
        return out;
    }

    public static String serialize(List<Entry> entries) {
        JSONArray arr = new JSONArray();
        for (Entry e : entries) {
            if (!isRealEffect(e.name)) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("name", e.name);
                o.put("enabled", e.enabled);
                arr.put(o);
            } catch (JSONException ignored) {}
        }
        return arr.toString();
    }

    // Enforce the solo invariant: at most one entry enabled. Keeps the FIRST enabled one active and
    // bypasses the rest (in stack mode this is a no-op). Cheap guard so a bad save can't light up
    // two effects in solo mode.
    public static void enforceSolo(List<Entry> entries, String mode) {
        if (!MODE_SOLO.equals(normalizeMode(mode))) return;
        boolean seen = false;
        for (Entry e : entries) {
            if (e.enabled) {
                if (seen) e.enabled = false;
                else seen = true;
            }
        }
    }

    // Per-effect uniform overrides. New saves NEST params under the effect name
    // ({"<effect>":{"<uniform>":v}}); legacy saves (no loadout array) keep a FLAT {"<uniform>":v}
    // that belongs to the single legacy effect. [nested] = whether a reshadeLoadout array was present
    // (the discriminator the caller already knows). Returns null when this effect has no overrides.
    public static JSONObject paramsForEffect(String paramsJson, String effectName,
                                             boolean nested, String legacyEffect) {
        if (paramsJson == null || paramsJson.isEmpty() || effectName == null) return null;
        try {
            JSONObject root = new JSONObject(paramsJson);
            if (nested) return root.optJSONObject(effectName);
            // Flat legacy params only ever belonged to the single legacy effect.
            if (effectName.equalsIgnoreCase(legacyEffect)) return root;
            return null;
        } catch (JSONException e) {
            return null;
        }
    }
}
