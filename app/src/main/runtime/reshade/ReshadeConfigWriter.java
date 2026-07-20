package com.winlator.cmod.runtime.reshade;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.runtime.display.environment.ImageFs;
import com.winlator.cmod.runtime.wine.EnvVars;
import com.winlator.cmod.shared.io.FileUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Launch-time wiring for the ReShade drop-in feature. Given the effect selected for this game (a
// subfolder name under the ReShade/ drop-in folder), this:
//   1. copies the effect's self-contained folder (.fx + .fxh + textures) into the guest config dir,
//   2. writes a vkBasalt.conf next to it, and
//   3. sets ENABLE_VKBASALT=1 + VKBASALT_CONFIG_FILE so the already-bundled vkBasalt implicit layer
//      (usr/share/vulkan/implicit_layer.d/vkBasalt.json, enable_env ENABLE_VKBASALT=1) picks it up.
//
// vkBasalt is a guest-side Vulkan instance layer that hooks the game's swapchain, so it only affects
// Vulkan-backed titles (DXVK/VKD3D) — WineD3D/GL/GDI/software titles carry no layer. The caller gates
// on the DX wrapper before calling apply().
//
// Paths written into the conf are HOST-ABSOLUTE under ImageFs.home_path, matching how this project
// already sets HOME/WINEPREFIX for the guest (GuestProgramLauncherComponent / XServerDisplayActivity).
//
// STOCK-LAYER SCOPE: the libvkbasalt.so currently bundled reads its config ONCE at swapchain create,
// has no path to override a .fx uniform from config, and its HOME toggle is a no-op. So on the stock
// layer an effect applies at its .fx DEFAULT look; the per-uniform lines below are inert. They are
// emitted in the live-reload key scheme ("<effectKey>_<uniform>") so the follow-up PR that swaps in
// the patched libvkbasalt.so enables pre-launch + live tuning with no change here.
public class ReshadeConfigWriter {
    private static final String TAG = "ReshadeConfigWriter";

    // Persisted on the Container / Shortcut as plain string extras.
    //   EXTRA_LOADOUT  reshadeLoadout — JSON array [{"name":..,"enabled":..}, ..] (the ordered chain).
    //   EXTRA_MODE     reshadeMode    — "solo" | "stack".
    //   EXTRA_PARAMS   reshadeParams  — nested {"<effect>":{"<uniform>":v}} overrides (flat for legacy).
    //   EXTRA_MASTER   reshadeMasterEnabled — "0" when the in-game master switch was turned off; else on.
    //   EXTRA_EFFECT   reshadeEffect  — LEGACY single-effect folder name; still read for back-compat and
    //                                   kept roughly coherent (= first loadout effect) on save.
    public static final String EXTRA_EFFECT = "reshadeEffect";
    public static final String EXTRA_PARAMS = "reshadeParams";
    public static final String EXTRA_LOADOUT = "reshadeLoadout";
    public static final String EXTRA_MODE = "reshadeMode";
    public static final String EXTRA_MASTER = "reshadeMasterEnabled";

    // vkBasalt applies to Vulkan-backed titles only. dxvk covers D3D9/10/11; vkd3d covers D3D12.
    public static boolean supportedFor(String dxwrapper) {
        if (dxwrapper == null) return false;
        String w = dxwrapper.toLowerCase(Locale.US);
        return w.contains("dxvk") || w.contains("vkd3d") || w.contains("d8vk");
    }

    // LEGACY single-effect launch path — SUPERSEDED by applyLoadout()/writeMergedConfig() below, which
    // the activity now uses for both launch and live. Retained (no callers) only so an external caller of
    // the old single-effect API still compiles; safe to delete once none remain.
    // Writes the conf + effect files and injects env. Returns true when a ReShade effect was applied
    // (env mutated), false otherwise (no env change — caller's launch is untouched).
    public static boolean apply(Context context, ImageFs imageFs, String effectName,
                                String paramsJson, boolean vulkanWrapper, EnvVars envVars) {
        if (!vulkanWrapper) return false;
        if (effectName == null || effectName.trim().isEmpty()) return false;

        // Respect a user override: if custom env / launch options already set ENABLE_VKBASALT (e.g. to
        // force the layer off, or drive vkBasalt by hand), leave the whole ReShade env untouched.
        if (envVars.has("ENABLE_VKBASALT")) {
            Log.i(TAG, "ENABLE_VKBASALT already set by user env; leaving ReShade selection untouched");
            return false;
        }

        ReshadeManager.ReshadeEffect effect = ReshadeManager.findEffect(context, effectName);
        if (effect == null) {
            Log.w(TAG, "Selected ReShade effect not found in drop-in folder: " + effectName);
            return false;
        }

        // Guest config dir: <home>/.config/vkBasalt/ (host-absolute, same base as HOME).
        File vkBasaltDir = new File(imageFs.home_path, ".config/vkBasalt");
        File effectsDir = new File(vkBasaltDir, "effects");
        File destEffectDir = new File(effectsDir, effect.name);
        FileUtils.clear(destEffectDir); // fresh copy each launch (drop-in folder is the source of truth)
        if (!copyTree(effect.dir, destEffectDir)) {
            Log.e(TAG, "Failed to stage ReShade effect files for: " + effect.name);
            return false;
        }

        File destFx = new File(destEffectDir, effect.fxFile.getName());
        String effectKey = sanitizeKey(effect.name);
        JSONObject saved = parseJson(paramsJson);

        File conf = new File(vkBasaltDir, "vkBasalt.conf");
        if (!FileUtils.writeString(conf, buildConfString(destFx, destEffectDir, effectKey, effect.params, saved))) {
            Log.e(TAG, "Failed to write vkBasalt.conf");
            return false;
        }

        envVars.put("ENABLE_VKBASALT", "1");
        envVars.put("VKBASALT_CONFIG_FILE", conf.getAbsolutePath());
        Log.i(TAG, "ReShade effect '" + effect.name + "' -> " + conf.getAbsolutePath()
                + " (" + effect.params.size() + " params reflected)");
        return true;
    }

    // Host-absolute vkBasalt.conf path for a prefix — the SAME file apply() writes at launch and the
    // patched libvkbasalt.so watches for mtime changes. Exposed so the in-game live pane rewrites it.
    public static File confFile(ImageFs imageFs) {
        return new File(new File(imageFs.home_path, ".config/vkBasalt"), "vkBasalt.conf");
    }

    // ── Multi-effect loadout ─────────────────────────────────────────────────────────────────────
    // A loadout compiles EVERY listed effect into the vkBasalt chain up front (effects = e1:e2:..); the
    // per-effect `<ei>_enabled = 0|1` flag decides which of them present. The patched libvkbasalt.so
    // watches this conf's mtime and re-reads enableOnLaunch (whole-chain passthrough) + each `_enabled`
    // flag + the uniform values WITHOUT recompiling, so the in-game drawer flips effects on/off (solo
    // switch / stack layering) LIVE. This is the fix for the single-effect drawer's failed live switch:
    // an effect can only be toggled live if it was compiled at launch.

    // Launch-time entry point. Stages every loadout effect, writes the merged conf, and sets the enabling
    // env. Returns true when the layer was armed (env mutated), false otherwise (launch untouched).
    public static boolean applyLoadout(Context context, ImageFs imageFs, List<ReshadeLoadout.Entry> loadout,
                                       String paramsJson, boolean nested, String legacyEffect,
                                       boolean masterEnabled, boolean vulkanWrapper, EnvVars envVars) {
        if (!vulkanWrapper) return false;
        if (loadout == null || loadout.isEmpty()) return false;

        // Respect a user override: if custom env / launch options already set ENABLE_VKBASALT, leave the
        // whole ReShade env untouched (user drives vkBasalt by hand).
        if (envVars.has("ENABLE_VKBASALT")) {
            Log.i(TAG, "ENABLE_VKBASALT already set by user env; leaving ReShade loadout untouched");
            return false;
        }

        if (!writeMergedConfig(context, imageFs, loadout, paramsJson, nested, legacyEffect, masterEnabled, true)) {
            return false; // no effect could be staged (all missing) -> nothing to arm
        }

        File conf = confFile(imageFs);
        envVars.put("ENABLE_VKBASALT", "1");
        envVars.put("VKBASALT_CONFIG_FILE", conf.getAbsolutePath());
        Log.i(TAG, "ReShade loadout (" + loadout.size() + " effect(s)) -> " + conf.getAbsolutePath());
        return true;
    }

    // Write the merged vkBasalt.conf for a loadout. Shared by the launch path (restage=true, re-copies
    // each effect's drop-in folder) and the in-game live path (restage=false, folders already staged —
    // rewrite + mtime bump only). Always bumps the mtime so an identical-bytes rewrite still trips the
    // layer's watcher. Returns true when at least one effect was staged into the chain. Fully swallowed.
    public static boolean writeMergedConfig(Context context, ImageFs imageFs, List<ReshadeLoadout.Entry> loadout,
                                            String paramsJson, boolean nested, String legacyEffect,
                                            boolean masterEnabled, boolean restage) {
        try {
            if (context == null || imageFs == null || loadout == null) return false;
            File vkBasaltDir = new File(imageFs.home_path, ".config/vkBasalt");
            if (!vkBasaltDir.isDirectory() && !vkBasaltDir.mkdirs()) return false;
            File effectsRoot = new File(vkBasaltDir, "effects");

            StringBuilder chain = new StringBuilder();       // e1:e2:...:en
            StringBuilder effectLines = new StringBuilder();  // per-effect: <ei> = fx + uniforms + _enabled
            List<String> stagedDirs = new ArrayList<>();
            int idx = 0;

            for (ReshadeLoadout.Entry entry : loadout) {
                // Defensive hard cap: never compile more than MAX_EFFECTS even if a bad save carries more.
                if (idx >= ReshadeLoadout.MAX_EFFECTS) {
                    Log.w(TAG, "ReShade loadout exceeds " + ReshadeLoadout.MAX_EFFECTS
                            + " effects; truncating the rest");
                    break;
                }
                ReshadeManager.ReshadeEffect effect = ReshadeManager.findEffect(context, entry.name);
                if (effect == null) {
                    // Skip-and-continue: one missing effect must not kill the rest of the chain.
                    Log.w(TAG, "ReShade loadout effect not found, skipping: " + entry.name);
                    continue;
                }

                String effectKey = sanitizeKey(effect.name);
                if (effectKey.equals("reshade")) effectKey = "reshade" + idx; // keep keys distinct

                File destDir = new File(effectsRoot, effect.name);
                File destFx = new File(destDir, effect.fxFile.getName());
                if (restage || !destFx.isFile()) {
                    FileUtils.clear(destDir);
                    if (!copyTree(effect.dir, destDir)) {
                        Log.e(TAG, "Failed to stage ReShade effect folder: " + effect.dir);
                        continue;
                    }
                }
                stagedDirs.add(destDir.getAbsolutePath()); // host-absolute

                if (chain.length() > 0) chain.append(":");
                chain.append(effectKey);
                effectLines.append(effectKey).append(" = ").append(destFx.getAbsolutePath()).append("\n");

                // Per-uniform overrides for THIS effect (nested {"<effect>":{...}}, or migrated flat
                // legacy), layered over the .fx defaults, emitted in the "<effectKey>_<uniform>" scheme.
                JSONObject paramJson =
                        ReshadeLoadout.paramsForEffect(paramsJson, effect.name, nested, legacyEffect);
                Map<String, Float> values = new LinkedHashMap<>();
                for (ReshadeManager.ReshadeParam p : effect.params) ReshadeManager.seedValues(p, paramJson, values);
                for (ReshadeManager.ReshadeParam p : effect.params) appendUniformLines(effectLines, effectKey, p, values);

                // Per-effect enable gate the patched layer reads (1 = active, 0 = bypassed).
                effectLines.append(effectKey).append("_enabled = ").append(entry.enabled ? "1" : "0").append("\n");
                idx++;
            }

            if (chain.length() == 0) {
                Log.w(TAG, "No ReShade loadout effects could be staged; skipping conf");
                return false;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# Generated by WinNative — ReShade loadout (do not edit; regenerated on each change)\n");
            sb.append("effects = ").append(chain).append("\n");
            sb.append(effectLines);

            // Texture/include search paths: colon-join every staged effect dir (vkBasalt splits these on
            // ':' the same way it splits the effects list — the verified way to resolve multiple effects;
            // each effect keeps its OWN staged dir). A single-effect loadout collapses to one path.
            String pathList = android.text.TextUtils.join(":", stagedDirs);
            sb.append("reshadeTexturePath = ").append(pathList).append("\n");
            sb.append("reshadeIncludePath = ").append(pathList).append("\n");
            sb.append("depthCapture = off\n");
            sb.append("toggleKey = Home\n");
            sb.append("enableOnLaunch = ").append(masterEnabled ? "True" : "False").append("\n");

            File conf = new File(vkBasaltDir, "vkBasalt.conf");
            boolean ok = FileUtils.writeString(conf, sb.toString());
            if (ok) {
                conf.setLastModified(System.currentTimeMillis());
                Log.d(TAG, "Wrote ReShade loadout conf (" + chain + ") -> " + conf.getAbsolutePath());
            }
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "writeMergedConfig failed (ignored)", e);
            return false;
        }
    }

    // Live in-game rewrite of the running prefix's vkBasalt.conf so the PATCHED libvkbasalt.so picks up
    // the change on its next rendered frame (Part A mtime toggle / Part B UBO uniform overrides). Does
    // NOT touch env — ENABLE_VKBASALT/VKBASALT_CONFIG_FILE were already set at launch. Fully swallowed:
    // returns false on any failure so a live edit can never crash the session.
    //   enabled=false (or blank effect) -> writes a conf with no active effect, disabling the layer live.
    //   restage -> re-copies the effect's drop-in files (needed only when SWITCHING to an effect that
    //              wasn't staged at launch); plain param/toggle changes pass false and reuse the staged
    //              copy. We always bump the file mtime (setLastModified) so an identical-bytes rewrite
    //              still trips the layer's watcher.
    public static boolean writeLiveConfig(Context context, ImageFs imageFs, String effectName,
                                          String paramsJson, boolean enabled, boolean restage) {
        try {
            if (context == null || imageFs == null) return false;
            File vkBasaltDir = new File(imageFs.home_path, ".config/vkBasalt");
            if (!vkBasaltDir.isDirectory() && !vkBasaltDir.mkdirs()) return false;
            File conf = new File(vkBasaltDir, "vkBasalt.conf");

            if (!enabled || effectName == null || effectName.trim().isEmpty()) {
                // Off: no active effect. The patched layer reads `effects =` empty and drops all passes
                // on its next frame (Part A). We leave the effect files in place for a quick re-enable.
                String off = "# Generated by WinNative — ReShade live control (disabled)\n"
                        + "effects = \n"
                        + "enableOnLaunch = False\n"
                        + "toggleKey = Home\n";
                boolean ok = FileUtils.writeString(conf, off);
                if (ok) conf.setLastModified(System.currentTimeMillis());
                return ok;
            }

            ReshadeManager.ReshadeEffect effect = ReshadeManager.findEffect(context, effectName);
            if (effect == null) {
                Log.w(TAG, "Live ReShade effect not found: " + effectName);
                return false;
            }

            File destEffectDir = new File(new File(vkBasaltDir, "effects"), effect.name);
            File destFx = new File(destEffectDir, effect.fxFile.getName());
            if (restage || !destFx.isFile()) {
                FileUtils.clear(destEffectDir);
                if (!copyTree(effect.dir, destEffectDir)) {
                    Log.e(TAG, "Failed to (re)stage ReShade effect files for: " + effect.name);
                    return false;
                }
            }

            String effectKey = sanitizeKey(effect.name);
            JSONObject saved = parseJson(paramsJson);
            boolean ok = FileUtils.writeString(conf,
                    buildConfString(destFx, destEffectDir, effectKey, effect.params, saved));
            if (ok) conf.setLastModified(System.currentTimeMillis());
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "writeLiveConfig failed (ignored)", e);
            return false;
        }
    }

    // Assemble the full vkBasalt.conf body for one effect (shared by the launch and live-rewrite paths).
    // [saved] is the per-game/session param override JSON (null -> .fx defaults). Emits per-uniform
    // lines in the patched-layer key scheme "<effectKey>_<uniform>".
    private static String buildConfString(File destFx, File destEffectDir, String effectKey,
                                          java.util.List<ReshadeManager.ReshadeParam> params, JSONObject saved) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated by WinNative — ReShade drop-in (do not edit; regenerated on each change)\n");
        sb.append("effects = ").append(effectKey).append("\n");
        sb.append(effectKey).append(" = ").append(destFx.getAbsolutePath()).append("\n");
        sb.append("reshadeTexturePath = ").append(destEffectDir.getAbsolutePath()).append("\n");
        sb.append("reshadeIncludePath = ").append(destEffectDir.getAbsolutePath()).append("\n");
        sb.append("depthCapture = off\n");   // depth effects (SSAO/DOF) are a later step
        sb.append("toggleKey = Home\n");
        sb.append("enableOnLaunch = True\n");

        Map<String, Float> values = new LinkedHashMap<>();
        for (ReshadeManager.ReshadeParam p : params) ReshadeManager.seedValues(p, saved, values);
        for (ReshadeManager.ReshadeParam p : params) appendUniformLines(sb, effectKey, p, values);
        return sb.toString();
    }

    // effectKey used both in `effects = <key>` and as the `<key> = <fxPath>` mapping. Must match the
    // patched layer's config lookup, so keep it stable: [^A-Za-z0-9_] -> _, lowercased.
    private static String sanitizeKey(String name) {
        String k = name.replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(Locale.US);
        return k.isEmpty() ? "reshade" : k;
    }

    // Emit conf lines for one param in the patched-layer key scheme "<effectKey>_<uniform>"
    // (vectors get one line per component with a "_<c>" suffix).
    private static void appendUniformLines(StringBuilder sb, String effectKey,
                                           ReshadeManager.ReshadeParam p, Map<String, Float> values) {
        switch (p.type) {
            case COLOR:
                for (int c = 0; c < p.components; c++) {
                    Float v = values.get(p.name + "_" + c);
                    if (v == null) continue;
                    sb.append(effectKey).append('_').append(p.name).append('_').append(c)
                      .append(" = ").append(fmt(v)).append('\n');
                }
                break;
            case BOOL:
            case COMBO:
            case INT: {
                Float v = values.get(p.name);
                if (v != null) {
                    sb.append(effectKey).append('_').append(p.name)
                      .append(" = ").append(Math.round(v)).append('\n');
                }
                break;
            }
            case FLOAT:
            default: {
                Float v = values.get(p.name);
                if (v != null) {
                    sb.append(effectKey).append('_').append(p.name)
                      .append(" = ").append(fmt(v)).append('\n');
                }
                break;
            }
        }
    }

    private static String fmt(float v) {
        if (v == Math.rint(v) && !Float.isInfinite(v) && Math.abs(v) < 1e9f) return String.valueOf((int) v);
        // Plain decimal (no scientific notation, e.g. "0.0001" not "1.0E-4"), locale-independent.
        return new java.math.BigDecimal(Float.toString(v)).toPlainString();
    }

    private static JSONObject parseJson(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try { return new JSONObject(s); } catch (Exception e) { return null; }
    }

    // Recursive copy of the effect subfolder (small: a .fx plus a few .fxh/textures).
    private static boolean copyTree(File src, File dst) {
        if (src.isDirectory()) {
            if (!dst.isDirectory() && !dst.mkdirs()) return false;
            File[] kids = src.listFiles();
            if (kids == null) return true;
            boolean ok = true;
            for (File k : kids) ok &= copyTree(k, new File(dst, k.getName()));
            return ok;
        }
        File parent = dst.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        return FileUtils.copy(src, dst);
    }
}
