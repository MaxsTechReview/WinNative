package com.winlator.cmod.runtime.reshade;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.shared.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ReShade drop-in support (vkBasalt-powered). App-side discovery + parameter reflection.
//
// Users drop ReShade effects into one folder, ONE self-contained subfolder per effect (the .fx plus
// any .fxh includes and textures). scanEffects() lists every subfolder that contains a .fx;
// reflectParams() scrapes each .fx's `uniform ... < ui_* > = default;` annotations so the settings UI
// can describe each tunable uniform. This class never touches the layer binary — it only discovers
// effects and describes their parameters; conf generation + env wiring live in ReshadeConfigWriter.
//
// vkBasalt (already bundled in assets/graphics_driver/extra_libs.tzst as usr/lib/libvkbasalt.so, zlib
// licence, with the reshadefx compiler embedded) compiles the .fx -> SPIR-V on-device and applies it
// to DXVK/VKD3D (Vulkan) games. Continuation of DadSchoorse/vkBasalt (original), bundled into the
// Winlator/Cmod lineage by Pipetto-crypto.
//
// NOTE on the SHIPPED (stock) vkBasalt: it reads its config once at swapchain create and does NOT
// support overriding a .fx uniform from config (only the .fx's own defaults) — and its HOME toggle is
// a no-op. So on the stock layer, an effect applies at its DEFAULT look; the per-uniform lines this
// package emits are inert until the live-reload patched libvkbasalt.so lands (follow-up PR). The
// reflection below is kept so that follow-up wires live/pre-launch parameter tuning with no new
// scanning code.
public class ReshadeManager {
    private static final String TAG = "ReshadeManager";

    // User-visible drop-in folder: /sdcard/Android/data/<pkg>/files/ReShade/. Reachable from any file
    // manager without extra storage permissions. Effect files are COPIED into the guest config dir at
    // launch (ReshadeConfigWriter), so the layer never reads from external storage directly.
    public static final String FOLDER_NAME = "ReShade";

    // FLOAT/INT -> slider (ui_type slider|drag). BOOL -> toggle. COMBO -> dropdown (radio/list too;
    // value = selected index). COLOR -> color picker (floatN; value = N components).
    public enum ParamType { FLOAT, INT, BOOL, COMBO, COLOR }

    // One tunable uniform reflected from a .fx. Float/int carry min/max/step; bool ignores them.
    // COMBO carries `options` (the ui_items labels; value is the selected index). COLOR carries
    // `components` (3 for float3, 4 for float4) + `componentDefaults` (per-component .fx default).
    public static class ReshadeParam {
        public final String name;          // the uniform identifier (what we write into vkBasalt.conf)
        public final ParamType type;
        public final float min, max, step;
        public final float defaultValue;   // bool default carried as 0.0/1.0; combo default = index; color = component 0
        public final String label;         // ui_label, else the uniform name
        public final String uiType;        // ui_type ("slider"/"drag"/"combo"/"color"/...), may be ""
        public final List<String> options; // COMBO/RADIO/LIST item labels (else null)
        public final int components;        // COLOR component count (3/4); 1 otherwise
        public final float[] componentDefaults; // COLOR per-component defaults (else null)

        // Scalar/bool params (FLOAT/INT/BOOL): no options, single component.
        public ReshadeParam(String name, ParamType type, float min, float max, float step,
                            float defaultValue, String label, String uiType) {
            this(name, type, min, max, step, defaultValue, label, uiType, null, 1, null);
        }

        public ReshadeParam(String name, ParamType type, float min, float max, float step,
                            float defaultValue, String label, String uiType,
                            List<String> options, int components, float[] componentDefaults) {
            this.name = name;
            this.type = type;
            this.min = min;
            this.max = max;
            this.step = step;
            this.defaultValue = defaultValue;
            this.label = label;
            this.uiType = uiType;
            this.options = options;
            this.components = components;
            this.componentDefaults = componentDefaults;
        }
    }

    // A discovered effect: its subfolder name (the selectable identifier), its folder and .fx, and the
    // reflected param list.
    public static class ReshadeEffect {
        public final String name;
        public final File dir;
        public final File fxFile;
        public final List<ReshadeParam> params;

        public ReshadeEffect(String name, File dir, File fxFile, List<ReshadeParam> params) {
            this.name = name;
            this.dir = dir;
            this.fxFile = fxFile;
            this.params = params;
        }
    }

    public static File getReshadeDir(Context context) {
        File dir = new File(context.getExternalFilesDir(null), FOLDER_NAME);
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    // List every subfolder that contains at least one .fx, sorted by name. Each becomes one selectable
    // effect. A .fx matching the folder name wins as the technique source; otherwise the first .fx.
    public static List<ReshadeEffect> scanEffects(Context context) {
        ArrayList<ReshadeEffect> out = new ArrayList<>();
        File root = getReshadeDir(context);
        File[] subdirs = root.listFiles(File::isDirectory);
        if (subdirs == null) return out;
        for (File dir : subdirs) {
            File fx = findFxFile(dir);
            if (fx == null) continue;
            out.add(new ReshadeEffect(dir.getName(), dir, fx, reflectParams(fx)));
        }
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    // Names only (for dropdowns) — "None" is prepended by callers, not here.
    public static List<String> scanEffectNames(Context context) {
        ArrayList<String> names = new ArrayList<>();
        for (ReshadeEffect e : scanEffects(context)) names.add(e.name);
        return names;
    }

    public static ReshadeEffect findEffect(Context context, String name) {
        if (name == null || name.isEmpty()) return null;
        for (ReshadeEffect e : scanEffects(context)) {
            if (e.name.equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    /** Delete a downloaded effect's entire subfolder from the ReShade dir (recursive). Returns true when
     *  the folder was found and removed. Only ever deletes a directory that sits directly inside
     *  getReshadeDir — a crafted name that would escape the dir (or resolve to the root itself) is refused. */
    public static boolean deleteEffect(Context context, String name) {
        if (name == null || name.isEmpty()) return false;
        File root = getReshadeDir(context);
        ReshadeEffect e = findEffect(context, name);
        File dir = (e != null) ? e.dir : new File(root, name);
        File parent = dir.getParentFile();
        if (parent == null || !parent.equals(root) || dir.equals(root)) return false;
        boolean ok = FileUtils.delete(dir);           // WinNative FileUtils.delete(File) is recursive
        if (!ok) Log.w(TAG, "Failed to delete effect folder: " + dir);
        return ok;
    }

    public static File findFxFile(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        File first = null;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".fx")) {
                if (first == null) first = f;
                // Prefer a .fx whose basename matches the folder (e.g. Sepia/Sepia.fx).
                String base = f.getName().substring(0, f.getName().length() - 3);
                if (base.equalsIgnoreCase(dir.getName())) return f;
            }
        }
        return first;
    }

    // ── Param reflection (lightweight regex scraper, no native call) ─────────────────────────────
    // Matches: uniform <type> <name> < ...annotations... > = <default> ;   (default optional)
    // ReShade annotations don't nest <>, so a non-greedy [^>]* block is sufficient.
    private static final Pattern UNIFORM = Pattern.compile(
            "uniform\\s+(\\w+)\\s+(\\w+)\\s*<([^>]*)>\\s*(?:=\\s*([^;]+?))?\\s*;",
            Pattern.DOTALL);

    public static List<ReshadeParam> reflectParams(File fxFile) {
        ArrayList<ReshadeParam> params = new ArrayList<>();
        String src;
        try {
            src = FileUtils.readString(fxFile);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read .fx for reflection: " + fxFile, e);
            return params;
        }
        if (src == null) return params;

        Matcher m = UNIFORM.matcher(src);
        while (m.find()) {
            String typeStr = m.group(1).toLowerCase(Locale.US);
            String name = m.group(2);
            String ann = m.group(3) != null ? m.group(3) : "";
            String defExpr = m.group(4);

            // Skip engine-driven uniforms: a `source = "..."` annotation marks a ReShade-provided
            // semantic (timer/frametime/framecount/pingpong/random/...) that vkBasalt fills each
            // frame. These are NOT user-tunable — a slider would do nothing and just clutter the list.
            if (annValue(ann, "source") != null) continue;

            // Base scalar type + component count (float3 -> base "float", components 3). Matrices (e.g.
            // float3x3) keep an "x" in baseType and fall through the family check below.
            String baseType = typeStr.replaceAll("[0-9]+$", "");
            int components = parseComponents(typeStr);
            if (!baseType.equals("float") && !baseType.equals("int")
                    && !baseType.equals("uint") && !baseType.equals("bool")) continue;

            String uiType = unquote(annValue(ann, "ui_type"));
            if (uiType == null) uiType = "";
            String uiTypeLc = uiType.toLowerCase(Locale.US);

            String label = unquote(annValue(ann, "ui_label"));
            if (label == null || label.isEmpty()) label = name;

            // ── bool -> toggle ──
            if (baseType.equals("bool")) {
                float def = (defExpr != null && defExpr.trim().equalsIgnoreCase("true")) ? 1f : 0f;
                params.add(new ReshadeParam(name, ParamType.BOOL, 0f, 1f, 1f, def, label, uiType));
                continue;
            }

            // ── combo / radio / list -> dropdown (index-backed) ──
            if (uiTypeLc.equals("combo") || uiTypeLc.equals("radio") || uiTypeLc.equals("list")) {
                List<String> options = parseUiItems(unquote(annValue(ann, "ui_items")));
                if (options.size() >= 2) {
                    int def = Math.round(parseFloat(defExpr, 0f));
                    if (def < 0) def = 0;
                    if (def > options.size() - 1) def = options.size() - 1;
                    params.add(new ReshadeParam(name, ParamType.COMBO, 0f, options.size() - 1f, 1f,
                            def, label, uiType, options, 1, null));
                    continue;
                }
                // No usable ui_items -> fall through to a numeric slider.
            }

            // ── color (floatN) -> color picker ──
            if (uiTypeLc.equals("color") && baseType.equals("float")) {
                int comp = Math.max(1, components);
                float[] defs = parseFloatList(defExpr, comp, 0f);
                params.add(new ReshadeParam(name, ParamType.COLOR, 0f, 1f, 0.01f,
                        defs.length > 0 ? defs[0] : 0f, label, uiType, null, comp, defs));
                continue;
            }

            // ── scalar float/int (ui_type slider | drag) -> slider ──
            // Non-color vectors (e.g. a float2 drag) have no single widget here -> skip, as before.
            if (components != 1) continue;
            ParamType type = baseType.equals("float") ? ParamType.FLOAT : ParamType.INT;

            float min = parseFloat(annValue(ann, "ui_min"), 0f);
            float max = parseFloat(annValue(ann, "ui_max"), type == ParamType.INT ? 100f : 1f);
            float step = parseFloat(annValue(ann, "ui_step"),
                    type == ParamType.INT ? 1f : (Math.max(0.0001f, (max - min) / 100f)));
            if (max <= min) max = min + 1f;

            float def = parseFloat(defExpr, min);
            if (def < min) def = min;
            if (def > max) def = max;

            params.add(new ReshadeParam(name, type, min, max, step, def, label, uiType));
        }
        return params;
    }

    // Value-map key scheme, shared by the settings UI and the conf writer. COLOR seeds one entry per
    // component under "<name>_<c>"; everything else seeds a single "<name>" entry. Saved JSON
    // (per-game/container override) wins over the .fx default.
    public static void seedValues(ReshadeParam p, org.json.JSONObject saved, Map<String, Float> out) {
        if (p.type == ParamType.COLOR) {
            for (int c = 0; c < p.components; c++) {
                String k = p.name + "_" + c;
                float def = (p.componentDefaults != null && c < p.componentDefaults.length)
                        ? p.componentDefaults[c] : 0f;
                float v = def;
                if (saved != null && saved.has(k)) v = (float) saved.optDouble(k, def);
                out.put(k, v);
            }
        } else {
            float v = p.defaultValue;
            if (saved != null && saved.has(p.name)) v = (float) saved.optDouble(p.name, v);
            out.put(p.name, v);
        }
    }

    // Trailing component count of a vector type (float3 -> 3, float -> 1), clamped to 1..4.
    private static int parseComponents(String typeStr) {
        Matcher m = Pattern.compile("([0-9]+)$").matcher(typeStr);
        if (m.find()) {
            try {
                int n = Integer.parseInt(m.group(1));
                if (n >= 1 && n <= 4) return n;
            } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    // Split a ui_items string ("A\0B\0C\0") on the literal "\0" escape (two chars: backslash + '0', how
    // the .fx source carries them) or a real NUL, dropping the terminating trailing empty.
    private static List<String> parseUiItems(String raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw == null) return out;
        String[] parts = raw.split("\\\\0|\\x00", -1);
        Collections.addAll(out, parts);
        while (!out.isEmpty() && out.get(out.size() - 1).trim().isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    // Parse up to [count] leading floats from a constructor/initializer ("float3(0.5, 0.2, 0.1)",
    // "{1.0, 0.0, 0.0}", or a scalar "0.5" which is broadcast to all components). A single leading
    // "floatN"/"intN" constructor name is skipped so its digit isn't read as a component value.
    private static float[] parseFloatList(String s, int count, float fallback) {
        float[] out = new float[count];
        Arrays.fill(out, fallback);
        if (s == null) return out;
        String body = s;
        int paren = s.indexOf('(');
        if (paren >= 0) body = s.substring(paren); // drop a "floatN(" / "intN(" constructor name
        Matcher m = Pattern.compile("[-+]?[0-9]*\\.?[0-9]+").matcher(body);
        ArrayList<Float> nums = new ArrayList<>();
        while (m.find() && nums.size() < count) {
            try { nums.add(Float.parseFloat(m.group())); } catch (NumberFormatException ignored) {}
        }
        if (nums.size() == 1) { Arrays.fill(out, nums.get(0)); return out; } // float3(0.5) broadcast
        for (int i = 0; i < nums.size(); i++) out[i] = nums.get(i);
        return out;
    }

    // Pull `key = value` from an annotation block; value runs to the next `;` or end. Returns the raw
    // (possibly quoted) value, or null when absent.
    private static String annValue(String ann, String key) {
        Pattern p = Pattern.compile("\\b" + Pattern.quote(key) + "\\s*=\\s*([^;]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(ann);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    private static String unquote(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s;
    }

    // Parse the leading float out of an expression (handles "0.5", "float(0.5)", "1.0f", "{0.5, ...}"
    // by taking the first number). Falls back to [fallback] on anything unparseable.
    private static float parseFloat(String s, float fallback) {
        if (s == null) return fallback;
        Matcher m = Pattern.compile("[-+]?[0-9]*\\.?[0-9]+").matcher(s);
        if (m.find()) {
            try {
                return Float.parseFloat(m.group());
            } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }
}
