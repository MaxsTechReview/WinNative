-keep class com.winlator.cmod.shared.io.NativeContentIO {
    *;
}

-keep class com.winlator.cmod.shared.util.OnExtractFileListener {
    public java.io.File onExtractFile(java.io.File, long);
}

-keep class com.winlator.cmod.runtime.content.Downloader$DownloadListener {
    public void onProgress(long, long);
}
