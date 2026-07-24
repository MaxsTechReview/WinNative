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

// Stages effect folders under ImageFs.home_path, writes vkBasalt.conf (host-absolute paths), sets ENABLE_VKBASALT/VKBASALT_CONFIG_FILE.
public class ReshadeConfigWriter {
    private static final String TAG = "ReshadeConfigWriter";

    // Container/Shortcut string extras: loadout [{"name":..,"enabled":..}], params {"<effect>":{"<uniform>":v}} (flat when legacy), master "0"=off, effect = legacy single name.
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

    // Legacy single-effect path, superseded by applyLoadout(); no in-tree callers. True = env mutated.
    public static boolean apply(Context context, ImageFs imageFs, String effectName,
                                String paramsJson, boolean vulkanWrapper, EnvVars envVars) {
        if (!vulkanWrapper) return false;
        if (effectName == null || effectName.trim().isEmpty()) return false;

        // user env already sets ENABLE_VKBASALT -> they drive vkBasalt by hand, leave env untouched.
        if (envVars.has("ENABLE_VKBASALT")) {
            Log.i(TAG, "ENABLE_VKBASALT already set by user env; leaving ReShade selection untouched");
            return false;
        }

        ReshadeManager.ReshadeEffect effect = ReshadeManager.findEffect(context, effectName);
        if (effect == null) {
            Log.w(TAG, "Selected ReShade effect not found in drop-in folder: " + effectName);
            return false;
        }

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
        if (!writeConf(conf, buildConfString(destFx, destEffectDir, effectKey, effect.params, saved))) {
            Log.e(TAG, "Failed to write vkBasalt.conf");
            return false;
        }

        envVars.put("ENABLE_VKBASALT", "1");
        envVars.put("VKBASALT_CONFIG_FILE", conf.getAbsolutePath());
        Log.i(TAG, "ReShade effect '" + effect.name + "' -> " + conf.getAbsolutePath()
                + " (" + effect.params.size() + " params reflected)");
        return true;
    }

    // Same conf the layer watches for mtime changes; the in-game live pane rewrites it.
    public static File confFile(ImageFs imageFs) {
        return new File(new File(imageFs.home_path, ".config/vkBasalt"), "vkBasalt.conf");
    }

    // Whole loadout is compiled into the chain up front: an effect can only be toggled live (`<key>_enabled`) if it was compiled at launch.
    public static boolean applyLoadout(Context context, ImageFs imageFs, List<ReshadeLoadout.Entry> loadout,
                                       String paramsJson, boolean nested, String legacyEffect,
                                       boolean masterEnabled, boolean vulkanWrapper, EnvVars envVars) {
        if (!vulkanWrapper) return false;
        if (loadout == null || loadout.isEmpty()) return false;

        // user env already sets ENABLE_VKBASALT -> they drive vkBasalt by hand, leave env untouched.
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

    // restage=true re-copies each effect folder (launch), false reuses staged copies (live). mtime is always bumped so an identical-bytes rewrite still trips the layer's watcher.
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
            java.util.Set<String> usedKeys = new java.util.HashSet<>();
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
                if (usedKeys.contains(effectKey)) effectKey = effectKey + "_" + idx;
                usedKeys.add(effectKey);

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

                // per-uniform overrides layered over the .fx defaults, keyed "<effectKey>_<uniform>".
                JSONObject paramJson =
                        ReshadeLoadout.paramsForEffect(paramsJson, effect.name, nested, legacyEffect);
                Map<String, Float> values = new LinkedHashMap<>();
                for (ReshadeManager.ReshadeParam p : effect.params) ReshadeManager.seedValues(p, paramJson, values);
                for (ReshadeManager.ReshadeParam p : effect.params) appendUniformLines(effectLines, effectKey, p, values);

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

            // vkBasalt splits these paths on ':' like the effects list, so each effect keeps its own staged dir.
            String pathList = android.text.TextUtils.join(":", stagedDirs);
            sb.append("reshadeTexturePath = ").append(pathList).append("\n");
            sb.append("reshadeIncludePath = ").append(pathList).append("\n");
            sb.append("depthCapture = off\n");
            sb.append("toggleKey = Home\n");
            sb.append("enableOnLaunch = ").append(masterEnabled ? "True" : "False").append("\n");

            File conf = new File(vkBasaltDir, "vkBasalt.conf");
            boolean ok = writeConf(conf, sb.toString());
            if (ok) Log.d(TAG, "Wrote ReShade loadout conf (" + chain + ") -> " + conf.getAbsolutePath());
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "writeMergedConfig failed (ignored)", e);
            return false;
        }
    }

    // Live rewrite of the running prefix's conf; env is untouched (set at launch), failures swallowed. restage only when switching to an effect not staged at launch.
    public static boolean writeLiveConfig(Context context, ImageFs imageFs, String effectName,
                                          String paramsJson, boolean enabled, boolean restage) {
        try {
            if (context == null || imageFs == null) return false;
            File vkBasaltDir = new File(imageFs.home_path, ".config/vkBasalt");
            if (!vkBasaltDir.isDirectory() && !vkBasaltDir.mkdirs()) return false;
            File conf = new File(vkBasaltDir, "vkBasalt.conf");

            if (!enabled || effectName == null || effectName.trim().isEmpty()) {
                // empty `effects =` drops all passes next frame; staged files stay for a quick re-enable.
                String off = "# Generated by WinNative — ReShade live control (disabled)\n"
                        + "effects = \n"
                        + "enableOnLaunch = False\n"
                        + "toggleKey = Home\n";
                return writeConf(conf, off);
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
            return writeConf(conf, buildConfString(destFx, destEffectDir, effectKey, effect.params, saved));
        } catch (Exception e) {
            Log.e(TAG, "writeLiveConfig failed (ignored)", e);
            return false;
        }
    }

    // saved = param override JSON, null -> .fx defaults.
    private static String buildConfString(File destFx, File destEffectDir, String effectKey,
                                          java.util.List<ReshadeManager.ReshadeParam> params, JSONObject saved) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated by WinNative — ReShade drop-in (do not edit; regenerated on each change)\n");
        sb.append("effects = ").append(effectKey).append("\n");
        sb.append(effectKey).append(" = ").append(destFx.getAbsolutePath()).append("\n");
        sb.append("reshadeTexturePath = ").append(destEffectDir.getAbsolutePath()).append("\n");
        sb.append("reshadeIncludePath = ").append(destEffectDir.getAbsolutePath()).append("\n");
        sb.append("depthCapture = off\n");
        sb.append("toggleKey = Home\n");
        sb.append("enableOnLaunch = True\n");

        Map<String, Float> values = new LinkedHashMap<>();
        for (ReshadeManager.ReshadeParam p : params) ReshadeManager.seedValues(p, saved, values);
        for (ReshadeManager.ReshadeParam p : params) appendUniformLines(sb, effectKey, p, values);
        return sb.toString();
    }

    private static boolean writeConf(File conf, String data) {
        File tmp = new File(conf.getParentFile(), conf.getName() + ".tmp");
        if (!FileUtils.writeString(tmp, data)) {
            tmp.delete();
            return false;
        }
        tmp.setLastModified(System.currentTimeMillis());
        if (!tmp.renameTo(conf)) {
            tmp.delete();
            Log.e(TAG, "Failed to replace " + conf.getAbsolutePath());
            return false;
        }
        return true;
    }

    private static final java.util.Set<String> BUILTIN_KEYS = new java.util.HashSet<>(
            java.util.Arrays.asList("cas", "fxaa", "smaa", "deband", "dls", "lut"));

    // must stay stable — the layer's config lookup keys off this.
    private static String sanitizeKey(String name) {
        String k = name.replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(Locale.US);
        if (k.isEmpty()) return "reshade";
        return BUILTIN_KEYS.contains(k) ? k + "_fx" : k;
    }

    // "<effectKey>_<uniform>"; vectors get one line per component with a "_<c>" suffix.
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
