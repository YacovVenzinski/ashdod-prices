package il.yaakov.pricecompare;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class Net {
    private Net() {}

    static class HttpEx extends IOException {
        final int code;
        final String body;

        HttpEx(int code, String body) {
            super("HTTP " + code);
            this.code = code;
            this.body = body;
        }
    }

    static String get(String url, String... headers) throws IOException {
        return request("GET", url, null, headers);
    }

    static String request(String method, String url, String body, String... headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AshdodPrices/8");
        for (int i = 0; i + 1 < headers.length; i += 2) c.setRequestProperty(headers[i], headers[i + 1]);
        if (body != null) {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(b.length);
            try (OutputStream o = c.getOutputStream()) {
                o.write(b);
            }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String s = in == null ? "" : read(in);
        c.disconnect();
        if (code >= 400) throw new HttpEx(code, s);
        return s;
    }

    static String read(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
            return o.toString("UTF-8");
        }
    }

    static boolean offline(Exception e) {
        return e instanceof java.net.UnknownHostException || e instanceof java.net.SocketTimeoutException
                || e instanceof java.net.ConnectException;
    }
}
