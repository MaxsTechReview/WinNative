package com.winlator.cmod.runtime.display.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.winlator.cmod.runtime.display.renderer.RenderCallback;
import com.winlator.cmod.runtime.display.renderer.VulkanRenderer;
import com.winlator.cmod.runtime.display.xserver.XServer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * SurfaceView that drives a {@link VulkanRenderer} on a dedicated render thread.
 *
 * <p>This is the Vulkan replacement for the previous {@code GLSurfaceView}-backed
 * {@code XServerView}. It preserves the public API the rest of the codebase relies on:
 * {@link #queueEvent(Runnable)}, {@link #requestRender()}, {@link #setRenderMode(int)},
 * {@link #onResume()}, {@link #onPause()}, {@link #getRenderer()}.
 */
@SuppressLint("ViewConstructor")
public class XServerSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    public static final int RENDERMODE_WHEN_DIRTY  = 0;
    public static final int RENDERMODE_CONTINUOUSLY = 1;

    private final VulkanRenderer renderer;

    private final Object renderLock = new Object();
    private final Deque<Runnable> eventQueue = new ArrayDeque<>();
    private Thread renderThread;
    private volatile boolean running;
    private volatile boolean renderRequested;
    private volatile boolean paused;
    private volatile boolean surfaceReady;
    private int renderMode = RENDERMODE_WHEN_DIRTY;

    private volatile int width;
    private volatile int height;
    private volatile float surfaceFrameRate;

    public XServerSurfaceView(Context context, XServer xServer) {
        super(context);
        setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        renderer = new VulkanRenderer(this, xServer);
        getHolder().addCallback(this);
    }

    public VulkanRenderer getRenderer() {
        return renderer;
    }

    public void queueEvent(Runnable r) {
        if (r == null) return;
        synchronized (renderLock) {
            eventQueue.add(r);
            renderRequested = true;
            renderLock.notifyAll();
        }
    }

    public void requestRender() {
        synchronized (renderLock) {
            renderRequested = true;
            renderLock.notifyAll();
        }
    }

    public void setRenderMode(int mode) {
        if (mode != RENDERMODE_WHEN_DIRTY && mode != RENDERMODE_CONTINUOUSLY) return;
        synchronized (renderLock) {
            renderMode = mode;
            if (mode == RENDERMODE_CONTINUOUSLY) {
                renderRequested = true;
                renderLock.notifyAll();
            }
        }
    }

    public int getRenderMode() {
        return renderMode;
    }

    public void onResume() {
        synchronized (renderLock) {
            paused = false;
            renderLock.notifyAll();
        }
    }

    public void onPause() {
        synchronized (renderLock) {
            paused = true;
            renderLock.notifyAll();
        }
    }

    // --- SurfaceHolder.Callback ----------------------------------------------

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        synchronized (renderLock) {
            surfaceReady = false;
            width = 0;
            height = 0;
        }
        renderer.attachSurface(holder.getSurface());
        applySurfaceFrameRate();
        startRenderThreadIfNeeded();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (w <= 0 || h <= 0) {
            synchronized (renderLock) {
                surfaceReady = false;
                width = 0;
                height = 0;
                renderLock.notifyAll();
            }
            return;
        }

        renderer.notifySurfaceChanged(w, h);
        applySurfaceFrameRate();
        synchronized (renderLock) {
            width = w;
            height = h;
            eventQueue.add(() -> renderer.onSurfaceChanged(w, h));
            surfaceReady = true;
            renderRequested = true;
            renderLock.notifyAll();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        clearSurfaceFrameRate(holder.getSurface());
        synchronized (renderLock) {
            surfaceReady = false;
            width = 0;
            height = 0;
            renderLock.notifyAll();
        }
        // Run the render thread one more iteration so it sees surfaceReady=false and exits.
        stopRenderThread();
        renderer.detachSurface();
    }

    // --- Render thread -------------------------------------------------------

    private void startRenderThreadIfNeeded() {
        if (renderThread != null && renderThread.isAlive()) return;
        running = true;
        renderThread = new Thread(this::renderLoop, "VkRenderer");
        renderThread.start();
    }

    private void stopRenderThread() {
        Thread t;
        synchronized (renderLock) {
            running = false;
            renderLock.notifyAll();
            t = renderThread;
            renderThread = null;
        }
        if (t != null) {
            try {
                t.join(1000);
            } catch (InterruptedException ignore) {}
        }
    }

    private void renderLoop() {
        renderer.onSurfaceCreated();
        if (width > 0 && height > 0) renderer.onSurfaceChanged(width, height);

        while (true) {
            Runnable event = null;
            boolean draw = false;
            synchronized (renderLock) {
                while (true) {
                    if (!running) break;
                    if (paused || !surfaceReady) {
                        try { renderLock.wait(50); } catch (InterruptedException ignore) {}
                        continue;
                    }

                    if (!eventQueue.isEmpty()) {
                        event = eventQueue.poll();
                        break;
                    }

                    if (renderRequested) {
                        draw = true;
                        renderRequested = false;
                        break;
                    }

                    if (renderMode == RENDERMODE_CONTINUOUSLY) {
                        draw = true;
                        break;
                    }

                    try { renderLock.wait(); } catch (InterruptedException ignore) {}
                }
            }
            if (!running) break;
            if (event != null) {
                try { event.run(); } catch (Throwable ignore) {}
            } else if (draw) {
                try { renderer.onDrawFrame(); } catch (Throwable ignore) {}
            }
        }
        renderer.onSurfaceDestroyed();
    }

    // ---- Convenience accessors used by VulkanRenderer ----------------------

    public int getSurfaceWidth() { return width; }
    public int getSurfaceHeight() { return height; }

    public void setSurfaceFrameRate(float frameRate) {
        surfaceFrameRate = frameRate > 0f ? frameRate : 0f;
        applySurfaceFrameRate();
    }

    private void applySurfaceFrameRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post(this::applySurfaceFrameRate);
            return;
        }

        Surface surface = getHolder().getSurface();
        if (surface == null || !surface.isValid()) return;
        try {
            surface.setFrameRate(surfaceFrameRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
        } catch (IllegalArgumentException | IllegalStateException ignored) {}
    }

    private void clearSurfaceFrameRate(Surface surface) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        if (surface == null || !surface.isValid()) return;
        try {
            surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
        } catch (IllegalArgumentException | IllegalStateException ignored) {}
    }
}
