package com.winlator.cmod.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.winlator.cmod.core.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

public final class IconFileUtils {

    private IconFileUtils() {}

    public static Bitmap decodeImageOrIco(File file) {
        if (file == null || !file.isFile()) return null;

        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap != null) return bitmap;

        String lowerName = file.getName().toLowerCase(Locale.ENGLISH);
        if (!lowerName.endsWith(".ico")) return null;

        byte[] data = FileUtils.read(file);
        if (data == null || data.length < 6) return null;
        return decodeIco(data);
    }

    private static Bitmap decodeIco(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int reserved = bb.getShort() & 0xFFFF;
        int type = bb.getShort() & 0xFFFF;
        int count = bb.getShort() & 0xFFFF;
        if (reserved != 0 || type != 1 || count <= 0) return null;

        IcoEntry best = null;
        for (int i = 0; i < count; i++) {
            int width = bb.get() & 0xFF;
            int height = bb.get() & 0xFF;
            bb.get(); // color count
            bb.get(); // reserved
            bb.getShort(); // planes
            int bitCount = bb.getShort() & 0xFFFF;
            int bytesInRes = bb.getInt();
            int imageOffset = bb.getInt();

            if (width == 0) width = 256;
            if (height == 0) height = 256;
            if (bytesInRes <= 0 || imageOffset < 0) continue;
            if (imageOffset + bytesInRes > data.length) continue;

            IcoEntry candidate = new IcoEntry(width, height, bitCount, bytesInRes, imageOffset);
            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }
        }

        if (best == null) return null;

        byte[] imageData = Arrays.copyOfRange(data, best.imageOffset, best.imageOffset + best.bytesInRes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
        if (bitmap != null) return bitmap;

        // Some decoders expect a valid ICO envelope around the payload.
        byte[] wrappedIco = buildSingleIco(imageData, best.width, best.height, best.bitCount);
        return BitmapFactory.decodeByteArray(wrappedIco, 0, wrappedIco.length);
    }

    private static byte[] buildSingleIco(byte[] imageData, int width, int height, int bitCount) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0); // reserved
        header.putShort((short) 1); // type=icon
        header.putShort((short) 1); // count
        header.put((byte) (width >= 256 ? 0 : width));
        header.put((byte) (height >= 256 ? 0 : height));
        header.put((byte) 0); // color count
        header.put((byte) 0); // reserved
        header.putShort((short) 1); // planes
        header.putShort((short) Math.max(1, bitCount));
        header.putInt(imageData.length);
        header.putInt(22); // payload offset
        bos.write(header.array(), 0, header.array().length);
        bos.write(imageData, 0, imageData.length);
        return bos.toByteArray();
    }

    private static final class IcoEntry {
        final int width;
        final int height;
        final int bitCount;
        final int bytesInRes;
        final int imageOffset;

        IcoEntry(int width, int height, int bitCount, int bytesInRes, int imageOffset) {
            this.width = width;
            this.height = height;
            this.bitCount = bitCount;
            this.bytesInRes = bytesInRes;
            this.imageOffset = imageOffset;
        }

        long score() {
            return (long) width * (long) height * 1000L + bitCount;
        }
    }
}
