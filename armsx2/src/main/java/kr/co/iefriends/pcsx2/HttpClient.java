package kr.co.iefriends.pcsx2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class HttpClient {

    private HttpClient() {}

    public static final class Response {
        public int statusCode;
        public String contentType = "";
        public byte[] data = new byte[0];
    }

    public static Response doRequest(
            String url,
            String method,
            byte[] postData,
            String userAgent,
            int timeoutMs) {
        Response out = new Response();
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setInstanceFollowRedirects(true);
            if (userAgent != null && !userAgent.isEmpty()) {
                conn.setRequestProperty("User-Agent", userAgent);
            }
            if ("POST".equalsIgnoreCase(method) && postData != null) {
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(postData.length);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                }
            }

            int code = conn.getResponseCode();
            out.statusCode = code;
            String ct = conn.getContentType();
            out.contentType = ct == null ? "" : ct;

            InputStream in;
            try {
                in = conn.getInputStream();
            } catch (IOException ignored) {
                in = conn.getErrorStream();
            }
            if (in != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    baos.write(buf, 0, n);
                }
                out.data = baos.toByteArray();
                in.close();
            }
            return out;
        } catch (java.net.SocketTimeoutException e) {
            out.statusCode = -2;
            return out;
        } catch (Throwable e) {
            out.statusCode = -1;
            return out;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
