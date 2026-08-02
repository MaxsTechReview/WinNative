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

// ReShade effect discovery + .fx uniform reflection; conf generation lives in ReshadeConfigWriter.
public class ReshadeManager {
    private static final String TAG = "ReshadeManager";

    // drop-in folder: /sdcard/Android/data/<pkg>/files/ReShade/, one subfolder per effect
    public static final String FOLDER_NAME = "ReShade";

    public enum ParamType { FLOAT, INT, BOOL, COMBO, COLOR }

    // one tunable uniform reflected from a .fx; COMBO value is an index into options
    public static class ReshadeParam {
        public final String name;
        public final ParamType type;
        public final float min, max, step;
        public final float defaultValue;   // bool default carried as 0.0/1.0; combo default = index; color = component 0
        public final String label;         // ui_label, else the uniform name
        public final String uiType;        // ui_type ("slider"/"drag"/"combo"/"color"/...), may be ""
        public final List<String> options; // COMBO/RADIO/LIST item labels (else null)
        public final int components;        // COLOR component count (3/4); 1 otherwise
        public final float[] componentDefaults; // COLOR per-component defaults (else null)

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

    // every subfolder holding at least one .fx becomes one selectable effect
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

    /** Recursive delete of one effect subfolder; refuses anything not directly inside getReshadeDir. */
    public static boolean deleteEffect(Context context, String name) {
        if (name == null || name.isEmpty()) return false;
        File root = getReshadeDir(context);
        ReshadeEffect e = findEffect(context, name);
        File dir = (e != null) ? e.dir : new File(root, name);
        File parent = dir.getParentFile();
        if (parent == null || !parent.equals(root) || dir.equals(root)) return false;
        boolean ok = FileUtils.delete(dir);           // recursive
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
                String base = f.getName().substring(0, f.getName().length() - 3);
                if (base.equalsIgnoreCase(dir.getName())) return f;
            }
        }
        return first;
    }

    // uniform <type> <name> < annotations > = <default>; — annotations never nest <>, so [^>]* suffices
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
        src = src.replaceAll("(?s)/\\*.*?\\*/", " ");

        Matcher m = UNIFORM.matcher(src);
        while (m.find()) {
            String typeStr = m.group(1).toLowerCase(Locale.US);
            String name = m.group(2);
            String ann = m.group(3) != null ? m.group(3) : "";
            String defExpr = m.group(4);

            // a `source =` annotation marks an engine-filled semantic, not user-tunable
            if (annValue(ann, "source") != null) continue;

            // matrices (float3x3) keep an "x" in baseType and get dropped by the family check
            String baseType = typeStr.replaceAll("[0-9]+$", "");
            int components = parseComponents(typeStr);
            if (!baseType.equals("float") && !baseType.equals("int")
                    && !baseType.equals("uint") && !baseType.equals("bool")) continue;

            String uiType = unquote(annValue(ann, "ui_type"));
            if (uiType == null) uiType = "";
            String uiTypeLc = uiType.toLowerCase(Locale.US);

            String label = unquote(annValue(ann, "ui_label"));
            if (label == null || label.isEmpty()) label = name;

            if (baseType.equals("bool")) {
                float def = (defExpr != null && defExpr.trim().equalsIgnoreCase("true")) ? 1f : 0f;
                params.add(new ReshadeParam(name, ParamType.BOOL, 0f, 1f, 1f, def, label, uiType));
                continue;
            }

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
                // no usable ui_items -> fall through to a numeric slider
            }

            if (uiTypeLc.equals("color") && baseType.equals("float") && components > 1) {
                float[] defs = parseFloatList(defExpr, components, 0f);
                params.add(new ReshadeParam(name, ParamType.COLOR, 0f, 1f, 0.01f,
                        defs.length > 0 ? defs[0] : 0f, label, uiType, null, components, defs));
                continue;
            }

            // non-color vectors have no single widget -> skip
            if (components != 1) continue;
            ParamType type = baseType.equals("float") ? ParamType.FLOAT : ParamType.INT;

            boolean hasMin = annValue(ann, "ui_min") != null;
            boolean hasMax = annValue(ann, "ui_max") != null;
            float min = parseFloat(annValue(ann, "ui_min"), 0f);
            float max = parseFloat(annValue(ann, "ui_max"), type == ParamType.INT ? 100f : 1f);
            float def = parseFloat(defExpr, min);
            if (!hasMin && def < min) min = def;
            if (!hasMax && def > max) max = def;
            if (max <= min) max = min + 1f;
            float step = parseFloat(annValue(ann, "ui_step"),
                    type == ParamType.INT ? 1f : (Math.max(0.0001f, (max - min) / 100f)));
            if (def < min) def = min;
            if (def > max) def = max;

            params.add(new ReshadeParam(name, type, min, max, step, def, label, uiType));
        }
        return params;
    }

    // key scheme shared with the conf writer: COLOR seeds "<name>_<c>" per component, others "<name>"
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

    // ui_items separators arrive as the literal two-char "\0" escape from .fx source, or a real NUL
    private static List<String> parseUiItems(String raw) {
        ArrayList<String> out = new ArrayList<>();
        if (raw == null) return out;
        String[] parts = raw.split("\\\\0|\\x00", -1);
        Collections.addAll(out, parts);
        while (!out.isEmpty() && out.get(out.size() - 1).trim().isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    // up to [count] floats from "float3(...)", "{...}" or a scalar (broadcast to all components)
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

    // raw (possibly quoted) `key = value` from an annotation block; value runs to the next `;` or end
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
