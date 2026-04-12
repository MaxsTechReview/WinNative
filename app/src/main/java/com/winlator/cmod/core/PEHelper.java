package com.winlator.cmod.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public abstract class PEHelper {
    public static boolean is64Bit(File file) {
        String arch = getArchitecture(file);
        return arch.equals("x86_64") || arch.equals("arm64ec") || arch.equals("arm64");
    }

    public static String getArchitecture(File file) {
        if (file == null || !file.exists() || file.isDirectory()) return "";
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] dosHeader = new byte[64];
            if (fis.read(dosHeader) != 64) return "";
            if (dosHeader[0] != 'M' || dosHeader[1] != 'Z') return "";

            int peOffset = (dosHeader[60] & 0xFF) | ((dosHeader[61] & 0xFF) << 8) |
                           ((dosHeader[62] & 0xFF) << 16) | ((dosHeader[63] & 0xFF) << 24);

            fis.getChannel().position(peOffset);
            byte[] peHeader = new byte[24];
            if (fis.read(peHeader) != 24) return "";

            if (peHeader[0] != 'P' || peHeader[1] != 'E' || peHeader[2] != 0 || peHeader[3] != 0) return "";

            int machine = (peHeader[4] & 0xFF) | ((peHeader[5] & 0xFF) << 8);
            switch (machine) {
                case 0x014C: return "x86";
                case 0x8664: return "x86_64";
                case 0xAA64: return "arm64";
                case 0xA641: return "arm64ec";
                default: return "";
            }
        } catch (IOException e) {
            return "";
        }
    }
}
