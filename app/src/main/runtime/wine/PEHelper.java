package com.winlator.cmod.runtime.wine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public abstract class PEHelper {
  public static boolean is64Bit(File file) {
    if (file == null || !file.exists()) return true; // Default to 64-bit if unknown
    try (FileInputStream fis = new FileInputStream(file)) {
      byte[] dosHeader = new byte[64];
      if (fis.read(dosHeader) != 64) return true;
      if (dosHeader[0] != 'M' || dosHeader[1] != 'Z') return true;

      int peOffset =
          (dosHeader[60] & 0xFF)
              | ((dosHeader[61] & 0xFF) << 8)
              | ((dosHeader[62] & 0xFF) << 16)
              | ((dosHeader[63] & 0xFF) << 24);

      fis.getChannel().position(peOffset);
      byte[] peHeader = new byte[24];
      if (fis.read(peHeader) != 24) return true;

      if (peHeader[0] != 'P' || peHeader[1] != 'E' || peHeader[2] != 0 || peHeader[3] != 0)
        return true;

      int machine = (peHeader[4] & 0xFF) | ((peHeader[5] & 0xFF) << 8);
      return machine == 0x8664 || machine == 0xAA64; // AMD64 or ARM64
    } catch (IOException e) {
      return true;
    }
  }
}
