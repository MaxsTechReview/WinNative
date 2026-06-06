package com.winlator.cmod.runtime.display.xserver.events;

import com.winlator.cmod.runtime.display.connector.XOutputStream;
import com.winlator.cmod.runtime.display.connector.XStreamLock;
import java.io.IOException;

public class XIRawMotionNotify extends Event {
    public static final int GENERIC_EVENT_CODE = 35;
    private static final short XI_RAWMOTION_EVTYPE = 17;
    private final int deviceId;
    private final byte extensionOpcode;
    private final int valuatorMask;
    private final double[] valuators;

    public XIRawMotionNotify(int deviceId, byte extensionOpcode, double[] valuators, int valuatorMask) {
        super(35);
        this.deviceId = deviceId;
        this.extensionOpcode = extensionOpcode;
        this.valuators = valuators;
        this.valuatorMask = valuatorMask;
    }

    @Override
    public void send(short sequenceNumber, XOutputStream outputStream) throws IOException {
        XStreamLock lock = outputStream.lock();
        try {
            int numAxes = this.valuators.length;
            int payloadBytes = (numAxes * 8) + 4 + (numAxes * 8);
            int payloadLengthUnits = payloadBytes / 4;
            outputStream.writeByte(this.code);
            outputStream.writeByte(this.extensionOpcode);
            outputStream.writeShort(sequenceNumber);
            outputStream.writeInt(payloadLengthUnits);
            outputStream.writeShort((short) 17);
            outputStream.writeShort((short) this.deviceId);
            outputStream.writeInt((int) System.currentTimeMillis());
            outputStream.writeInt(0);
            outputStream.writeShort((short) this.deviceId);
            outputStream.writeShort((short) 1);
            outputStream.writeInt(0);
            outputStream.writePad(4);
            outputStream.writeInt(this.valuatorMask);
            for (double v : this.valuators) {
                outputStream.writeFP3232(v);
            }
            for (double v2 : this.valuators) {
                outputStream.writeFP3232(v2);
            }
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
