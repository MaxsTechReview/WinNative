package com.winlator.cmod.runtime.display;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// Exit cloud-sync callback/result types split out of XServerDisplayActivity.
interface ExitUploadAction {
    void start(ExitUploadCallback callback);
}

interface ExitUploadCallback {
    void onComplete(ExitUploadResult result);
}

interface ExitUploadBlockingAction {
    ExitUploadResult run() throws Exception;
}

final class ExitUploadResult {
    final boolean success;
    @NonNull final String message;
    final boolean retryable;

    ExitUploadResult(boolean success, @Nullable String message, boolean retryable) {
        this.success = success;
        this.message = message == null ? "" : message;
        this.retryable = retryable;
    }
}
