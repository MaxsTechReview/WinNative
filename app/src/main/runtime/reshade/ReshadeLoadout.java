package com.winlator.cmod.runtime.reshade;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// ordered effect chain; solo = one enabled at a time, stack = any subset. legacy saves (no reshadeLoadout array) migrate from reshadeEffect + flat reshadeParams.
public class ReshadeLoadout {
    public static final String MODE_SOLO = "solo";
    public static final String MODE_STACK = "stack";

    // every entry is shader-compiled on-device at launch, so the chain is capped
    public static final int MAX_EFFECTS = 6;

    public static class Entry {
        public final String name;
        public boolean enabled;

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

    // legacy reshadeEffect becomes a one-entry loadout when the array is absent/blank/unparseable
    public static List<Entry> parse(String loadoutJson, String legacyEffect) {
        ArrayList<Entry> out = new ArrayList<>();
        if (loadoutJson != null && !loadoutJson.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(loadoutJson);
                for (int i = 0; i < arr.length() && out.size() < MAX_EFFECTS; i++) {
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

    // solo invariant: keeps the first enabled entry, bypasses the rest
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

    // nested = params keyed by effect name; flat params belong solely to the legacy effect
    public static JSONObject paramsForEffect(String paramsJson, String effectName,
                                             boolean nested, String legacyEffect) {
        if (paramsJson == null || paramsJson.isEmpty() || effectName == null) return null;
        try {
            JSONObject root = new JSONObject(paramsJson);
            if (nested) return root.optJSONObject(effectName);
            if (effectName.equalsIgnoreCase(legacyEffect)) return root;
            return null;
        } catch (JSONException e) {
            return null;
        }
    }
}
