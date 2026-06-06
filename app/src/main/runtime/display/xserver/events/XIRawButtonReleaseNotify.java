package com.winlator.cmod.runtime.display.xserver.events;

import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.connector.XStreamLock;
import java.io.IOException;

public class XIRawButtonReleaseNotify extends Event {
    public static final int GENERIC_EVENT_CODE = 35;
    private static final short XI_RAWBUTTONRELEASE_EVTYPE = 16;
    private final int buttonNumber;
    private final int deviceId;
    private final byte extensionOpcode;

    public XIRawButtonReleaseNotify(int deviceId, byte extensionOpcode, int buttonNumber) {
        super(35);
        this.deviceId = deviceId;
        this.extensionOpcode = extensionOpcode;
        this.buttonNumber = buttonNumber;
    }

    @Override
    public void send(short sequenceNumber, XOutputStream outputStream) throws IOException {
        XStreamLock lock = outputStream.lock();
        try {
            outputStream.writeByte(this.code);
            outputStream.writeByte(this.extensionOpcode);
            outputStream.writeShort(sequenceNumber);
            outputStream.writeInt(0);
            outputStream.writeShort((short) 16);
            outputStream.writeShort((short) this.deviceId);
            outputStream.writeInt((int) System.currentTimeMillis());
            outputStream.writeInt(this.buttonNumber);
            outputStream.writeShort((short) this.deviceId);
            outputStream.writeShort((short) 0);
            outputStream.writeInt(0);
            outputStream.writePad(4);
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
}
