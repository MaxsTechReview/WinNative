package com.winlator.cmod.widget;

import android.annotation.SuppressLint;
import android.os.Build;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Surface;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.xserver.XServer;

@SuppressLint("ViewConstructor")
public class XServerView extends GLSurfaceView {
    private static final String TAG = "XServerView";
    private final GLRenderer renderer;
    private float preferredFrameRate = 0f;
    private int currentRenderMode = RENDERMODE_WHEN_DIRTY;

    public XServerView(Context context, XServer xServer) {
        super(context);
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        renderer = new GLRenderer(this, xServer);
        setRenderer(renderer);
        super.setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    public void setPreferredFrameRateCompat(float refreshRate) {
        preferredFrameRate = Math.max(0f, refreshRate);
        Log.d(TAG, "setPreferredFrameRateCompat rate=" + preferredFrameRate);
        post(this::applyPreferredFrameRateToSurface);
    }

    @Override
    public void setRenderMode(int renderMode) {
        if (currentRenderMode != renderMode) {
            Log.d(TAG, "setRenderMode "
                    + renderModeToString(currentRenderMode)
                    + " -> "
                    + renderModeToString(renderMode));
            currentRenderMode = renderMode;
        }
        super.setRenderMode(renderMode);
    }

    @Override
    public void surfaceCreated(android.view.SurfaceHolder holder) {
        super.surfaceCreated(holder);
        applyPreferredFrameRateToSurface();
    }

    private void applyPreferredFrameRateToSurface() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        Surface surface = getHolder().getSurface();
        if (surface == null || !surface.isValid()) return;

        float frameRate = preferredFrameRate > 0f ? preferredFrameRate : 0f;
        Log.d(TAG, "applyPreferredFrameRateToSurface rate=" + frameRate
                + " surfaceValid=" + surface.isValid());
        surface.setFrameRate(frameRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
    }

    private static String renderModeToString(int renderMode) {
        if (renderMode == RENDERMODE_CONTINUOUSLY) {
            return "CONTINUOUS";
        }
        if (renderMode == RENDERMODE_WHEN_DIRTY) {
            return "DIRTY";
        }
        return String.valueOf(renderMode);
    }

    public GLRenderer getRenderer() {
        return renderer;
    }
}
