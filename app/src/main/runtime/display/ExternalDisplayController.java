package com.winlator.cmod.runtime.display;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.hardware.display.DeviceProductInfo;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.winlator.cmod.runtime.display.renderer.ViewTransformation;
import com.winlator.cmod.runtime.display.renderer.VulkanRenderer;
import com.winlator.cmod.runtime.display.ui.XServerSurfaceView;

import java.util.ArrayList;
import java.util.List;

/**
 * Swaps the game onto a connected external display (USB-C/HDMI/Miracast/XR glasses) by moving the
 * {@link XServerSurfaceView} into a {@link Presentation}, while controls and the menu stay on the
 * phone. Offers a hybrid resolution/refresh list — real {@link Display.Mode}s switch the panel via
 * preferredDisplayModeId; other tiers render via SurfaceHolder.setFixedSize and are hardware-scaled.
 */
public final class ExternalDisplayController {
    private static final String TAG = "ExternalDisplay";

    // Standard render-resolution tiers (by height); width is derived from the panel aspect ratio.
    private static final int[] STANDARD_TIER_HEIGHTS = {2160, 1440, 1080, 720, 480};
    // Standard refresh tiers offered alongside the panel's detected rates (best-effort if unmatched).
    private static final float[] STANDARD_REFRESH_RATES = {165f, 144f, 120f, 90f, 60f, 50f, 30f};
    private static final float REFRESH_EPSILON = 0.5f; // tolerates 59.94 vs 60.0

    public interface Callbacks {
        void onExternalDisplayConnected(Display display);

        void onExternalDisplayDisconnected();

        /** Refresh the drawer menu (Output tab visibility/controls). */
        void onSwapStateChanged(boolean swapActive);
    }

    // A selectable resolution. physical=true switches the panel mode; otherwise it's a render tier.
    private static final class ResEntry {
        final int w;
        final int h;
        final boolean physical;
        final String label;

        ResEntry(int w, int h, boolean physical, String label) {
            this.w = w;
            this.h = h;
            this.physical = physical;
            this.label = label;
        }
    }

    private final Activity activity;
    private final FrameLayout phoneFrame;
    private final XServerSurfaceView gameView;
    private final Callbacks callbacks;
    private final DisplayManager displayManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private GamePresentation presentation;
    private Display externalDisplay;
    private boolean swapActive;
    private boolean listenerRegistered;
    private int promptedDisplayId = -1; // guards against re-prompting for the same connection

    // Output-tab selections (only meaningful while swapped).
    private final List<ResEntry> resolutions = new ArrayList<>();
    private int selectedResolutionIndex = 0;
    private final List<Float> refreshOptions = new ArrayList<>();
    private int selectedRefreshIndex = 0;
    private boolean renderActive = false; // a setFixedSize render buffer is currently in effect
    private boolean panelScalerLocked = false; // sink ignored a real mode switch — render-scale instead
    private int modeRequestGen = 0;            // guards a verify against a newer selection superseding it
    private boolean gameMode = true;      // request HDMI ALLM when the sink supports it
    private String lastModesSignature = ""; // detects EDID re-advertisement (e.g. glasses unlocking 120Hz)

    private int fillMode = ViewTransformation.FILL_MODE_FIT;
    private int savedPresentMode = VulkanRenderer.PRESENT_MODE_FIFO;
    private float savedPhoneRefreshRate = 0f;

    // Phone surface size captured before the swap, to restore it exactly on return.
    private int savedPhoneViewWidth = 0;
    private int savedPhoneViewHeight = 0;

    // Shared Viture glasses controller + settings (owned app-wide by GlassesManager so the library and
    // the in-game swap never double-claim the USB); values persist across both.
    private final VitureGlasses viture;
    private final GlassesManager.Listener glassesListener;

    public ExternalDisplayController(Activity activity, FrameLayout phoneFrame,
                                     XServerSurfaceView gameView, Callbacks callbacks) {
        this.activity = activity;
        this.phoneFrame = phoneFrame;
        this.gameView = gameView;
        this.callbacks = callbacks;
        this.displayManager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
        GlassesManager.INSTANCE.init(activity);
        this.viture = GlassesManager.INSTANCE.glasses();
        this.glassesListener = (connectionChanged) -> {
            // Only re-apply the mode / re-render on (re)connect — not on every slider tick, which would
            // storm the drawer recompose and the display-mode command while dragging.
            if (connectionChanged) {
                if (viture != null && viture.isConnected() && swapActive) applyOutputMode();
                callbacks.onSwapStateChanged(swapActive);
            }
        };
        GlassesManager.INSTANCE.addListener(glassesListener);
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    public void start() {
        if (displayManager == null) return;
        if (!listenerRegistered) {
            displayManager.registerDisplayListener(displayListener, mainHandler);
            listenerRegistered = true;
        }
        maybePromptForDisplay();
    }

    // Offer the swap prompt at most once per connection (and never while swapped).
    private void maybePromptForDisplay() {
        Display d = findExternalDisplay();
        if (d != null && !swapActive && d.getDisplayId() != promptedDisplayId) {
            promptedDisplayId = d.getDisplayId();
            callbacks.onExternalDisplayConnected(d);
        }
    }

    public void stop() {
        if (displayManager != null && listenerRegistered) {
            displayManager.unregisterDisplayListener(displayListener);
            listenerRegistered = false;
        }
    }

    public void release() {
        stop();
        GlassesManager.INSTANCE.removeListener(glassesListener);
        exitSwap();
    }

    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            maybePromptForDisplay();
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            if (displayId == promptedDisplayId) promptedDisplayId = -1;
            if (swapActive && externalDisplay != null && externalDisplay.getDisplayId() == displayId) {
                exitSwap();
                callbacks.onExternalDisplayDisconnected();
            }
        }

        @Override
        public void onDisplayChanged(int displayId) {
            if (swapActive && externalDisplay != null && externalDisplay.getDisplayId() == displayId) {
                Display fresh = displayManager != null ? displayManager.getDisplay(displayId) : null;
                if (fresh != null) externalDisplay = fresh;
                if (!renderActive) {
                    // Re-scan if the sink re-advertised modes (glasses unlocking 120Hz, 2D↔3D toggle).
                    if (!modesSignature(externalDisplay).equals(lastModesSignature)) {
                        rebuildModeTables();
                    }
                    syncSelectionToActiveMode();
                }
                callbacks.onSwapStateChanged(true);
            }
        }
    };

    private static String modesSignature(Display d) {
        if (d == null) return "";
        StringBuilder sb = new StringBuilder();
        try {
            for (Display.Mode m : d.getSupportedModes()) {
                sb.append(m.getPhysicalWidth()).append('x').append(m.getPhysicalHeight())
                        .append('@').append(Math.round(m.getRefreshRate())).append(';');
            }
        } catch (Exception ignore) {}
        return sb.toString();
    }

    // Reflect the actual active panel mode in the selection (physical selections only).
    private void syncSelectionToActiveMode() {
        if (externalDisplay == null || resolutions.isEmpty()) return;
        Display.Mode active;
        try {
            active = externalDisplay.getMode();
        } catch (Exception e) {
            return; // display may be invalidated mid-change
        }
        if (active == null) return;
        for (int i = 0; i < resolutions.size(); i++) {
            ResEntry r = resolutions.get(i);
            if (r.physical && r.w == active.getPhysicalWidth() && r.h == active.getPhysicalHeight()) {
                selectedResolutionIndex = i;
                break;
            }
        }
        // Keep the glasses' persisted rate; don't snap back to the panel's transient 60Hz boot mode.
        if (!isVitureSink()) selectedRefreshIndex = closestRateIndex(round1(active.getRefreshRate()));
    }

    // ── Discovery ──────────────────────────────────────────────────────────

    public boolean hasExternalDisplay() {
        return findExternalDisplay() != null;
    }

    public boolean isSwapActive() {
        return swapActive;
    }

    public Display findExternalDisplay() {
        if (displayManager == null) return null;
        Display[] presentationDisplays =
                displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        for (Display d : presentationDisplays) {
            if (d != null && d.getDisplayId() != Display.DEFAULT_DISPLAY && d.isValid()) return d;
        }
        for (Display d : displayManager.getDisplays()) {
            if (d != null && d.getDisplayId() != Display.DEFAULT_DISPLAY && d.isValid()
                    && (d.getFlags() & Display.FLAG_PRESENTATION) != 0) {
                return d;
            }
        }
        return null;
    }

    public String getDisplayName() {
        return describeDisplay(externalDisplay);
    }

    public String getAvailableDisplayName() {
        return describeDisplay(findExternalDisplay());
    }

    // Prefer the EDID product name (the actual model); fall back to the framework display name.
    private static String describeDisplay(Display d) {
        if (d == null) return "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                DeviceProductInfo info = d.getDeviceProductInfo();
                if (info != null) {
                    String product = info.getName();
                    if (product != null && !product.trim().isEmpty()) return product.trim();
                }
            } catch (Exception ignore) {}
        }
        String name = d.getName();
        return name != null ? name.trim() : "";
    }

    // True when the active sink is Viture glasses (USB control up, or the EDID product name says VITURE).
    private boolean isVitureSink() {
        if (viture.isConnected()) return true;
        String name = describeDisplay(externalDisplay);
        return !name.isEmpty() && name.toUpperCase(java.util.Locale.ROOT).contains("VITURE");
    }

    // ── Swap / restore ─────────────────────────────────────────────────────

    public void enterSwap() {
        if (swapActive) return;
        Display d = findExternalDisplay();
        if (d == null) return;
        externalDisplay = d;

        // Capture the phone surface size before reparenting, so swap-back can restore it exactly.
        savedPhoneViewWidth = phoneFrame != null && phoneFrame.getWidth() > 0
                ? phoneFrame.getWidth() : gameView.getWidth();
        savedPhoneViewHeight = phoneFrame != null && phoneFrame.getHeight() > 0
                ? phoneFrame.getHeight() : gameView.getHeight();

        GamePresentation pres = new GamePresentation(activity, d);
        pres.setOnDismissListener(dialog -> {
            if (swapActive) {
                exitSwap();
                callbacks.onExternalDisplayDisconnected();
            }
        });
        try {
            pres.show();
        } catch (Exception e) {
            // InvalidDisplayException / BadTokenException — abort the swap cleanly instead of crashing.
            Log.w(TAG, "Could not show presentation on external display", e);
            pres.setOnDismissListener(null);
            try { pres.dismiss(); } catch (Exception ignore) {}
            externalDisplay = null;
            return;
        }
        presentation = pres;

        // Move only the render surface; controls/menu/HUD remain on the phone.
        if (gameView.getParent() instanceof ViewGroup) {
            ((ViewGroup) gameView.getParent()).removeView(gameView);
        }
        pres.getContainer().addView(gameView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        swapActive = true;
        renderActive = false;
        fillMode = ViewTransformation.FILL_MODE_FIT;
        rebuildModeTables();
        applyFillModeToRenderer();
        applyGameMode();
        applyHighRefreshToPhone();   // reduce phone touch lag
        applyExternalPresentMode();  // non-blocking present so a slow sink can't stall the render thread

        // Glasses already under USB control: push the persisted mode (default 120Hz) now via the MCU.
        if (viture.isConnected()) applyOutputMode();

        callbacks.onSwapStateChanged(true);
    }

    // Return the game to the phone and dismiss the presentation. Idempotent.
    public void exitSwap() {
        if (!swapActive && presentation == null) return;
        boolean wasActive = swapActive;
        swapActive = false;
        renderActive = false;
        panelScalerLocked = false;

        // Clear any render buffer so the phone surface follows the phone layout again.
        fillMode = ViewTransformation.FILL_MODE_FIT;
        VulkanRenderer renderer = gameView.getRenderer();
        try { gameView.getHolder().setSizeFromLayout(); } catch (Exception ignore) {}
        if (renderer != null) {
            renderer.setFillModeQuiet(ViewTransformation.FILL_MODE_FIT);
            renderer.invalidateSurfaceSize();
        }
        restorePhoneRefresh();
        restorePresentMode();

        if (gameView.getParent() instanceof ViewGroup) {
            ((ViewGroup) gameView.getParent()).removeView(gameView);
        }
        GamePresentation pres = presentation;
        presentation = null;
        if (pres != null) {
            pres.setOnDismissListener(null);
            try { pres.dismiss(); } catch (Exception ignore) {}
        }
        externalDisplay = null;

        // Re-add behind the on-screen controls (index 0 = lowest z-order).
        if (gameView.getParent() == null && phoneFrame != null) {
            phoneFrame.addView(gameView, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // Compose's empty AndroidView update={} won't re-measure the reparented view, so size it
            // explicitly at the phone frame; the posted/delayed retries cover the async surface.
            restorePhoneSurfaceSize();
            gameView.requestLayout();
            phoneFrame.requestLayout();
            mainHandler.post(this::restorePhoneSurfaceSize);
            mainHandler.postDelayed(this::syncViewportToPhoneSurface, 250L);
            mainHandler.postDelayed(this::syncViewportToPhoneSurface, 600L);
        }
        if (wasActive) callbacks.onSwapStateChanged(false);
    }

    // Measure + layout the reparented SurfaceView at the phone frame's size and recompute the viewport.
    private void restorePhoneSurfaceSize() {
        if (swapActive || phoneFrame == null) return;
        int w = phoneFrame.getWidth();
        int h = phoneFrame.getHeight();
        if (w <= 0 || h <= 0) { w = savedPhoneViewWidth; h = savedPhoneViewHeight; }
        if (w <= 0 || h <= 0) return;
        gameView.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        gameView.layout(0, 0, w, h);
        VulkanRenderer r = gameView.getRenderer();
        if (r != null) r.forceViewportRecompute(w, h);
    }

    private void syncViewportToPhoneSurface() {
        if (swapActive) return;
        VulkanRenderer r = gameView.getRenderer();
        if (r == null) return;
        int w = gameView.getSurfaceWidth();
        int h = gameView.getSurfaceHeight();
        if (w <= 0 || h <= 0) {
            w = phoneFrame != null ? phoneFrame.getWidth() : 0;
            h = phoneFrame != null ? phoneFrame.getHeight() : 0;
        }
        if (w > 0 && h > 0) r.forceViewportRecompute(w, h);
    }

    // ── Output tab: resolution / refresh / aspect / game mode ──────────────

    // Build the hybrid resolution list (physical modes ∪ render tiers) and refresh options.
    private void rebuildModeTables() {
        resolutions.clear();
        refreshOptions.clear();
        selectedResolutionIndex = 0;
        selectedRefreshIndex = 0;
        renderActive = false;
        panelScalerLocked = false;
        if (externalDisplay == null) return;

        Display.Mode current;
        try {
            current = externalDisplay.getMode();
        } catch (Exception e) {
            current = null;
        }
        int nativeW = current != null ? current.getPhysicalWidth() : 0;
        int nativeH = current != null ? current.getPhysicalHeight() : 0;
        float panelAspect = (nativeW > 0 && nativeH > 0) ? (float) nativeW / nativeH : 16f / 9f;

        Display.Mode[] modes;
        try {
            modes = externalDisplay.getSupportedModes();
        } catch (Exception e) {
            modes = new Display.Mode[0];
        }

        // Physical resolutions reported by the panel.
        List<int[]> physical = new ArrayList<>();
        for (Display.Mode m : modes) {
            int w = m.getPhysicalWidth();
            int h = m.getPhysicalHeight();
            if (!containsRes(physical, w, h)) physical.add(new int[]{w, h});
        }
        for (int[] p : physical) {
            boolean isNative = p[0] == nativeW && p[1] == nativeH;
            resolutions.add(new ResEntry(p[0], p[1], true, resLabel(p[0], p[1], isNative)));
        }

        // Standard render tiers (aspect-matched), so single-mode sinks still get 4K/2K/1080/720/480.
        int maxH = Math.max(2160, nativeH);
        for (int tierH : STANDARD_TIER_HEIGHTS) {
            if (tierH > maxH) continue;
            int tierW = Math.round(tierH * panelAspect);
            tierW -= (tierW & 1); // keep even
            if (tierW <= 0 || containsResEntry(tierW, tierH)) continue;
            resolutions.add(new ResEntry(tierW, tierH, false, resLabel(tierW, tierH, false)));
        }
        resolutions.sort((a, b) -> Long.compare((long) b.w * b.h, (long) a.w * a.h));

        for (int i = 0; i < resolutions.size(); i++) {
            ResEntry r = resolutions.get(i);
            if (r.physical && r.w == nativeW && r.h == nativeH) {
                selectedResolutionIndex = i;
                break;
            }
        }

        // Detected rates first (so 59.94 wins over 60.0), then standard tiers.
        for (Display.Mode m : modes) addRate(refreshOptions, round1(m.getRefreshRate()));
        for (float r : STANDARD_REFRESH_RATES) addRate(refreshOptions, r);
        refreshOptions.sort((a, b) -> Float.compare(b, a));
        float nativeRate = current != null ? round1(current.getRefreshRate()) : 60f;
        // Glasses default to the persisted rate (120 out of the box); other sinks to their native rate.
        selectedRefreshIndex = closestRateIndex(
                isVitureSink() ? GlassesManager.INSTANCE.currentRefreshHz() : nativeRate);

        lastModesSignature = modesSignature(externalDisplay);
    }

    public List<String> getResolutionLabels() {
        List<String> out = new ArrayList<>();
        for (ResEntry r : resolutions) out.add(r.label);
        return out;
    }

    public int getSelectedResolutionIndex() {
        return clampIndex(selectedResolutionIndex, resolutions.size());
    }

    public List<String> getRefreshRateLabels() {
        List<String> out = new ArrayList<>();
        for (Float r : refreshOptions) out.add(Math.round(r) + " Hz");
        return out;
    }

    public int getSelectedRefreshRateIndex() {
        return clampIndex(selectedRefreshIndex, refreshOptions.size());
    }

    public void selectResolution(int index) {
        if (index < 0 || index >= resolutions.size()) return;
        selectedResolutionIndex = index;
        applyOutputMode();
    }

    public void selectRefreshRate(int index) {
        if (index < 0 || index >= refreshOptions.size()) return;
        selectedRefreshIndex = index;
        if (isVitureSink()) GlassesManager.INSTANCE.persistRefreshHz(Math.round(refreshOptions.get(index)));
        applyOutputMode();
    }

    private float currentSelectedRefresh() {
        if (refreshOptions.isEmpty()) return 0f;
        return refreshOptions.get(clampIndex(selectedRefreshIndex, refreshOptions.size()));
    }

    // Physical resolution with a matching mode switches the panel; otherwise drive the render buffer.
    private void applyOutputMode() {
        if (!swapActive || externalDisplay == null || presentation == null
                || presentation.getWindow() == null || resolutions.isEmpty()) {
            return;
        }
        ResEntry res = resolutions.get(clampIndex(selectedResolutionIndex, resolutions.size()));
        float hz = currentSelectedRefresh();
        WindowManager.LayoutParams lp = presentation.getWindow().getAttributes();
        final int gen = ++modeRequestGen;

        // On Viture glasses the panel timing is driven over USB (Android may not even enumerate the
        // mode), so force it via the MCU; the Android calls below then lock the Presentation onto it.
        if (viture.isConnected()) viture.forceRefreshHz(Math.round(hz));

        if (res.physical && !panelScalerLocked) {
            Display.Mode best = bestPhysicalMode(res.w, res.h, hz);
            if (best != null) {
                clearRenderBuffer();
                lp.preferredRefreshRate = 0f; // must be 0 when a modeId is set
                lp.preferredDisplayModeId = best.getModeId();
                presentation.getWindow().setAttributes(lp);
                requestSurfaceFrameRate(best.getRefreshRate()); // reinforce on the surface
                renderActive = false;
                Log.i(TAG, "Physical mode " + res.w + "x" + res.h + "@"
                        + Math.round(best.getRefreshRate()) + " (modeId=" + best.getModeId() + ")");
                verifyPhysicalSwitch(best, gen);
                return;
            }
        }

        // Render path: panel stays at native, the scaler maps our buffer onto it.
        lp.preferredDisplayModeId = 0;
        lp.preferredRefreshRate = hz > 0f ? hz : 0f;
        presentation.getWindow().setAttributes(lp);
        setRenderBuffer(res.w, res.h);
        requestSurfaceFrameRate(hz);
        renderActive = true;
        Log.i(TAG, "Render buffer " + res.w + "x" + res.h + " @~" + Math.round(hz)
                + "Hz (hardware-scaled to panel)");
    }

    private Display.Mode bestPhysicalMode(int w, int h, float hz) {
        if (externalDisplay == null) return null;
        Display.Mode best = null;
        float bestDiff = Float.MAX_VALUE;
        Display.Mode[] modes;
        try {
            modes = externalDisplay.getSupportedModes();
        } catch (Exception e) {
            return null;
        }
        for (Display.Mode m : modes) {
            if (m.getPhysicalWidth() == w && m.getPhysicalHeight() == h) {
                float diff = Math.abs(m.getRefreshRate() - hz);
                if (diff < bestDiff) { bestDiff = diff; best = m; }
            }
        }
        return best;
    }

    // After requesting a real switch, confirm the panel actually moved. Some phone display pipelines
    // (e.g. OnePlus over USB-C/HDMI) silently hold the native timing and hardware-scale — even the
    // system setUserPreferredDisplayMode can't move them. When that happens, latch into render scaling
    // so the Output controls stay honest instead of implying a switch that never landed.
    private void verifyPhysicalSwitch(Display.Mode requested, final int gen) {
        final int wantW = requested.getPhysicalWidth();
        final int wantH = requested.getPhysicalHeight();
        final float wantHz = requested.getRefreshRate();
        mainHandler.postDelayed(() -> {
            if (gen != modeRequestGen || !swapActive || externalDisplay == null || panelScalerLocked) {
                return; // superseded by a newer selection, swap ended, or already known to scale
            }
            if (displayManager != null) {
                Display fresh = displayManager.getDisplay(externalDisplay.getDisplayId());
                if (fresh != null) externalDisplay = fresh;
            }
            Display.Mode now;
            try {
                now = externalDisplay.getMode();
            } catch (Exception e) {
                return;
            }
            if (now == null) return;
            boolean moved = now.getPhysicalWidth() == wantW && now.getPhysicalHeight() == wantH
                    && Math.abs(now.getRefreshRate() - wantHz) < 1.5f;
            if (moved) return;
            panelScalerLocked = true;
            Log.i(TAG, "Sink ignored mode switch; native " + now.getPhysicalWidth() + "x"
                    + now.getPhysicalHeight() + "@" + Math.round(now.getRefreshRate())
                    + " held — using render scaling");
            applyOutputMode();
            callbacks.onSwapStateChanged(true);
        }, 2500L);
    }

    // True once the connected sink has been observed to ignore a real mode switch (phone is scaling).
    public boolean isPanelScaling() {
        return panelScalerLocked;
    }

    // The panel's actual output mode, e.g. "3440 × 1440 · 60 Hz" — shown when the phone is scaling.
    public String getPanelNativeSummary() {
        if (externalDisplay == null) return "";
        try {
            Display.Mode m = externalDisplay.getMode();
            if (m == null) return "";
            return m.getPhysicalWidth() + " × " + m.getPhysicalHeight()
                    + " · " + Math.round(m.getRefreshRate()) + " Hz";
        } catch (Exception e) {
            return "";
        }
    }

    private void setRenderBuffer(int w, int h) {
        if (w <= 0 || h <= 0) return;
        try { gameView.getHolder().setFixedSize(w, h); } catch (Exception ignore) {}
    }

    private void clearRenderBuffer() {
        try { gameView.getHolder().setSizeFromLayout(); } catch (Exception ignore) {}
    }

    // Push the external surface toward a refresh rate (API 30+). FIXED_SOURCE + CHANGE_FRAME_RATE_ALWAYS
    // is the most aggressive public lever — it requests a non-seamless switch to the panel's nearest
    // matching mode rather than only a seamless one. Still a vote: a no-op if the panel can't produce it.
    private void requestSurfaceFrameRate(float hz) {
        if (hz <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        try {
            Surface s = gameView.getHolder().getSurface();
            if (s == null || !s.isValid()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                s.setFrameRate(hz, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                        Surface.CHANGE_FRAME_RATE_ALWAYS);
            } else {
                s.setFrameRate(hz, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
            }
        } catch (Exception ignore) {}
    }

    // ── Game mode (HDMI ALLM / minimal post-processing) ────────────────────

    public boolean isGameModeSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || externalDisplay == null) return false;
        try {
            return externalDisplay.isMinimalPostProcessingSupported();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isGameModeEnabled() {
        return gameMode;
    }

    public void setGameMode(boolean enabled) {
        gameMode = enabled;
        applyGameMode();
    }

    private void applyGameMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        if (presentation == null || presentation.getWindow() == null) return;
        try {
            if (isGameModeSupported()) {
                presentation.getWindow().setPreferMinimalPostProcessing(gameMode);
            }
        } catch (Exception ignore) {}
    }

    // ── Viture XR glasses (USB control, only when Viture glasses are connected) ──

    public boolean isVitureConnected() {
        return viture.isConnected();
    }

    public String getVitureName() {
        return viture.modelName();
    }

    public boolean vitureSupportsBrightness() {
        return viture.supportsBrightness();
    }

    public boolean vitureSupportsFilm() {
        return viture.supportsFilm();
    }

    public boolean vitureFilmStepped() {
        return viture.filmIsStepped();
    }

    public boolean vitureSupports3D() {
        return viture.supports3D();
    }

    public int getVitureBrightnessMax() {
        return viture.brightnessMax();
    }

    public int getVitureBrightness() {
        return GlassesManager.INSTANCE.currentBrightness();
    }

    public int getVitureFilm() {
        return GlassesManager.INSTANCE.isSunblock() ? 1 : 0;
    }

    public boolean isViture3D() {
        return GlassesManager.INSTANCE.is3D();
    }

    public boolean vitureSupportsVolume() {
        return viture.supportsVolume();
    }

    public int getVitureVolumeMax() {
        return viture.volumeMax();
    }

    public int getVitureVolume() {
        return GlassesManager.INSTANCE.currentVolume();
    }

    public void setVitureVolume(int level) {
        GlassesManager.INSTANCE.setVolume(level);
    }

    public void setVitureBrightness(int level) {
        GlassesManager.INSTANCE.setBrightness(level);
    }

    public void setVitureFilm(int level) {
        GlassesManager.INSTANCE.setSunblock(level > 0);
    }

    public void setViture3D(boolean enabled) {
        GlassesManager.INSTANCE.set3D(enabled);
    }

    // ── Phone refresh / present mode (touch-lag mitigation) ────────────────

    // Keep the phone panel at its max refresh while an external display is attached.
    private void applyHighRefreshToPhone() {
        if (activity.getWindow() == null || displayManager == null) return;
        Display def = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (def == null) return;
        float maxRate = 0f;
        for (Display.Mode m : def.getSupportedModes()) maxRate = Math.max(maxRate, m.getRefreshRate());
        if (maxRate <= 0f) return;
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        savedPhoneRefreshRate = lp.preferredRefreshRate;
        lp.preferredRefreshRate = maxRate;
        activity.getWindow().setAttributes(lp);
    }

    private void restorePhoneRefresh() {
        if (activity.getWindow() == null) return;
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.preferredRefreshRate = savedPhoneRefreshRate;
        activity.getWindow().setAttributes(lp);
    }

    private void applyExternalPresentMode() {
        VulkanRenderer r = gameView.getRenderer();
        if (r == null) return;
        savedPresentMode = r.getPresentMode();
        r.setPresentMode(VulkanRenderer.PRESENT_MODE_MAILBOX);
    }

    private void restorePresentMode() {
        VulkanRenderer r = gameView.getRenderer();
        if (r != null) r.setPresentMode(savedPresentMode);
    }

    // ── Aspect ratio (fill mode) ───────────────────────────────────────────

    public int getFillMode() {
        return fillMode;
    }

    public void selectFillMode(int mode) {
        fillMode = mode;
        applyFillModeToRenderer();
    }

    private void applyFillModeToRenderer() {
        VulkanRenderer renderer = gameView.getRenderer();
        if (renderer != null) renderer.setFillMode(fillMode);
    }

    // ── Small helpers ──────────────────────────────────────────────────────

    private static boolean containsRes(List<int[]> list, int w, int h) {
        for (int[] r : list) if (r[0] == w && r[1] == h) return true;
        return false;
    }

    private boolean containsResEntry(int w, int h) {
        for (ResEntry r : resolutions) if (r.w == w && r.h == h) return true;
        return false;
    }

    private static void addRate(List<Float> list, float r) {
        if (r <= 0f) return;
        for (float e : list) if (Math.abs(e - r) < REFRESH_EPSILON) return;
        list.add(r);
    }

    private int closestRateIndex(float target) {
        int best = 0;
        float bestDiff = Float.MAX_VALUE;
        for (int i = 0; i < refreshOptions.size(); i++) {
            float diff = Math.abs(refreshOptions.get(i) - target);
            if (diff < bestDiff) { bestDiff = diff; best = i; }
        }
        return best;
    }

    private static String resLabel(int w, int h, boolean isNative) {
        String base;
        switch (h) {
            case 2160: base = "2160p · 4K"; break;
            case 1440: base = "1440p · 2K"; break;
            case 1080: base = "1080p"; break;
            case 720:  base = "720p"; break;
            case 480:  base = "480p"; break;
            default:   base = w + " × " + h; break;
        }
        return isNative ? base + " · native" : base;
    }

    private static int clampIndex(int index, int size) {
        if (size <= 0) return 0;
        return Math.max(0, Math.min(index, size - 1));
    }

    private static float round1(float v) {
        return Math.round(v * 10f) / 10f;
    }

    // Black full-screen presentation whose container holds the moved game view.
    private static final class GamePresentation extends Presentation {
        private FrameLayout container;

        GamePresentation(Context outerContext, Display display) {
            super(outerContext, display);
        }

        FrameLayout getContainer() {
            return container;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            container = new FrameLayout(getContext());
            container.setBackgroundColor(Color.BLACK);
            container.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setContentView(container);
            View decor = getWindow() != null ? getWindow().getDecorView() : null;
            if (decor != null) {
                decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        }
    }
}
