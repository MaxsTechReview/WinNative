/* Shared download helper used by content and driver flows to stream files with progress updates. */
package com.winlator.cmod.contents;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

public class Downloader {

    public interface DownloadListener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    public static boolean downloadFile(String address, File file, DownloadListener listener) {
        try {
            URL url = new URL(address);
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.connect();

            // download the file
            InputStream input = url.openStream();

            // Output stream
            OutputStream output = new FileOutputStream(file.getAbsolutePath());

            byte[] data = new byte[8192];

            int count;
            long total = 0;
            long lengthOfFile = connection.getContentLengthLong();
            long lastUpdateTime = 0;
            
            while ((count = input.read(data)) != -1) {
                total += count;
                output.write(data, 0, count);
                if (listener != null) {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdateTime > 80 || total == lengthOfFile) {
                        listener.onProgress(total, lengthOfFile);
                        lastUpdateTime = currentTime;
                    }
                }
            }

            // flushing output
            output.flush();

            // closing streams
            output.close();
            input.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String downloadString(String address) {
        try {
            URL url = new URL(address);
            URLConnection connection = url.openConnection();
            connection.connect();

            InputStream input = url.openStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}