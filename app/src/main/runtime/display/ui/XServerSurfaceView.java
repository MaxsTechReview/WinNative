package com.winlator.cmod.runtime.display.ui;

import android.annotation.SuppressLint;
import android.content.Context;
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
    private static final long TRANSIENT_FRAME_INTERVAL_NS = 1_000_000_000L / 120L;

    private final VulkanRenderer renderer;

    private final Object renderLock = new Object();
    private final Deque<Runnable> eventQueue = new ArrayDeque<>();
    private Thread renderThread;
    // Outgoing render thread still finishing teardown; the next surfaceCreated joins it before
    // re-attaching so a stale destroy() can't free the handle the new surface re-attaches to.
    private Thread retiringRenderThread;
    private volatile boolean running;
    private volatile boolean renderRequested;
    private volatile boolean transientRenderRequested;
    private volatile boolean paused;
    private volatile boolean surfaceReady;
    private volatile long transientRenderUntilNs;
    private long nextContinuousFrameNs;
    private int renderMode = RENDERMODE_WHEN_DIRTY;

    private volatile int width;
    private volatile int height;

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

    public void requestTransientRender(long durationMs) {
        long untilNs = System.nanoTime() + Math.max(1L, durationMs) * 1_000_000L;
        synchronized (renderLock) {
            if (untilNs > transientRenderUntilNs) transientRenderUntilNs = untilNs;
            transientRenderRequested = true;
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
            renderRequested = true;
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
        // Let any retiring render thread finish freeing the renderer before attaching the new surface.
        joinRetiringRenderThread();
        synchronized (renderLock) {
            surfaceReady = false;
            width = 0;
            height = 0;
        }
        renderer.attachSurface(holder.getSurface());
        startRenderThreadIfNeeded();
    }

    private void joinRetiringRenderThread() {
        Thread t;
        synchronized (renderLock) {
            t = retiringRenderThread;
            retiringRenderThread = null;
        }
        if (t != null && t != Thread.currentThread() && t.isAlive()) {
            try { t.join(3000); } catch (InterruptedException ignore) {}
        }
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
        synchronized (renderLock) {
            running = false;
            renderLock.notifyAll();
            if (renderThread != null) retiringRenderThread = renderThread;
            renderThread = null;
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
                        nextContinuousFrameNs = 0;
                        try { renderLock.wait(50); } catch (InterruptedException ignore) {}
                        continue;
                    }

                    long now = System.nanoTime();
                    boolean transientActive = transientRenderUntilNs > now;

                    if (!eventQueue.isEmpty()) {
                        event = eventQueue.poll();
                        break;
                    }

                    if (renderRequested) {
                        draw = true;
                        renderRequested = false;
                        transientRenderRequested = false;
                        if (!transientActive) nextContinuousFrameNs = 0;
                        break;
                    }

                    if (renderMode == RENDERMODE_CONTINUOUSLY) {
                        draw = true;
                        transientRenderRequested = false;
                        nextContinuousFrameNs = 0;
                        break;
                    }

                    if (transientRenderRequested) {
                        draw = true;
                        transientRenderRequested = false;
                        nextContinuousFrameNs = now + TRANSIENT_FRAME_INTERVAL_NS;
                        break;
                    }

                    if (transientActive) {
                        if (nextContinuousFrameNs == 0 || now >= nextContinuousFrameNs) {
                            draw = true;
                            nextContinuousFrameNs = now + TRANSIENT_FRAME_INTERVAL_NS;
                            break;
                        }
                        waitNanosLocked(nextContinuousFrameNs - now);
                        continue;
                    }

                    nextContinuousFrameNs = 0;
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

    private void waitNanosLocked(long nanos) {
        if (nanos <= 0) return;
        long millis = nanos / 1_000_000L;
        int extraNanos = (int) (nanos % 1_000_000L);
        try { renderLock.wait(millis, extraNanos); } catch (InterruptedException ignore) {}
    }

    // ---- Convenience accessors used by VulkanRenderer ----------------------

    public int getSurfaceWidth() { return width; }
    public int getSurfaceHeight() { return height; }
}
