package com.swordfish.libretrodroid;

public class RetroAchievements {

    static {
        System.loadLibrary("libretrodroid");
    }

    public static final RetroAchievements INSTANCE = new RetroAchievements();

    public static volatile boolean active = false;

    private RetroAchievements() {}

    public native void nativeInit(boolean hardcore);

    public native void nativeLoginPassword(String username, String password);
    public native void nativeLoginToken(String username, String token);

    public native void nativeLoadGame(int consoleId, String filePath);
    public native void nativeUnloadGame();

    public native void nativeDoFrame();
    public native void nativeIdle();
    public native void nativeReset();

    public native void nativeSetHardcore(boolean enabled);
    public native boolean nativeGetHardcore();
    public native boolean nativeHardcoreCompatible();

    public native String[] nativePollHttpRequest();
    public native void nativeProvideResponse(int requestId, int httpStatus, byte[] body);
    public native String[] nativePollEvents();

    public native String[] nativeGetAchievements();
    public native String nativeGetGameSummary();

    public native void nativeDestroy();
}
