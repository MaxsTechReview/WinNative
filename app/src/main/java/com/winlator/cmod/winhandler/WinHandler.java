package com.winlator.cmod.winhandler;

import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.ExternalController;
import com.winlator.cmod.inputcontrols.FakeInputWriter;
import com.winlator.cmod.inputcontrols.GamepadState;
import com.winlator.cmod.xserver.XServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WinHandler {
    private static final short CLIENT_PORT = 7946;
    public static final byte DEFAULT_INPUT_TYPE = 4;
    public static final byte FLAG_INPUT_TYPE_DINPUT = 8;
    public static final byte FLAG_INPUT_TYPE_XINPUT = 4;
    public static final byte FLAG_DINPUT_MAPPER_STANDARD = 1;
    public static final byte FLAG_DINPUT_MAPPER_XINPUT = 2;
    public static final byte INPUT_TYPE_MIXED = 2;
    private static final int GAMEPAD_SOURCE_NONE = 0;
    private static final int GAMEPAD_SOURCE_VIRTUAL = 1;
    private static final int GAMEPAD_SOURCE_CONTROLLER = 2;
    private static final int MAX_CONTROLLERS = 4;
    private static final int OSC_DEVICE_ID = -1;
    private static final short SERVER_PORT = 7947;
    private static final float GYRO_AXIS_EPSILON = 0.001f;
    private static final float GYRO_TRIGGER_PRESS_THRESHOLD = 0.15f;
    private final XServerDisplayActivity activity;
    private String fakeInputBasePath;
    private final InputManager inputManager;
    private InetAddress localhost;
    private OnGetProcessInfoListener onGetProcessInfoListener;
    private SharedPreferences preferences;
    private DatagramSocket socket;
    private boolean xinputDisabled;
    private final ByteBuffer sendData = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer receiveData = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    private final DatagramPacket sendPacket = new DatagramPacket(this.sendData.array(), 64);
    private final DatagramPacket receivePacket = new DatagramPacket(this.receiveData.array(), 64);
    private final ArrayDeque<Runnable> actions = new ArrayDeque<>();
    private boolean initReceived = false;
    private boolean running = false;
    private final Map<Integer, ExternalController> controllers = new HashMap();
    private byte inputType = 4;
    private final List<Integer> gamepadClients = new CopyOnWriteArrayList();
    private FakeInputWriter[] writers = new FakeInputWriter[4];
    private Map<Integer, Integer> deviceToSlot = new HashMap();
    private Map<String, Integer> descriptorToSlot = new HashMap<>();  // physical device → slot
    private Map<Integer, String> deviceToDescriptor = new HashMap<>(); // deviceId → descriptor
    private Set<Integer> usedSlots = new HashSet();
    private boolean xinputDisabledInitialized = false;
    private ExternalController currentController;
    private final GamepadState outputGamepadState = new GamepadState();
    private int lastGamepadSource = 0;
    private float smoothedGyroX = 0.0f;
    private float smoothedGyroY = 0.0f;
    private float currentGyroStickX = 0.0f;
    private float currentGyroStickY = 0.0f;
    private boolean gyroToggleEnabled = false;
    private boolean gyroActivatorPressed = false;
    private int lastGyroTargetSource = 0;
    private ExternalController lastGyroTargetController;
    private final InputManager.InputDeviceListener inputDeviceListener = new InputManager.InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int deviceId) {
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            WinHandler.this.releaseSlot(deviceId);
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
        }
    };

    public WinHandler(XServerDisplayActivity activity) {
        this.activity = activity;
        this.inputManager = (InputManager) activity.getSystemService("input");
        this.inputManager.registerInputDeviceListener(this.inputDeviceListener, null);
        this.preferences = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
    }

    public DatagramSocket getSocket() {
        return this.socket;
    }

    private boolean sendPacket(int port) throws IOException {
        try {
            int size = this.sendData.position();
            if (size == 0) {
                return false;
            }
            this.sendPacket.setAddress(this.localhost);
            this.sendPacket.setPort(port);
            this.socket.send(this.sendPacket);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void exec(String command) {
        final String filename;
        final String parameters;
        String command2 = command.trim();
        if (command2.isEmpty()) {
            return;
        }
        if (command2.contains("\"")) {
            int firstQuote = command2.indexOf("\"");
            int lastQuote = command2.lastIndexOf("\"");
            filename = command2.substring(firstQuote + 1, lastQuote);
            if (lastQuote + 1 < command2.length()) {
                parameters = command2.substring(lastQuote + 1).trim();
            } else {
                parameters = "";
            }
        } else {
            String[] cmdList = command2.split(" ", 2);
            filename = cmdList[0];
            if (cmdList.length > 1) {
                parameters = cmdList[1];
            } else {
                parameters = "";
            }
        }
        addAction(() -> {
            try {
                byte[] filenameBytes = filename.getBytes();
                byte[] parametersBytes = parameters.getBytes();
                this.sendData.rewind();
                this.sendData.put((byte) 2);
                this.sendData.putInt(filenameBytes.length + parametersBytes.length + 8);
                this.sendData.putInt(filenameBytes.length);
                this.sendData.putInt(parametersBytes.length);
                this.sendData.put(filenameBytes);
                this.sendData.put(parametersBytes);
                sendPacket(CLIENT_PORT);
            } catch (IOException ignored) {
            }
        });
    }

    public void killProcess(final String processName) {
        addAction(() -> {
            try {
                this.sendData.rewind();
                this.sendData.put((byte) 3);
                byte[] bytes = processName.getBytes();
                this.sendData.putInt(bytes.length);
                this.sendData.put(bytes);
                sendPacket(CLIENT_PORT);
            } catch (IOException ignored) {
            }
        });
    }

    public void listProcesses() {
        addAction(() -> {
            try {
                this.sendData.rewind();
                this.sendData.put((byte) 4);
                this.sendData.putInt(0);
                if (!sendPacket(CLIENT_PORT) && this.onGetProcessInfoListener != null) {
                    this.onGetProcessInfoListener.onGetProcessInfo(0, 0, null);
                }
            } catch (IOException ignored) {
            }
        });
    }

    public void setProcessAffinity(final String processName, final int affinityMask) {
        addAction(() -> {
            try {
                byte[] bytes = processName.getBytes();
                this.sendData.rewind();
                this.sendData.put((byte) 6);
                this.sendData.putInt(bytes.length + 9);
                this.sendData.putInt(0);
                this.sendData.putInt(affinityMask);
                this.sendData.put((byte) bytes.length);
                this.sendData.put(bytes);
                sendPacket(CLIENT_PORT);
            } catch (IOException ignored) {
            }
        });
    }

    public void setProcessAffinity(final int pid, final int affinityMask) {
        addAction(() -> {
            try {
                this.sendData.rewind();
                this.sendData.put((byte) 6);
                this.sendData.putInt(9);
                this.sendData.putInt(pid);
                this.sendData.putInt(affinityMask);
                this.sendData.put((byte) 0);
                sendPacket(CLIENT_PORT);
            } catch (IOException ignored) {
            }
        });
    }

    public void mouseEvent(final int flags, final int dx, final int dy, final int wheelDelta) {
        if (!this.initReceived) {
            return;
        }
        addAction(() -> {
            try {
                this.sendData.rewind();
                this.sendData.put((byte) 7);
                this.sendData.putInt(10);
                this.sendData.putInt(flags);
                this.sendData.putShort((short) dx);
                this.sendData.putShort((short) dy);
                this.sendData.putShort((short) wheelDelta);
                this.sendData.put((byte) ((flags & 1) != 0 ? 1 : 0));
                sendPacket(CLIENT_PORT);
            } catch (IOException ignored) {
            }
        });
    }

    public void keyboardEvent(final byte vkey, final int flags) {
        if (!this.initReceived) {
            return;
        }
        addAction(() -> {
            try {
                this.sendData.rewind();
                this.sendData.put((byte) 11);
                this.sendData.put(vkey);
                this.sendData.putInt(flags);
                sendPacket(CLIENT_PORT);
            } catch (IOException ignored) {
            }
        });
    }

    public void bringToFront(String processName) {
        bringToFront(processName, 0L);
    }

    public void bringToFront(final String processName, final long handle) {
        addAction(() -> {
            try {
                this.sendData.rewind();
                this.sendData.put((byte) 12);
                byte[] bytes = processName.getBytes();
                this.sendData.putInt(bytes.length);
                this.sendData.put(bytes);
                this.sendData.putLong(handle);
                sendPacket(CLIENT_PORT);
            } catch (BufferOverflowException e) {
                e.printStackTrace();
                this.sendData.rewind();
            } catch (IOException ignored) {
            }
        });
    }

    private void addAction(Runnable action) {
        synchronized (this.actions) {
            this.actions.add(action);
            this.actions.notify();
        }
    }

    public OnGetProcessInfoListener getOnGetProcessInfoListener() {
        return this.onGetProcessInfoListener;
    }

    public void setOnGetProcessInfoListener(OnGetProcessInfoListener onGetProcessInfoListener) {
        synchronized (this.actions) {
            this.onGetProcessInfoListener = onGetProcessInfoListener;
        }
    }

    private void startSendThread() {
        Executors.newSingleThreadExecutor().execute(() -> {
            while (this.running) {
                synchronized (this.actions) {
                    while (this.initReceived && !this.actions.isEmpty()) {
                        this.actions.poll().run();
                    }
                    try {
                        this.actions.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        });
    }

    public void stop() {
        this.running = false;
        closeFakeInputWriter();
        if (this.socket != null) {
            this.socket.close();
            this.socket = null;
        }
        synchronized (this.actions) {
            this.actions.notify();
        }
    }

    private void handleRequest(byte requestCode, int port) {
        switch (requestCode) {
            case 1:
                this.initReceived = true;
                this.preferences = PreferenceManager.getDefaultSharedPreferences(this.activity.getBaseContext());
                if (!this.xinputDisabledInitialized) {
                    this.xinputDisabled = this.preferences.getBoolean("xinput_toggle", false);
                }
                synchronized (this.actions) {
                    this.actions.notify();
                }
                return;
            case 5:
                if (this.onGetProcessInfoListener != null) {
                    this.receiveData.position(this.receiveData.position() + 4);
                    int numProcesses = this.receiveData.getShort();
                    int index = this.receiveData.getShort();
                    int pid = this.receiveData.getInt();
                    long memoryUsage = this.receiveData.getLong();
                    int affinityMask = this.receiveData.getInt();
                    boolean wow64Process = this.receiveData.get() == 1;
                    byte[] bytes = new byte[32];
                    this.receiveData.get(bytes);
                    String name = StringUtils.fromANSIString(bytes);
                    this.onGetProcessInfoListener.onGetProcessInfo(index, numProcesses, new ProcessInfo(pid, name, memoryUsage, affinityMask, wow64Process));
                }
                return;
            case 10:
            case 13:
                short x = this.receiveData.getShort();
                short y = this.receiveData.getShort();
                XServer xServer = this.activity.getXServer();
                xServer.pointer.setX(x);
                xServer.pointer.setY(y);
                this.activity.getXServerView().requestRender();
                return;
            default:
                return;
        }
    }

    public void start() {
        try {
            this.localhost = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            try {
                this.localhost = InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException e2) {
            }
        }
        this.running = true;
        startSendThread();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                this.socket = new DatagramSocket((SocketAddress) null);
                this.socket.setReuseAddress(true);
                this.socket.bind(new InetSocketAddress((InetAddress) null, SERVER_PORT));
                while (this.running) {
                    this.socket.receive(this.receivePacket);
                    synchronized (this.actions) {
                        this.receiveData.rewind();
                        byte requestCode = this.receiveData.get();
                        handleRequest(requestCode, this.receivePacket.getPort());
                    }
                }
            } catch (IOException e) {
            }
        });
    }

    public void sendGamepadState() {
        setLastGamepadSource(GAMEPAD_SOURCE_VIRTUAL, null);
        maybeClearGyroTarget(GAMEPAD_SOURCE_VIRTUAL, null);
        writeVirtualGamepadState(shouldApplyGyroToTarget(GAMEPAD_SOURCE_VIRTUAL, null));
    }

    private void writeVirtualGamepadState(boolean applyGyroOverlay) {
        ControlsProfile profile = this.activity.getInputControlsView().getProfile();
        if (profile == null) {
            return;
        }
        GamepadState gamepadState = profile.getGamepadState();
        boolean useVirtualGamepad = profile.isVirtualGamepad() && this.activity.getInputControlsView().isShowTouchscreenControls();
        if (useVirtualGamepad) {
            int slot = assignSlot(-1);
            if (slot >= 0 && this.writers[slot] != null) {
                try {
                    this.writers[slot].writeGamepadState(getOutputGamepadState(gamepadState, applyGyroOverlay));
                } catch (IOException ignored) {
                }
                return;
            }
            return;
        }
        releaseSlot(-1);
    }

    public void sendGamepadState(ExternalController controller) {
        if (controller != null) {
            this.currentController = controller;
        }
        setLastGamepadSource(GAMEPAD_SOURCE_CONTROLLER, controller);
        maybeClearGyroTarget(GAMEPAD_SOURCE_CONTROLLER, controller);
        writeControllerGamepadState(controller, shouldApplyGyroToTarget(GAMEPAD_SOURCE_CONTROLLER, controller));
    }

    private void writeControllerGamepadState(ExternalController controller, boolean applyGyroOverlay) {
        ExternalController profileController;
        if (controller == null) {
            return;
        }
        ControlsProfile profile = this.activity.getInputControlsView().getProfile();
        if (profile != null && (profileController = profile.getController(controller.getDeviceId())) != null && profileController.getControllerBindingCount() > 0) {
            int slot = assignSlot(controller.getDeviceId());
            if (slot >= 0 && this.writers[slot] != null) {
                try {
                    this.writers[slot].writeGamepadState(getOutputGamepadState(controller.remappedState, applyGyroOverlay));
                } catch (IOException ignored) {
                }
                return;
            }
            return;
        }
        int slot2 = assignSlot(controller.getDeviceId());
        if (slot2 >= 0 && this.writers[slot2] != null) {
            try {
                this.writers[slot2].writeGamepadState(getOutputGamepadState(controller.state, applyGyroOverlay));
            } catch (IOException ignored) {
            }
        }
    }

    private int assignSlot(int deviceId) {
        // Fast path: already assigned
        Integer existing = this.deviceToSlot.get(deviceId);
        if (existing != null) {
            return existing;
        }

        // Resolve the physical device descriptor to group sub-devices (e.g. DualSense
        // gamepad + touchpad + motion sensors all share one physical controller)
        String descriptor = null;
        android.view.InputDevice device = android.view.InputDevice.getDevice(deviceId);
        if (device != null) {
            descriptor = device.getDescriptor();
        }

        // If another deviceId from the same physical controller already has a slot, reuse it
        if (descriptor != null) {
            Integer descriptorSlot = this.descriptorToSlot.get(descriptor);
            if (descriptorSlot != null) {
                this.deviceToSlot.put(deviceId, descriptorSlot);
                this.deviceToDescriptor.put(deviceId, descriptor);
                Log.d("WinHandler", "Mapped device " + deviceId + " to existing slot " + descriptorSlot + " (same physical controller: " + descriptor + ")");
                return descriptorSlot;
            }
        }

        // Assign a new slot
        for (int slot = 0; slot < 4; slot++) {
            if (!this.usedSlots.contains(slot)) {
                this.usedSlots.add(slot);
                this.deviceToSlot.put(deviceId, slot);
                if (descriptor != null) {
                    this.descriptorToSlot.put(descriptor, slot);
                    this.deviceToDescriptor.put(deviceId, descriptor);
                }
                if (this.fakeInputBasePath != null && this.writers[slot] == null) {
                    this.writers[slot] = new FakeInputWriter(this.fakeInputBasePath, slot);
                    this.writers[slot].open();
                    Log.d("WinHandler", "Assigned device " + deviceId + " to slot " + slot + " (descriptor: " + descriptor + ")");
                }
                return slot;
            }
        }
        Log.w("WinHandler", "No slots available for device " + deviceId);
        return -1;
    }

    private void releaseSlot(int deviceId) {
        Integer slot = this.deviceToSlot.remove(deviceId);
        if (slot != null) {
            // Check if any other deviceId still maps to this slot (same physical controller)
            String descriptor = this.deviceToDescriptor.remove(deviceId);
            boolean slotStillInUse = false;
            if (descriptor != null) {
                for (Map.Entry<Integer, String> entry : this.deviceToDescriptor.entrySet()) {
                    if (descriptor.equals(entry.getValue()) && this.deviceToSlot.containsKey(entry.getKey())) {
                        slotStillInUse = true;
                        break;
                    }
                }
                if (!slotStillInUse) {
                    this.descriptorToSlot.remove(descriptor);
                }
            }

            if (!slotStillInUse) {
                if (this.writers[slot] != null) {
                    this.writers[slot].softRelease();
                }
                this.usedSlots.remove(slot);
                Log.d("WinHandler", "Device " + deviceId + " disconnected. Slot " + slot + " released.");
            } else {
                Log.d("WinHandler", "Device " + deviceId + " removed but slot " + slot + " still used by sibling sub-device.");
            }
            this.controllers.remove(deviceId);
        }
    }

    public void setXInputDisabled(boolean disabled) {
        this.xinputDisabled = disabled;
        this.xinputDisabledInitialized = true;
        Log.d("WinHandler", "XInput Disabled set to: " + this.xinputDisabled);
    }

    public void setFakeInputPath(String fakeInputPath) {
        if (fakeInputPath != null && !fakeInputPath.isEmpty()) {
            this.fakeInputBasePath = fakeInputPath;
            Log.d("WinHandler", "FakeInputWriter base path set: " + fakeInputPath);
        }
    }

    public void closeFakeInputWriter() {
        if (this.inputManager != null && this.inputDeviceListener != null) {
            this.inputManager.unregisterInputDeviceListener(this.inputDeviceListener);
        }
        for (int i = 0; i < 4; i++) {
            if (this.writers[i] != null) {
                this.writers[i].destroy();
                this.writers[i] = null;
            }
        }
        this.deviceToSlot.clear();
        this.descriptorToSlot.clear();
        this.deviceToDescriptor.clear();
        this.usedSlots.clear();
        this.controllers.clear();
        this.currentController = null;
        this.lastGamepadSource = GAMEPAD_SOURCE_NONE;
        this.smoothedGyroX = 0.0f;
        this.smoothedGyroY = 0.0f;
        this.currentGyroStickX = 0.0f;
        this.currentGyroStickY = 0.0f;
        this.gyroToggleEnabled = false;
        this.gyroActivatorPressed = false;
        this.lastGyroTargetSource = GAMEPAD_SOURCE_NONE;
        this.lastGyroTargetController = null;
    }

    private ExternalController getController(int deviceId) {
        if (this.controllers.containsKey(deviceId)) {
            return this.controllers.get(deviceId);
        }

        // Check if another deviceId from the same physical controller already has a controller instance
        android.view.InputDevice device = android.view.InputDevice.getDevice(deviceId);
        if (device != null) {
            String descriptor = device.getDescriptor();
            for (Map.Entry<Integer, ExternalController> entry : this.controllers.entrySet()) {
                android.view.InputDevice existing = android.view.InputDevice.getDevice(entry.getKey());
                if (existing != null && descriptor.equals(existing.getDescriptor())) {
                    // Reuse the same ExternalController for sibling sub-devices
                    this.controllers.put(deviceId, entry.getValue());
                    return entry.getValue();
                }
            }
        }

        ExternalController controller = ExternalController.getController(deviceId);
        if (controller != null) {
            this.controllers.put(deviceId, controller);
        }
        return controller;
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        boolean handled = false;
        int deviceId = event.getDeviceId();
        ExternalController controller = getController(deviceId);
        if (controller != null && (handled = controller.updateStateFromMotionEvent(event))) {
            this.currentController = controller;
            sendGamepadState(controller);
        }
        return handled;
    }

    public boolean onKeyEvent(KeyEvent event) {
        boolean handled = false;
        int deviceId = event.getDeviceId();
        ExternalController controller = getController(deviceId);
        if (controller != null && event.getRepeatCount() == 0) {
            int action = event.getAction();
            if (action == 0 || action == 1) {
                handled = controller.updateStateFromKeyEvent(event);
            }
            if (handled) {
                this.currentController = controller;
                sendGamepadState(controller);
            }
        }
        return handled;
    }

    public byte getInputType() {
        return this.inputType;
    }

    public void setInputType(byte inputType) {
        this.inputType = inputType;
    }

    public ExternalController getCurrentController() {
        return currentController;
    }

    public void initializeController() {
        currentController = getController(0);
    }

    public void updateGyroData(float rawGyroX, float rawGyroY) {
        GyroSettings gyroSettings = getGyroSettings();
        if (!gyroSettings.enabled) {
            this.smoothedGyroX = 0.0f;
            this.smoothedGyroY = 0.0f;
            this.currentGyroStickX = 0.0f;
            this.currentGyroStickY = 0.0f;
            this.gyroToggleEnabled = false;
            this.gyroActivatorPressed = false;
            clearLastGyroTarget();
            return;
        }

        if (Math.abs(rawGyroX) < gyroSettings.deadzone) rawGyroX = 0.0f;
        if (Math.abs(rawGyroY) < gyroSettings.deadzone) rawGyroY = 0.0f;
        if (gyroSettings.invertX) rawGyroX = -rawGyroX;
        if (gyroSettings.invertY) rawGyroY = -rawGyroY;

        rawGyroX *= gyroSettings.sensitivityX;
        rawGyroY *= gyroSettings.sensitivityY;

        this.smoothedGyroX = (this.smoothedGyroX * gyroSettings.smoothing) + (rawGyroX * (1.0f - gyroSettings.smoothing));
        this.smoothedGyroY = (this.smoothedGyroY * gyroSettings.smoothing) + (rawGyroY * (1.0f - gyroSettings.smoothing));

        int targetSource = resolveGyroTargetSource();
        ExternalController targetController = targetSource == GAMEPAD_SOURCE_CONTROLLER ? getPreferredGyroController() : null;
        if (targetSource == GAMEPAD_SOURCE_NONE) {
            clearLastGyroTarget();
            return;
        }

        if (targetSource == GAMEPAD_SOURCE_CONTROLLER && targetController == null) {
            clearLastGyroTarget();
            return;
        }

        GamepadState targetState = getTargetGamepadState(targetSource, targetController);
        if (targetState == null) {
            clearLastGyroTarget();
            return;
        }

        boolean gyroActive = updateGyroActivation(targetState, gyroSettings);
        float nextGyroStickX = gyroActive ? clamp(this.smoothedGyroX, -1.0f, 1.0f) : 0.0f;
        float nextGyroStickY = gyroActive ? clamp(this.smoothedGyroY, -1.0f, 1.0f) : 0.0f;

        boolean targetChanged = targetSource != this.lastGyroTargetSource
                || (targetSource == GAMEPAD_SOURCE_CONTROLLER && !isSameController(targetController, this.lastGyroTargetController));
        boolean valuesChanged = Math.abs(nextGyroStickX - this.currentGyroStickX) > GYRO_AXIS_EPSILON
                || Math.abs(nextGyroStickY - this.currentGyroStickY) > GYRO_AXIS_EPSILON;

        if (!targetChanged && !valuesChanged) {
            return;
        }

        if (targetChanged) {
            clearLastGyroTarget();
        }

        this.currentGyroStickX = nextGyroStickX;
        this.currentGyroStickY = nextGyroStickY;

        if (targetSource == GAMEPAD_SOURCE_VIRTUAL) {
            writeVirtualGamepadState(gyroActive);
        } else {
            writeControllerGamepadState(targetController, gyroActive);
        }

        this.lastGyroTargetSource = gyroActive ? targetSource : GAMEPAD_SOURCE_NONE;
        this.lastGyroTargetController = gyroActive && targetSource == GAMEPAD_SOURCE_CONTROLLER ? targetController : null;
    }

    public void refreshControllerMappings() {
    }

    private GyroSettings getGyroSettings() {
        GyroSettings settings = new GyroSettings();
        settings.enabled = this.preferences.getBoolean("gyro_enabled", false);
        settings.mode = this.preferences.getInt("gyro_mode", 0);
        settings.activatorKeyCode = this.preferences.getInt("gyro_trigger_button", KeyEvent.KEYCODE_BUTTON_L1);
        settings.applyToRightStick = this.preferences.getBoolean("process_gyro_with_left_trigger", false);
        settings.sensitivityX = getFloatPreference("gyro_x_sensitivity", 1.0f);
        settings.sensitivityY = getFloatPreference("gyro_y_sensitivity", 1.0f);
        settings.smoothing = clamp(getFloatPreference("gyro_smoothing", 0.9f), 0.0f, 0.99f);
        settings.deadzone = clamp(getFloatPreference("gyro_deadzone", 0.05f), 0.0f, 1.0f);
        settings.invertX = this.preferences.getBoolean("invert_gyro_x", false);
        settings.invertY = this.preferences.getBoolean("invert_gyro_y", false);
        return settings;
    }

    private float getFloatPreference(String key, float defaultValue) {
        try {
            return this.preferences.getFloat(key, defaultValue);
        } catch (ClassCastException e) {
            try {
                int intValue = this.preferences.getInt(key, Integer.MIN_VALUE);
                if (intValue != Integer.MIN_VALUE) {
                    float floatValue = intValue / 100.0f;
                    this.preferences.edit().putFloat(key, floatValue).apply();
                    return floatValue;
                }
            } catch (ClassCastException ignored) {
            }
            return defaultValue;
        }
    }

    private int resolveGyroTargetSource() {
        if (this.lastGamepadSource == GAMEPAD_SOURCE_CONTROLLER && getPreferredGyroController() != null) {
            return GAMEPAD_SOURCE_CONTROLLER;
        }
        if (this.lastGamepadSource == GAMEPAD_SOURCE_VIRTUAL && canUseVirtualGamepad()) {
            return GAMEPAD_SOURCE_VIRTUAL;
        }
        if (canUseVirtualGamepad()) {
            return GAMEPAD_SOURCE_VIRTUAL;
        }
        return getPreferredGyroController() != null ? GAMEPAD_SOURCE_CONTROLLER : GAMEPAD_SOURCE_NONE;
    }

    private ExternalController getPreferredGyroController() {
        if (this.currentController == null) {
            return null;
        }
        int deviceId = this.currentController.getDeviceId();
        return deviceId >= 0 && android.view.InputDevice.getDevice(deviceId) != null ? this.currentController : null;
    }

    private boolean canUseVirtualGamepad() {
        ControlsProfile profile = this.activity.getInputControlsView().getProfile();
        return profile != null && profile.isVirtualGamepad() && this.activity.getInputControlsView().isShowTouchscreenControls();
    }

    private void setLastGamepadSource(int source, ExternalController controller) {
        if (source != this.lastGamepadSource) {
            this.gyroToggleEnabled = false;
            this.gyroActivatorPressed = false;
        }
        this.lastGamepadSource = source;
        if (controller != null) {
            this.currentController = controller;
        }
    }

    private void maybeClearGyroTarget(int nextSource, ExternalController nextController) {
        if (this.lastGyroTargetSource == GAMEPAD_SOURCE_NONE) {
            return;
        }
        if (nextSource == this.lastGyroTargetSource && (nextSource != GAMEPAD_SOURCE_CONTROLLER || isSameController(nextController, this.lastGyroTargetController))) {
            return;
        }
        clearLastGyroTarget();
    }

    private void clearLastGyroTarget() {
        if (this.lastGyroTargetSource == GAMEPAD_SOURCE_VIRTUAL) {
            writeVirtualGamepadState(false);
        } else if (this.lastGyroTargetSource == GAMEPAD_SOURCE_CONTROLLER && this.lastGyroTargetController != null) {
            writeControllerGamepadState(this.lastGyroTargetController, false);
        }
        this.lastGyroTargetSource = GAMEPAD_SOURCE_NONE;
        this.lastGyroTargetController = null;
    }

    private boolean shouldApplyGyroToTarget(int source, ExternalController controller) {
        GyroSettings gyroSettings = getGyroSettings();
        if (!gyroSettings.enabled) {
            return false;
        }
        int preferredSource = resolveGyroTargetSource();
        if (source != preferredSource) {
            return false;
        }
        if (source == GAMEPAD_SOURCE_CONTROLLER && !isSameController(controller, getPreferredGyroController())) {
            return false;
        }
        GamepadState targetState = getTargetGamepadState(source, controller);
        return targetState != null && updateGyroActivation(targetState, gyroSettings);
    }

    private GamepadState getTargetGamepadState(int source, ExternalController controller) {
        ControlsProfile profile = this.activity.getInputControlsView().getProfile();
        if (source == GAMEPAD_SOURCE_VIRTUAL) {
            return profile != null ? profile.getGamepadState() : null;
        }
        if (controller == null) {
            return null;
        }
        ExternalController profileController;
        if (profile != null && (profileController = profile.getController(controller.getDeviceId())) != null && profileController.getControllerBindingCount() > 0) {
            return controller.remappedState;
        }
        return controller.state;
    }

    private boolean updateGyroActivation(GamepadState targetState, GyroSettings gyroSettings) {
        boolean activatorPressed = isActivatorPressed(targetState, gyroSettings.activatorKeyCode);
        if (gyroSettings.mode == 1 && activatorPressed && !this.gyroActivatorPressed) {
            this.gyroToggleEnabled = !this.gyroToggleEnabled;
        }
        this.gyroActivatorPressed = activatorPressed;
        return gyroSettings.mode == 1 ? this.gyroToggleEnabled : activatorPressed;
    }

    private boolean isActivatorPressed(GamepadState state, int keyCode) {
        if (state == null) {
            return false;
        }
        int buttonIdx = ExternalController.getButtonIdxByKeyCode(keyCode);
        if (buttonIdx == GamepadState.BUTTON_L2) {
            return state.triggerL > GYRO_TRIGGER_PRESS_THRESHOLD || state.isPressed(GamepadState.BUTTON_L2);
        }
        if (buttonIdx == GamepadState.BUTTON_R2) {
            return state.triggerR > GYRO_TRIGGER_PRESS_THRESHOLD || state.isPressed(GamepadState.BUTTON_R2);
        }
        return buttonIdx != -1 && state.isPressed(buttonIdx);
    }

    private GamepadState getOutputGamepadState(GamepadState baseState, boolean applyGyroOverlay) {
        if (!applyGyroOverlay || baseState == null) {
            return baseState;
        }

        GyroSettings gyroSettings = getGyroSettings();
        this.outputGamepadState.copy(baseState);
        if (gyroSettings.applyToRightStick) {
            this.outputGamepadState.thumbRX = clamp(baseState.thumbRX + this.currentGyroStickX, -1.0f, 1.0f);
            this.outputGamepadState.thumbRY = clamp(baseState.thumbRY + this.currentGyroStickY, -1.0f, 1.0f);
        } else {
            this.outputGamepadState.thumbLX = clamp(baseState.thumbLX + this.currentGyroStickX, -1.0f, 1.0f);
            this.outputGamepadState.thumbLY = clamp(baseState.thumbLY + this.currentGyroStickY, -1.0f, 1.0f);
        }
        return this.outputGamepadState;
    }

    private boolean isSameController(ExternalController first, ExternalController second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        String firstId = first.getId();
        String secondId = second.getId();
        if (firstId != null && secondId != null) {
            return firstId.equals(secondId);
        }
        return first.getDeviceId() == second.getDeviceId();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class GyroSettings {
        boolean enabled;
        int mode;
        int activatorKeyCode;
        boolean applyToRightStick;
        float sensitivityX;
        float sensitivityY;
        float smoothing;
        float deadzone;
        boolean invertX;
        boolean invertY;
    }

    public void execWithDelay(final String command, int delaySeconds) {
        if (command == null || command.trim().isEmpty() || delaySeconds < 0) {
            return;
        }
        Executors.newSingleThreadScheduledExecutor().schedule(() -> exec(command), delaySeconds, TimeUnit.SECONDS);
    }
}
