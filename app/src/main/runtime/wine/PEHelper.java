package com.winlator.cmod.runtime.wine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public abstract class PEHelper {
  public enum Architecture {
    X86,
    X64,
    UNKNOWN
  }

  public static Architecture detectArchitecture(File file) {
    if (file == null || !file.exists()) return Architecture.UNKNOWN;
    try (FileInputStream fis = new FileInputStream(file)) {
      byte[] dosHeader = new byte[64];
      if (fis.read(dosHeader) != 64) return Architecture.UNKNOWN;
      if (dosHeader[0] != 'M' || dosHeader[1] != 'Z') return Architecture.UNKNOWN;

      int peOffset =
          (dosHeader[60] & 0xFF)
              | ((dosHeader[61] & 0xFF) << 8)
              | ((dosHeader[62] & 0xFF) << 16)
              | ((dosHeader[63] & 0xFF) << 24);

      fis.getChannel().position(peOffset);
      byte[] peHeader = new byte[24];
      if (fis.read(peHeader) != 24) return Architecture.UNKNOWN;

      if (peHeader[0] != 'P' || peHeader[1] != 'E' || peHeader[2] != 0 || peHeader[3] != 0) {
        return Architecture.UNKNOWN;
      }

      int machine = (peHeader[4] & 0xFF) | ((peHeader[5] & 0xFF) << 8);
      switch (machine) {
        case 0x014c:
        case 0x01c0:
        case 0x01c4:
          return Architecture.X86;
        case 0x8664:
        case 0xAA64:
        case 0xA641:
          return Architecture.X64;
        default:
          return Architecture.UNKNOWN;
      }
    } catch (IOException e) {
      return Architecture.UNKNOWN;
    }
  }

  public static String getArchitecture(File file) {
    if (file == null || !file.exists() || file.isDirectory()) return "";
    try (FileInputStream fis = new FileInputStream(file)) {
      byte[] dosHeader = new byte[64];
      if (fis.read(dosHeader) != 64) return "";
      if (dosHeader[0] != 'M' || dosHeader[1] != 'Z') return "";

      int peOffset =
          (dosHeader[60] & 0xFF)
              | ((dosHeader[61] & 0xFF) << 8)
              | ((dosHeader[62] & 0xFF) << 16)
              | ((dosHeader[63] & 0xFF) << 24);

      fis.getChannel().position(peOffset);
      byte[] peHeader = new byte[24];
      if (fis.read(peHeader) != 24) return "";

      if (peHeader[0] != 'P' || peHeader[1] != 'E' || peHeader[2] != 0 || peHeader[3] != 0) {
        return "";
      }

      int machine = (peHeader[4] & 0xFF) | ((peHeader[5] & 0xFF) << 8);
      switch (machine) {
        case 0x014C:
        case 0x01c0:
        case 0x01c4:
          return "x86";
        case 0x8664:
          return "x86_64";
        case 0xAA64:
          return "arm64";
        case 0xA641:
          return "arm64ec";
        default:
          return "";
      }
    } catch (IOException e) {
      return "";
    }
  }

  public static boolean is64Bit(File file) {
    return detectArchitecture(file) == Architecture.X64;
  }
}
