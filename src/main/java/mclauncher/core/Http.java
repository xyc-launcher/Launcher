package com.mclauncher.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** HTTP 辅助：跟随重定向的 GET/POST，带 Range 断点续传与进度回调的文件下载，HEAD 可达性探测。 */
public final class Http {
    private Http() {}

    public static final String UA = buildUA();

    private static String buildUA() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String platform;
        if (os.contains("win")) platform = "Windows NT 10.0; Win64; x64";
        else if (os.contains("mac")) platform = "Macintosh; Intel Mac OS X 10_15_7";
        else platform = "X11; Linux " + (arch.contains("aarch64") || arch.contains("arm")
                ? "aarch64" : "x86_64");
        return "Mozilla/5.0 (" + platform + ") xycLauncher/2.1-linux";
    }
    public static final int CONNECT_TIMEOUT = 20000;
    public static final int READ_TIMEOUT = 60000;

    private static HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", UA);
        c.setConnectTimeout(CONNECT_TIMEOUT);
        c.setReadTimeout(READ_TIMEOUT);
        c.setInstanceFollowRedirects(true);
        return c;
    }

    public static String getText(String url) throws IOException {
        HttpURLConnection c = open(url, "GET");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " : " + url);
            return readAll(c.getInputStream());
        } finally { c.disconnect(); }
    }

    public static String postForm(String url, String body) throws IOException {
        HttpURLConnection c = open(url, "POST");
        try {
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.setDoOutput(true);
            try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
            int code = c.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            return readAll(in);
        } finally { c.disconnect(); }
    }

    public static String postJson(String url, String json) throws IOException {
        HttpURLConnection c = open(url, "POST");
        try {
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            c.setDoOutput(true);
            try (OutputStream os = c.getOutputStream()) { os.write(json.getBytes(StandardCharsets.UTF_8)); }
            int code = c.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            return readAll(in);
        } finally { c.disconnect(); }
    }

    /** HEAD 探测 URL 是否可达（200/206，或 3xx 重定向均视为可达）。 */
    public static boolean headOk(String url) {
        try {
            HttpURLConnection c = open(url, "HEAD");
            int code = c.getResponseCode();
            c.disconnect();
            return code == 200 || code == 206 || (code >= 300 && code < 400);
        } catch (Exception e) {
            return false;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    /** 下载文件，支持 Range 断点续传；失败时保留部分文件由上层重试。 */
    public static void download(String url, Path target, Progress cb) throws IOException {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);

        long existing = Files.exists(target) ? Files.size(target) : 0;
        HttpURLConnection c = open(url, "GET");
        c.setRequestProperty("Accept-Encoding", "identity");
        if (existing > 0) c.setRequestProperty("Range", "bytes=" + existing + "-");
        int code = c.getResponseCode();
        if (code == 416) { c.disconnect(); return; } // 已完整
        boolean rangeOk = (code == 206);
        if (code != 200 && !rangeOk) { c.disconnect(); throw new IOException("HTTP " + code + " : " + url); }
        long total = c.getContentLengthLong();
        long done = rangeOk ? existing : 0;
        try (InputStream in = c.getInputStream();
             OutputStream os = Files.newOutputStream(target,
                     java.nio.file.StandardOpenOption.CREATE,
                     rangeOk ? java.nio.file.StandardOpenOption.APPEND
                             : java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[65536];
            int n;
            long lastCb = 0;
            while ((n = in.read(buf)) != -1) {
                os.write(buf, 0, n);
                done += n;
                if (cb != null && (done - lastCb) > 524288) {
                    lastCb = done;
                    cb.progress(total > 0 ? (double) done / total : 0);
                }
            }
            if (cb != null) cb.progress(1.0);
        } finally { c.disconnect(); }
    }

    public interface Progress { void progress(double p); }
}
