package com.winlator.cmod.runtime.wine;

import android.util.Log;
import com.winlator.cmod.shared.io.FileUtils;
import com.winlator.cmod.shared.math.Mathf;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class WineRegistryEditor implements java.io.Closeable {
  private static final String TAG = "WineRegistryEditor";
  private final File file;
  private final ArrayList<String> lines = new ArrayList<>();
  private boolean modified = false;
  private boolean createKeyIfNotExist = true;

  public WineRegistryEditor(File file) {
    this.file = file;
    if (file.isFile()) {
      String content = FileUtils.readString(file);
      if (content != null) {
        // Handle both CRLF and LF, but preserve the lines
        String[] split = content.split("\\r?\\n", -1);
        for (String line : split) lines.add(line);
      }
    }
  }

  private static String escape(String str) {
    return str.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  @Override
  public void close() {
    if (modified) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < lines.size(); i++) {
        sb.append(lines.get(i));
        if (i < lines.size() - 1) sb.append("\n");
      }
      File tempFile = FileUtils.createTempFile(file.getParentFile(), FileUtils.getBasename(file.getPath()));
      if (FileUtils.writeString(tempFile, sb.toString())) {
        tempFile.renameTo(file);
      } else {
        tempFile.delete();
        Log.e(TAG, "Failed to write modified registry to " + file.getPath());
      }
    }
  }

  public void setCreateKeyIfNotExist(boolean createKeyIfNotExist) {
    this.createKeyIfNotExist = createKeyIfNotExist;
  }

  private int findKeyLine(String key) {
    String escapedKey = "[" + escape(key) + "]";
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).trim().equals(escapedKey)) return i;
    }
    return -1;
  }

  private int ensureKey(String key) {
    int index = findKeyLine(key);
    if (index != -1) return index;

    if (createKeyIfNotExist) {
      if (lines.isEmpty() || !lines.get(0).startsWith("WINE REGISTRY Version")) {
        if (lines.isEmpty()) lines.add("WINE REGISTRY Version 2");
      }
      
      // Ensure parent keys exist (recursive-ish)
      int lastSlash = key.lastIndexOf("\\");
      if (lastSlash != -1) {
        ensureKey(key.substring(0, lastSlash));
      }

      lines.add("");
      lines.add("[" + escape(key) + "]");
      modified = true;
      return lines.size() - 1;
    }
    return -1;
  }

  public String getStringValue(String key, String name) {
    return getStringValue(key, name, null);
  }

  public String getStringValue(String key, String name, String fallback) {
    String value = getRawValue(key, name);
    if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
    }
    return fallback;
  }

  public void setStringValue(String key, String name, String value) {
    setRawValue(key, name, value != null ? "\"" + escape(value) + "\"" : "\"\"");
  }

  public Integer getDwordValue(String key, String name) {
    return getDwordValue(key, name, null);
  }

  public Integer getDwordValue(String key, String name, Integer fallback) {
    String value = getRawValue(key, name);
    if (value != null && value.startsWith("dword:")) {
      try {
        return Integer.decode("0x" + value.substring(6));
      } catch (NumberFormatException e) {
        return fallback;
      }
    }
    return fallback;
  }

  public void setDwordValue(String key, String name, int value) {
    setRawValue(key, name, "dword:" + String.format("%08x", value));
  }

  public void setHexValue(String key, String name, String value) {
    int startLength = (name != null ? escape(name).length() + 2 : 1) + 5; // "name"=hex:
    StringBuilder formatted = new StringBuilder();
    int currentLineLength = startLength;
    
    for (int i = 0; i < value.length(); i += 2) {
      if (i > 0) formatted.append(",");
      formatted.append(value.substring(i, i + 2));
      currentLineLength += 3;
      
      if (currentLineLength > 75 && i + 2 < value.length()) {
        formatted.append("\\\n  ");
        currentLineLength = 2;
      }
    }
    setRawValue(key, name, "hex:" + formatted.toString());
  }

  public void setHexValue(String key, String name, byte[] bytes) {
    StringBuilder data = new StringBuilder();
    for (byte b : bytes) data.append(String.format(Locale.ENGLISH, "%02x", b & 0xff));
    setHexValue(key, name, data.toString());
  }

  private String getRawValue(String key, String name) {
    int keyLine = findKeyLine(key);
    if (keyLine == -1) return null;

    String prefix = (name != null ? "\"" + escape(name) + "\"" : "@") + "=";
    StringBuilder fullValue = new StringBuilder();
    boolean found = false;

    for (int i = keyLine + 1; i < lines.size(); i++) {
      String line = lines.get(i).trim();
      if (line.startsWith("[")) break;
      
      if (!found) {
        if (line.startsWith(prefix)) {
          found = true;
          fullValue.append(line.substring(prefix.length()));
        }
      } else {
        if (line.isEmpty() || (line.contains("=") && !line.startsWith(" "))) break;
        fullValue.append(line);
      }
    }
    
    if (found) {
        String result = fullValue.toString().replace("\\\n", "").trim();
        return result;
    }
    return null;
  }

  private void setRawValue(String key, String name, String value) {
    int keyLine = ensureKey(key);
    if (keyLine == -1) return;

    String prefix = (name != null ? "\"" + escape(name) + "\"" : "@") + "=";
    String newLine = prefix + value;
    String[] newValueLines = newLine.split("\\n");

    int valueStartLine = -1;
    int valueEndLine = -1;

    for (int i = keyLine + 1; i < lines.size(); i++) {
      String line = lines.get(i).trim();
      if (line.startsWith("[")) break;
      if (line.startsWith(prefix)) {
        valueStartLine = i;
        // Find where this multi-line value ends
        int j = i + 1;
        while (j < lines.size()) {
            String nextLine = lines.get(j);
            if (nextLine.trim().startsWith("[") || (nextLine.contains("=") && !nextLine.startsWith(" "))) break;
            j++;
        }
        valueEndLine = j - 1;
        break;
      }
    }

    if (valueStartLine != -1) {
      // Replace existing lines
      for (int i = valueEndLine; i >= valueStartLine; i--) lines.remove(i);
      for (int i = 0; i < newValueLines.length; i++) {
        lines.add(valueStartLine + i, newValueLines[i]);
      }
    } else {
      // Append new value under key
      int insertPos = keyLine + 1;
      while (insertPos < lines.size() && !lines.get(insertPos).trim().startsWith("[")) {
          insertPos++;
      }
      for (int i = 0; i < newValueLines.length; i++) {
        lines.add(insertPos + i, newValueLines[i]);
      }
    }
    modified = true;
  }

  public void removeValue(String key, String name) {
    int keyLine = findKeyLine(key);
    if (keyLine == -1) return;

    String prefix = (name != null ? "\"" + escape(name) + "\"" : "@") + "=";
    for (int i = keyLine + 1; i < lines.size(); i++) {
      String line = lines.get(i).trim();
      if (line.startsWith("[")) break;
      if (line.startsWith(prefix)) {
        // Remove this line and any continuation lines
        lines.remove(i);
        while (i < lines.size()) {
            String nextLine = lines.get(i);
            if (nextLine.trim().startsWith("[") || (nextLine.contains("=") && !nextLine.startsWith(" "))) break;
            lines.remove(i);
        }
        modified = true;
        break;
      }
    }
  }

  public boolean removeKey(String key) {
    return removeKey(key, false);
  }

  public boolean removeKey(String key, boolean removeTree) {
    if (removeTree) {
        String prefix = "[" + escape(key);
        boolean anyRemoved = false;
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).trim().startsWith(prefix)) {
                removeKeyAt(i);
                anyRemoved = true;
            }
        }
        return anyRemoved;
    } else {
        int line = findKeyLine(key);
        if (line != -1) {
            removeKeyAt(line);
            return true;
        }
    }
    return false;
  }
  
  private void removeKeyAt(int lineIndex) {
      lines.remove(lineIndex);
      while (lineIndex < lines.size()) {
          if (lines.get(lineIndex).trim().startsWith("[")) break;
          lines.remove(lineIndex);
      }
      modified = true;
  }

  public boolean hasKey(String key) {
    return findKeyLine(key) != -1;
  }

  public boolean appendRawContent(String rawContent) {
    if (rawContent == null || rawContent.trim().isEmpty()) return true;
    lines.add("");
    String[] newLines = rawContent.trim().split("\\r?\\n");
    for (String line : newLines) lines.add(line);
    modified = true;
    return true;
  }

  public void importReg(String regFile) {
    try {
      JSONObject jobj = new JSONObject(regFile);
      Iterator<String> iterator = jobj.keys();
      while (iterator.hasNext()) {
        String key = iterator.next();
        JSONArray entries = jobj.getJSONArray(key);
        for (int i = 0; i < entries.length(); i++) {
          JSONObject entry = entries.getJSONObject(i);
          String type = entry.getString("type");
          String name = (entry.getString("name").isEmpty()) ? null : entry.getString("name");
          String value = entry.getString("value");
          switch (type) {
            case "String":
              setStringValue(key, name, value);
              break;
            case "Dword":
              setDwordValue(key, name, Integer.parseInt(value));
              break;
            default:
              break;
          }
        }
      }
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }
}
