package com.winlator.cmod.runtime.display.xserver.extensions;

import android.util.Log;
import com.winlator.cmod.runtime.display.connector.XInputStream;
import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.connector.XStreamLock;
import com.winlator.cmod.runtime.display.xserver.Bitmask;
import com.winlator.cmod.runtime.display.xserver.Window;
import com.winlator.cmod.runtime.display.xserver.XClient;
import com.winlator.cmod.runtime.display.xserver.XLock;
import com.winlator.cmod.runtime.display.xserver.XServer;
import com.winlator.cmod.runtime.display.xserver.errors.BadValue;
import com.winlator.cmod.runtime.display.xserver.errors.BadWindow;
import com.winlator.cmod.runtime.display.xserver.errors.XRequestError;
import com.winlator.cmod.runtime.display.xserver.events.XIRawButtonPressNotify;
import com.winlator.cmod.runtime.display.xserver.events.XIRawButtonReleaseNotify;
import com.winlator.cmod.runtime.display.xserver.events.XIRawMotionNotify;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class XInput2Extension implements Extension {
    public static final byte MAJOR_OPCODE = -105;
    private static final int MASTER_KEYBOARD_ID = 3;
    private static final int MASTER_POINTER_ID = 2;
    private static final int POINTER_BUTTON_COUNT = 7;
    private static final int RawMotion_XY_MASK = 3;
    private static final int XI_ALL_DEVICES = 0;
    private static final int XI_ALL_MASTER_DEVICES = 1;
    private static final int XI_BUTTON_CLASS = 1;
    private static final int XI_MAJOR = 2;
    private static final int XI_MINOR = 2;
    private static final long XI_RawButtonPress_MASK = 32768;
    private static final long XI_RawButtonRelease_MASK = 65536;
    private static final long XI_RawMotion_MASK = 131072;
    private static final int XI_VALUATOR_CLASS = 2;
    private byte firstEventId = 0;
    private byte firstErrorId = 0;
    private final List<Selection> selections = new CopyOnWriteArrayList<>();

    private static abstract class ClientOpcodes {
        private static final byte GET_CLIENT_POINTER = 45;
        private static final byte GET_EXTENSION_VERSION = 1;
        private static final byte QUERY_DEVICE = 48;
        private static final byte QUERY_VERSION = 47;
        private static final byte SELECT_EVENTS = 46;

        private ClientOpcodes() {
        }
    }

    private static class Selection {
        XClient client;
        int deviceId;
        int id;
        Bitmask mask;
        Window window;

        private Selection() {
        }
    }

    @Override
    public String getName() {
        return "XInputExtension";
    }

    @Override
    public byte getMajorOpcode() {
        return MAJOR_OPCODE;
    }

    @Override
    public int getNumEvents() {
        return 24;
    }

    @Override
    public int getNumErrors() {
        return 5;
    }

    @Override
    public void setFirstEventId(byte id) {
        this.firstEventId = id;
    }

    @Override
    public void setFirstErrorId(byte id) {
        this.firstErrorId = id;
    }

    @Override
    public byte getFirstEventId() {
        return this.firstEventId;
    }

    @Override
    public byte getFirstErrorId() {
        return this.firstErrorId;
    }

    private boolean isMasterDevice(int deviceId) {
        return deviceId == 2 || deviceId == 3;
    }

    private boolean matchesSelection(Selection sel, int deviceId) {
        if (sel.deviceId != 0) {
            return (sel.deviceId == 1 && isMasterDevice(deviceId)) || sel.deviceId == deviceId;
        }
        return true;
    }

    private static void getExtensionVersion(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException {
        inputStream.skip(client.getRemainingRequestLength());
        XStreamLock lock = outputStream.lock();
        try {
            outputStream.writeByte((byte) 1);
            outputStream.writeByte((byte) 0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeShort((short) 2);
            outputStream.writeShort((short) 0);
            outputStream.writeByte((byte) 1);
            outputStream.writePad(19);
            if (lock != null) {
                lock.close();
            }
        } catch (Throwable th) {
            if (lock != null) {
                try {
                    lock.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    private static void getClientPointer(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException {
        inputStream.skip(client.getRemainingRequestLength());
        XStreamLock lock = outputStream.lock();
        try {
            outputStream.writeByte((byte) 1);
            outputStream.writeByte((byte) 0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeByte((byte) 1);
            outputStream.writeByte((byte) 0);
            outputStream.writeShort((short) 2);
            outputStream.writePad(20);
            if (lock != null) {
                lock.close();
            }
        } catch (Throwable th) {
            if (lock != null) {
                try {
                    lock.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    private static void queryVersion(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException {
        short negotiatedMajor;
        short negotiatedMinor;
        short clientMajor = (short) (inputStream.readShort() & 0xFFFF);
        short clientMinor = (short) (0xFFFF & inputStream.readShort());
        inputStream.skip(client.getRemainingRequestLength());
        if (clientMajor < 2 || (clientMajor == 2 && clientMinor < 2)) {
            negotiatedMajor = clientMajor;
            negotiatedMinor = clientMinor;
        } else {
            negotiatedMajor = 2;
            negotiatedMinor = 2;
        }
        XStreamLock lock = outputStream.lock();
        try {
            outputStream.writeByte((byte) 1);
            outputStream.writeByte((byte) 0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeShort(negotiatedMajor);
            outputStream.writeShort(negotiatedMinor);
            outputStream.writePad(20);
            if (lock != null) {
                lock.close();
            }
        } catch (Throwable th) {
            if (lock != null) {
                try {
                    lock.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    private void writeButtonClass(XOutputStream outputStream, int sourceId, int numButtons) throws IOException {
        int stateBytes = Math.max(4, ((numButtons + 31) / 32) * 4);
        int labelsBytes = numButtons * 4;
        int totalBytes = stateBytes + 8 + labelsBytes;
        int length = totalBytes / 4;
        outputStream.writeShort((short) 1);
        outputStream.writeShort((short) length);
        outputStream.writeShort((short) sourceId);
        outputStream.writeShort((short) numButtons);
        outputStream.writeInt(0);
        if (stateBytes > 4) {
            outputStream.writePad(stateBytes - 4);
        }
        for (int i = 0; i < numButtons; i++) {
            outputStream.writeInt(0);
        }
    }

    private void writeValuatorClass(XOutputStream outputStream, int axisNumber) throws IOException {
        outputStream.writeShort((short) 2);
        outputStream.writeShort((short) 11);
        outputStream.writeShort((short) 2);
        outputStream.writeShort((short) axisNumber);
        outputStream.writeInt(0);
        outputStream.writeFP3232(0.0);
        outputStream.writeFP3232(0.0);
        outputStream.writeFP3232(0.0);
        outputStream.writeInt(0);
        outputStream.writeByte((byte) 0);
        outputStream.writePad(3);
    }

    private void queryDevice(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException {
        inputStream.skip(client.getRemainingRequestLength());
        byte[] nameBytes = "Virtual Core Pointer".getBytes();
        int nameLen = nameBytes.length;
        int namePad = (nameLen + 3) & (-4);
        int buttonStateBytes = Math.max(4, 4);
        int buttonClassBytes = buttonStateBytes + 8 + 28;
        int deviceInfoSize = namePad + 12 + buttonClassBytes + 88;
        int length = deviceInfoSize / 4;
        XStreamLock lock = outputStream.lock();
        try {
            outputStream.writeByte((byte) 1);
            outputStream.writeByte((byte) 0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(length);
            outputStream.writeShort((short) 1);
            outputStream.writePad(22);
            outputStream.writeShort((short) 2);
            outputStream.writeShort((short) 1);
            outputStream.writeShort((short) 0);
            outputStream.writeShort((short) 3);
            outputStream.writeShort((short) nameLen);
            outputStream.writeByte((byte) 1);
            outputStream.writeByte((byte) 0);
            outputStream.write(nameBytes);
            outputStream.writePad(namePad - nameLen);
            writeButtonClass(outputStream, 2, 7);
            writeValuatorClass(outputStream, 0);
            writeValuatorClass(outputStream, 1);
            if (lock != null) {
                lock.close();
            }
        } catch (Throwable th) {
            if (lock != null) {
                try {
                    lock.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    private void selectEvents(final XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        final int windowId = inputStream.readInt();
        int numMasks = inputStream.readShort() & 0xFFFF;
        if (numMasks == 0) {
            inputStream.skip(client.getRemainingRequestLength());
            throw new BadValue(numMasks);
        }
        inputStream.readShort();
        Window window = client.xServer.windowManager.getWindow(windowId);
        if (window == null) {
            inputStream.skip(client.getRemainingRequestLength());
            throw new BadWindow(windowId);
        }
        for (int i = 0; i < numMasks; i++) {
            final int deviceId = inputStream.readShort() & 0xFFFF;
            int maskLen = inputStream.readShort() & 0xFFFF;
            Bitmask mask = new Bitmask(0);
            for (int word = 0; word < maskLen; word++) {
                long value = inputStream.readUnsignedInt();
                mask.set(value << (word * 32));
            }
            Selection sel = new Selection();
            sel.client = client;
            sel.window = window;
            sel.deviceId = deviceId;
            sel.mask = mask;
            sel.id = windowId;
            this.selections.removeIf(old -> old.client == client && old.id == windowId && old.deviceId == deviceId);
            this.selections.add(sel);
        }
        inputStream.skip(client.getRemainingRequestLength());
    }

    @Override
    public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int opcode = client.getRequestData();
        switch (opcode) {
            case 1:
                getExtensionVersion(client, inputStream, outputStream);
                return;
            case 45:
                getClientPointer(client, inputStream, outputStream);
                return;
            case 46:
                try (XLock lock = client.xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                    selectEvents(client, inputStream, outputStream);
                }
                return;
            case 47:
                queryVersion(client, inputStream, outputStream);
                return;
            case 48:
                queryDevice(client, inputStream, outputStream);
                return;
            default:
                Log.w("XServer", String.format("XInput2Extension: unhandled minor opcode=%d, requestData=%d, requestLength=%d", opcode, client.getRequestData(), client.getRemainingRequestLength()));
                inputStream.skip(client.getRemainingRequestLength());
        }
    }

    public void onClientDisconnected(final XClient client) {
        this.selections.removeIf(sel -> sel.client == client);
    }

    public void emitRawMotion(int deviceId, double deltaX, double deltaY) {
        for (Selection sel : this.selections) {
            if (matchesSelection(sel, deviceId) && sel.mask.isSet(XI_RawMotion_MASK)) {
                try {
                    sendXIRawMotionToClient(sel.client, deviceId, deltaX, deltaY);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public void emitRawButton(int deviceId, int buttonNumber, boolean pressed) {
        long maskBit = pressed ? XI_RawButtonPress_MASK : XI_RawButtonRelease_MASK;
        for (Selection sel : this.selections) {
            if (matchesSelection(sel, deviceId) && sel.mask.isSet(maskBit)) {
                try {
                    sendXIRawButtonToClient(sel.client, deviceId, buttonNumber, pressed);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void sendXIRawMotionToClient(XClient client, int deviceId, double deltaX, double deltaY) throws IOException {
        client.sendEvent(new XIRawMotionNotify(deviceId, MAJOR_OPCODE, new double[]{deltaX, deltaY}, 3));
    }

    private void sendXIRawButtonToClient(XClient client, int deviceId, int buttonNumber, boolean pressed) throws IOException {
        if (pressed) {
            client.sendEvent(new XIRawButtonPressNotify(deviceId, MAJOR_OPCODE, buttonNumber));
        } else {
            client.sendEvent(new XIRawButtonReleaseNotify(deviceId, MAJOR_OPCODE, buttonNumber));
        }
    }
}
