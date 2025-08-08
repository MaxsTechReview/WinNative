//package com.winlator.cmod.xenvironment.components;
//
//import android.util.SparseArray;
//
//import androidx.annotation.Keep;
//
//import com.winlator.cmod.xconnector.Client;
//import com.winlator.cmod.xconnector.ConnectionHandler;
//import com.winlator.cmod.xconnector.RequestHandler;
//import com.winlator.cmod.xconnector.XConnectorEpoll;
//import com.winlator.cmod.xconnector.UnixSocketConfig;
//import com.winlator.cmod.xenvironment.EnvironmentComponent;
//import com.winlator.cmod.xenvironment.XEnvironment;
//import com.winlator.cmod.XServerDisplayActivity;
//import com.winlator.cmod.renderer.GPUImage;
//import com.winlator.cmod.renderer.GLRenderer;
//import com.winlator.cmod.renderer.Texture;
//import com.winlator.cmod.widget.XServerView;
//import com.winlator.cmod.xserver.Drawable;
//import com.winlator.cmod.xserver.Window;
//import com.winlator.cmod.xserver.XServer;
//
//import java.io.IOException;
//import java.util.ArrayDeque;
//import java.util.Objects;
//
//public class VortekRendererComponent extends EnvironmentComponent
//        implements ConnectionHandler, RequestHandler {
//
//    // ---------------------------------------------------------------------------------------------
//    // Instance Fields
//    // ---------------------------------------------------------------------------------------------
//    private XConnectorEpoll connector;
//    private String[] disabledDeviceExtensions;  // default null
//    private int memoryHeapSize;                 // default 0x1000
//    private final UnixSocketConfig socketConfig;
//    private final XServer xServer;
//
//    // ---------------------------------------------------------------------------------------------
//    // Static Initializer (Class Initialization)
//    // ---------------------------------------------------------------------------------------------
//    static {
//        // Called once when the class is loaded. This loads the native library "vortekrenderer".
//        System.loadLibrary("vortekrenderer");
//    }
//
//    // ---------------------------------------------------------------------------------------------
//    // Constructor
//    // ---------------------------------------------------------------------------------------------
//    public VortekRendererComponent(XServer xServer, UnixSocketConfig socketConfig) {
//        super();
//        this.disabledDeviceExtensions = null;
//        this.memoryHeapSize = 0x1000;   // 4096 in decimal
//        this.xServer = xServer;
//        this.socketConfig = socketConfig;
//    }
//
//    // ---------------------------------------------------------------------------------------------
//    // Native Methods (private)
//    //
//    // These are declared 'private native' in Smali. They’re presumably implemented in the
//    // "vortekrenderer" native library (libvortekrenderer.so).
//    // ---------------------------------------------------------------------------------------------
//    private native long createVkContext(int fd, int memoryHeapSize, String[] disabledDeviceExtensions);
//    private native void destroyVkContext(long contextPtr);
//    private native boolean isVkDeviceCreated(long contextPtr);
//
//    // ---------------------------------------------------------------------------------------------
//    // Private Utility Methods
//    // ---------------------------------------------------------------------------------------------
//
//    /**
//     * Return a pointer to the native hardware buffer for the specified window.
//     * If the window doesn't exist, returns 0L.
//     */
//    @Keep
//    private long getWindowHardwareBuffer(int windowId) {
//        Window window = xServer.windowManager.getWindow(windowId);
//        if (window == null) {
//            return 0L;
//        }
//
//        // Let the XServerDisplayActivity know this window is active for frame rating
//        XEnvironment env = this.environment;
//        if (env != null) {
//            XServerDisplayActivity activity = (XServerDisplayActivity) env.getContext();
//            if (activity != null) {
//                activity.setFrameRatingWindowId(windowId);
//            }
//        }
//
//        Drawable drawable = window.getContent();
//        Texture texture = drawable.getTexture();
//
//        // If the texture is not a GPUImage, we replace it
//        if (!(texture instanceof GPUImage)) {
//            GLRenderer renderer = xServer.getRenderer();
//            XServerView xServerView = renderer.xServerView;
//
//            // Queue a GL thread event to presumably free or reassign old textures
//            Objects.requireNonNull(texture);
//            xServerView.queueEvent(() -> {
//                // In Smali, it references an ExternalSyntheticLambda0, which simply sets
//                // texture = null or does some cleanup. We can’t see its contents,
//                // but presumably it's disposing the old texture on the GL thread.
//            });
//
//            // Create a new GPUImage and assign it to the drawable
//            GPUImage newGpuImage = new GPUImage(drawable.width, drawable.height, false);
//            drawable.setTexture(newGpuImage);
//        }
//
//        // Now that we've guaranteed a GPUImage, get its native buffer pointer.
//        GPUImage gpuImage = (GPUImage) drawable.getTexture();
//        return gpuImage.getHardwareBufferPtr();
//    }
//
//    /**
//     * Return the width of the specified window, or 0 if it doesn’t exist.
//     */
//    @Keep
//    private int getWindowWidth(int windowId) {
//        Window window = xServer.windowManager.getWindow(windowId);
//        if (window != null) {
//            return window.getWidth();
//        }
//        return 0;
//    }
//
//    /**
//     * Return the height of the specified window, or 0 if it doesn’t exist.
//     */
//    @Keep
//    private int getWindowHeight(int windowId) {
//        Window window = xServer.windowManager.getWindow(windowId);
//        if (window != null) {
//            return window.getHeight();
//        }
//        return 0;
//    }
//
//    /**
//     * Return the stride of the GPUImage for the specified window, or 0 if not found.
//     */
//    @Keep
//    private int getHardwareBufferStride(int windowId) {
//        // This method first calls getWindowHardwareBuffer(windowId),
//        // presumably to ensure the buffer is up-to-date. Then it fetches the window’s GPUImage stride.
//
//        getWindowHardwareBuffer(windowId); // discard the returned pointer
//
//        Window window = xServer.windowManager.getWindow(windowId);
//        if (window != null) {
//            Drawable drawable = window.getContent();
//            Texture texture = drawable.getTexture();
//            if (texture instanceof GPUImage) {
//                return ((GPUImage) texture).getStride();
//            }
//        }
//        return 0;
//    }
//
//    /**
//     * Force the XServer to re-render (update) the window's Drawable.
//     */
//    @Keep
//    private void updateWindowContent(int windowId) {
//        Window window = xServer.windowManager.getWindow(windowId);
//        if (window != null) {
//            Drawable drawable = window.getContent();
//            synchronized (drawable.renderLock) {
//                drawable.forceUpdate();
//            }
//        }
//    }
//
//    /**
//     * Searches through all clients in the connector and kills (destroys) those that do not have
//     * a valid Vulkan device context.
//     */
//    private void killClientsWithNoDevice() {
//        SparseArray<Client> clients = connector.getClients();
//        ArrayDeque<Client> destroyedClients = new ArrayDeque<>();
//
//        // Check each client to see if it has a valid device context
//        for (int i = 0; i < clients.size(); i++) {
//            Client client = clients.valueAt(i);
//            Long contextPtrObj = (Long) client.getTag();
//            if (contextPtrObj != null) {
//                long contextPtr = contextPtrObj;
//                // If the device is not created, destroy the context and mark the client for removal
//                if (!isVkDeviceCreated(contextPtr)) {
//                    destroyVkContext(contextPtr);
//                    client.setTag(null);
//                    destroyedClients.add(client);
//                }
//            }
//        }
//
//        // Kill connections for each invalid client
//        while (!destroyedClients.isEmpty()) {
//            Client clientToKill = destroyedClients.poll();
//            connector.killConnection(clientToKill);
//        }
//    }
//
//    // ---------------------------------------------------------------------------------------------
//    // ConnectionHandler / RequestHandler Implementations
//    // ---------------------------------------------------------------------------------------------
//
//    @Override
//    public void handleConnectionShutdown(Client client) {
//        // If the client had a valid contextPtr, destroy it
//        Object tag = client.getTag();
//        if (tag != null) {
//            long contextPtr = ((Long) tag).longValue();
//            destroyVkContext(contextPtr);
//        }
//    }
//
//    @Override
//    public void handleNewConnection(Client client) {
//        // Clear out invalid clients first
//        killClientsWithNoDevice();
//
//        // Attempt to create a new VkContext for the incoming client
//        int fd = client.clientSocket.fd;
//        long contextPtr = createVkContext(fd, memoryHeapSize, disabledDeviceExtensions);
//
//        if (contextPtr > 0) {
//            client.setTag(Long.valueOf(contextPtr));
//        } else {
//            // If creation fails, kill the connection
//            connector.killConnection(client);
//        }
//    }
//
//    @Override
//    public boolean handleRequest(Client client) throws IOException {
//        // The smali simply returns true (const/4 v0, 0x1)
//        return true;
//    }
//
//    // ---------------------------------------------------------------------------------------------
//    // Public Methods
//    // ---------------------------------------------------------------------------------------------
//
//    /**
//     * This method sets which Vulkan device extensions should be disabled, if any.
//     */
//    public void setDisabledDeviceExtensions(String... disabledDeviceExtensions) {
//        this.disabledDeviceExtensions = disabledDeviceExtensions;
//    }
//
//    /**
//     * Starts the Vortek renderer connector if it hasn’t been started yet.
//     */
//    @Override
//    public void start() {
//        if (this.connector != null) {
//            // Already started
//            return;
//        }
//        this.connector = new XConnectorEpoll(socketConfig, this, this);
//        // The smali shows setMonitorClients(false)
//        this.connector.setMonitorClients(false);
//        this.connector.start();
//    }
//
//    /**
//     * Stops the Vortek renderer connector and cleans up the reference.
//     */
//    @Override
//    public void stop() {
//        if (this.connector != null) {
//            this.connector.stop();
//            this.connector = null;
//        }
//    }
//}
