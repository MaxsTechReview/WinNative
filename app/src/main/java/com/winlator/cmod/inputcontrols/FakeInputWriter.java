package com.winlator.cmod.inputcontrols;

import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class FakeInputWriter {
    private static final String TAG = "FakeInputWriter";
    private static final int MEM_SIZE = 64;
    private final File eventFile;
    private MappedByteBuffer mappedBuffer;
    private FileChannel channel;
    private RandomAccessFile raf;
    private boolean isOpen = false;

    public FakeInputWriter(String fakeInputPath, int slot) {
        // Match evshim.c: gamepad.mem, gamepad1.mem, etc.
        String fileName = slot == 0 ? "gamepad.mem" : "gamepad" + slot + ".mem";
        this.eventFile = new File(fakeInputPath, fileName);
    }

    public boolean open() {
        if (this.isOpen) return true;
        try {
            this.eventFile.getParentFile().mkdirs();
            this.raf = new RandomAccessFile(this.eventFile, "rw");
            if (this.raf.length() < MEM_SIZE) this.raf.setLength(MEM_SIZE);
            this.channel = this.raf.getChannel();
            this.mappedBuffer = this.channel.map(FileChannel.MapMode.READ_WRITE, 0, MEM_SIZE);
            this.isOpen = true;
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to map shared memory: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        if (this.mappedBuffer != null) this.mappedBuffer = null;
        try {
            if (this.channel != null) this.channel.close();
            if (this.raf != null) this.raf.close();
        } catch (IOException e) {}
        this.isOpen = false;
    }

    public void writeGamepadState(GamepadState state) {
        if (!this.isOpen && !open()) return;
        
        // Zero out and write struct starting at index 0
        mappedBuffer.position(0);
        state.writeTo(mappedBuffer);
        
        // No need to flip or call write(); mmap handle it automatically
    }

    public boolean isOpen() {
        return this.isOpen;
    }
}
