package com.winlator.cmod.runtime.display;
import java.io.File;

// Steam executable descriptor split out of XServerDisplayActivity.
class SteamExecutableInfo {
    final String relativePath;
    final File file;

    SteamExecutableInfo(String relativePath, File file) {
        this.relativePath = relativePath;
        this.file = file;
    }
}
