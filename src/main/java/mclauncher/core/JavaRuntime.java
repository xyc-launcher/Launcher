package com.mclauncher.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Java 运行时自动供应（跨平台：Windows / Linux / macOS）。
 *
 * 关键规则（老版本必须精确匹配）：
 * - required <= 8（1.7 / 1.8 / 1.12 等）：**只接受完全等于该大版本的 Java**，
 *   用 Java 17/25 启动这类版本会因模块系统/ASM 直接崩。
 * - required >= 9：优先精确版本，找不到时退到 >= required 的可用版本。
 *
 * 平台差异：
 * - Windows 的 OpenJDK 是 .zip；Linux / macOS 是 .tar.gz。
 * - Java 8 微软不再提供，统一走 Adoptium（Eclipse Temurin）。
 * - Linux 解压后需要恢复可执行位，否则 bin/java 无法运行。
 */
public class JavaRuntime {

    public static Path runtimeDir(Config cfg, int major) {
        return cfg.gameDir.resolve("runtime").resolve("jdk-" + major);
    }

    /** 按版本 JSON 的 javaVersion.majorVersion 要求供给 Java（启动器和安装器共用）。 */
    public static Path forVersion(Config cfg, String requiredMajor, Downloader.Listener l) throws Exception {
        int required = parseMajor(requiredMajor);
        if (required <= 8) return ensureExact(cfg, required, l);
        try {
            return ensureExact(cfg, required, l);
        } catch (Exception exactFail) {
            try { return ensure(cfg, required, l); }
            catch (Exception ignored) { throw exactFail; }
        }
    }

    public static int parseMajor(String s) {
        if (s == null) return 17;
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return 17; }
    }

    /** 精确匹配：只认 major 完全相同的运行时。 */
    public static Path ensureExact(Config cfg, int major, Downloader.Listener l) throws Exception {
        Path exe = findInRuntime(cfg, major);
        if (exe != null) return exe;
        Path sys = detectSystemJava(major, true);
        if (sys != null) { l.status("复用系统已安装的 Java " + major + "：" + sys); return sys; }
        return download(cfg, major, l);
    }

    /** 至少版本：>= major 的最高可用（现代版本可接受更高 Java 时使用）。 */
    public static Path ensure(Config cfg, int major, Downloader.Listener l) throws Exception {
        Path exe = findInRuntime(cfg, major);
        if (exe != null) return exe;
        Path sys = detectSystemJava(major, false);
        if (sys != null) { l.status("复用系统已安装 Java " + versionOf(sys)); return sys; }
        return download(cfg, major, l);
    }

    /** 兼容旧调用：等价于「按游戏要求供 Java」。 */
    public static Path ensureLatest(Config cfg, int requiredMajor, Downloader.Listener l) throws Exception {
        return forVersion(cfg, String.valueOf(requiredMajor), l);
    }

    /* ---------- 已下载的运行时 ---------- */
    private static Path findInRuntime(Config cfg, int major) {
        Path dir = runtimeDir(cfg, major);
        if (!Files.exists(dir)) return null;
        Path exe = findJava(dir);
        if (exe == null) return null;
        try {
            int actual = versionOf(exe);
            return actual == major ? exe : null;   // 目录名骗不了人：版本对不上就不用
        } catch (Exception e) {
            return null;
        }
    }

    /* ---------- 下载 ---------- */
    private static Path download(Config cfg, int major, Downloader.Listener l) throws Exception {
        Path base = runtimeDir(cfg, major);
        Files.createDirectories(base);
        String os = osToken(), arch = archToken();

        List<String> urls = downloadUrls(major, os, arch);
        if (urls.isEmpty())
            throw new Exception("无法自动下载 Java " + major + "：请手动安装 JDK " + major
                    + " 并设置 JAVA_HOME（老版本对 Java 版本是精确要求的）");

        // 按操作系统判断压缩包格式，而不是从 URL 字符串猜。
        // 旧实现用 urls.get(0).contains(".tar.gz") 推断：Adoptium API 的地址以 /eclipse
        // 结尾（由服务端 307 重定向到真实文件），字符串里不含扩展名，导致 Java 8 的
        // tar.gz 被误判成 .zip，用 ZIP 解压器去解 gzip 流，一个文件都解不出来。
        String ext = os.equals("windows") ? ".zip" : ".tar.gz";
        Path archive = base.resolve("jdk-" + major + ext);

        l.status("正在下载 Java " + major + " 运行时（" + (major <= 8 ? "Adoptium" : "Microsoft OpenJDK") + "）…");
        Exception last = null;
        boolean ok = false;
        for (int attempt = 0; attempt < 8 && !ok; attempt++) {
            for (String u : urls) {
                try {
                    Files.deleteIfExists(archive);
                    Http.download(u, archive, p -> {});
                    if (Files.size(archive) < 1024) throw new IOException("下载文件过小，疑似失败");
                    ok = true; last = null; break;
                } catch (Exception e) {
                    last = e;
                }
            }
            if (!ok) {
                boolean rate = last != null && last.getMessage() != null && last.getMessage().contains("403");
                Thread.sleep(rate ? (long) (2000 * Math.pow(2, Math.min(attempt, 4))) : 600L * (attempt + 1));
            }
        }
        if (last != null || !ok)
            throw new Exception("Java 运行时下载失败: " + (last == null ? "未知错误" : last.getMessage()));

        l.status("正在解压 Java 运行时…");
        if (ext.equals(".tar.gz")) untarGz(archive, base);
        else unzip(archive, base);
        Files.deleteIfExists(archive);

        Path exe = findJava(base);
        if (exe == null) throw new Exception("解压后未找到 java 可执行文件");
        makeExecutable(exe);
        return exe;
    }

    /**
     * Java 下载地址候选（按顺序尝试）：
     * - Java 8：Adoptium Temurin，全平台 zip/tar.gz。
     * - Java 11+：微软 OpenJDK 官方直链（国内可达），失败回落 Adoptium。
     * - 32 位：微软不提供 x86，统一 Adoptium。
     */
    private static List<String> downloadUrls(int major, String os, String arch) {
        List<String> out = new ArrayList<>();
        String adoptiumOs = os.equals("macos") ? "mac" : os;
        String adoptium = "https://api.adoptium.net/v3/binary/latest/" + major + "/ga/"
                + adoptiumOs + "/" + arch + "/jdk/hotspot/normal/eclipse";

        if (major <= 8 || "x86".equals(arch)) {
            out.add(adoptium);
            return out;
        }
        String ext = "windows".equals(os) ? ".zip" : ".tar.gz";
        out.add("https://aka.ms/download-jdk/microsoft-jdk-" + major + "-" + os + "-" + arch + ext);
        out.add(adoptium);
        return out;
    }

    private static String osToken() {
        String o = Downloader.detectOs();
        return o.equals("windows") ? "windows" : o.equals("osx") ? "macos" : "linux";
    }

    private static String archToken() {
        String a = Downloader.detectArch();
        if (a.equals("aarch64")) return "aarch64";
        if (a.equals("x86")) return "x86";
        return "x64";
    }

    /* ---------- 系统探测 ---------- */
    private static Path findJava(Path dir) {
        if (dir == null || !Files.exists(dir)) return null;
        String name = Downloader.detectOs().equals("windows") ? "java.exe" : "java";
        try (var walk = Files.walk(dir)) {
            return walk.filter(p -> p.getParent() != null && p.getParent().endsWith("bin")
                            && p.getFileName().toString().equals(name))
                       .findFirst().orElse(null);
        } catch (Exception e) { return null; }
    }

    /**
     * 扫描 JAVA_HOME、PATH 与常见安装目录。
     * exact=true：只要版本完全等于 major；exact=false：要 >= major 的最高版本。
     */
    static Path detectSystemJava(int major, boolean exact) throws Exception {
        List<Path> roots = new ArrayList<>();
        String home = System.getenv("JAVA_HOME");
        if (home != null && !home.isBlank()) roots.add(java.nio.file.Path.of(home));
        boolean win = Downloader.detectOs().equals("windows");
        if (win) {
            for (String p : new String[]{
                "C:\\Program Files\\Java", "C:\\Program Files (x86)\\Java",
                "C:\\Program Files\\Eclipse Adoptium", "C:\\Program Files\\Microsoft",
                "C:\\Program Files\\Android\\openjdk", "C:\\Program Files (x86)\\Android\\openjdk",
                "C:\\Program Files\\Zulu",
                "C:\\Program Files\\Amazon Corretto", "C:\\Program Files\\BellSoft"})
                roots.add(java.nio.file.Path.of(p));
        } else {
            for (String p : new String[]{"/usr/lib/jvm", "/opt/java", "/usr/java",
                                         "/Library/Java/JavaVirtualMachines"})
                roots.add(java.nio.file.Path.of(p));
            // PATH 里的 java（Linux 上 apt/源码装的常见情形）
            String path = System.getenv("PATH");
            if (path != null) for (String seg : path.split(java.io.File.pathSeparator)) {
                if (seg == null || seg.isBlank()) continue;
                Path candidate = java.nio.file.Path.of(seg).resolve("java");
                if (Files.isRegularFile(candidate)) {
                    Path real = candidate.toAbsolutePath().normalize();
                    int v = -1;
                    try { v = versionOf(real); } catch (Exception ignore) {}
                    if (v > 0 && (exact ? v == major : v >= major)) return real;
                }
            }
        }
        String javaName = win ? "java.exe" : "java";
        int best = -1;
        Path bestExe = null;
        for (Path r : roots) {
            if (!Files.isDirectory(r)) continue;
            try (var s = Files.list(r)) {
                for (Path d : (Iterable<Path>) s::iterator) {
                    if (!Files.isDirectory(d)) continue;
                    // 兼容两种 JDK/JRE 布局：
                    //   标准 JDK: <home>/bin/java
                    //   Debian/Arch 的 JRE: <home>/jre/bin/java
                    // 旧实现只查 bin/java，漏掉 jre/bin/java，导致 apt/pacman 装的
                    // jre*-openjdk 识别不到，明明装了 Java 8 仍去下载。
                    Path exe = d.resolve("bin").resolve(javaName);
                    if (!Files.isRegularFile(exe)) {
                        Path jreExe = d.resolve("jre").resolve("bin").resolve(javaName);
                        if (Files.isRegularFile(jreExe)) exe = jreExe;
                    }
                    if (!Files.isRegularFile(exe)) continue;
                    try {
                        int v = versionOf(exe);
                        if (exact ? v == major : v >= major) {
                            if (v > best) { best = v; bestExe = exe; }
                        }
                    } catch (Exception ignore) {}
                }
            } catch (Exception ignore) {}
        }
        return bestExe;
    }

    /** 通过 `java -version` 解析大版本号；无法执行/解析时抛异常（调用方跳过，兼容失效的 junction）。 */
    private static int versionOf(Path exe) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(exe.toString(), "-version");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        p.waitFor();
        // Java 8 输出形如 `1.8.0_xxx`，其余形如 `21.0.x`
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("version \"(\\d+)").matcher(out);
        if (!m.find()) throw new Exception("无法解析 Java 版本");
        int major = Integer.parseInt(m.group(1));
        if (major == 1) {
            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("version \"1\\.(\\d+)").matcher(out);
            if (m2.find()) return Integer.parseInt(m2.group(1));
        }
        return major;
    }

    /** 对外：解析给定 java 可执行文件的大版本号。 */
    public static int detectMajor(Path exe) throws Exception { return versionOf(exe); }

    /* ---------- 解压 ---------- */
    private static void unzip(Path zip, Path dest) throws Exception {
        try (InputStream fis = Files.newInputStream(zip); ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = zis.getNextEntry()) != null) {
                Path out = dest.resolve(e.getName()).normalize();
                if (!out.startsWith(dest)) throw new Exception("非法 zip 条目: " + e.getName());
                if (e.isDirectory()) Files.createDirectories(out);
                else {
                    Files.createDirectories(out.getParent());
                    try (var os = Files.newOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) != -1) os.write(buf, 0, n);
                    }
                    if (e.getName().endsWith("/java") || e.getName().endsWith("/java.exe")) makeExecutable(out);
                }
                zis.closeEntry();
            }
        }
    }

    /** 纯 Java 解压 .tar.gz（不依赖系统 tar 命令），支持 GNU longname 与 PAX path，保留可执行位。 */
    private static void untarGz(Path gz, Path dest) throws Exception {
        byte[] block = new byte[512];
        String longName = null;
        Map<String,String> pax = new HashMap<>();
        try (InputStream raw = Files.newInputStream(gz);
             GZIPInputStream in = new GZIPInputStream(raw, 65536)) {
            while (readBlock(in, block)) {
                boolean allZero = true;
                for (byte b : block) if (b != 0) { allZero = false; break; }
                if (allZero) break;

                String name = cstr(block, 0, 100);
                String prefix = cstr(block, 345, 155);
                if (!prefix.isEmpty()) name = prefix + "/" + name;
                char type = (char) (block[156] & 0xFF);
                long size = octal(block, 124, 12);
                int mode = (int) octal(block, 100, 8);

                // GNU 长文件名：数据块内容是下一个条目的真实名字
                if (type == 'L') {
                    longName = cstr(readData(in, size), 0, -1);
                    continue;
                }
                // PAX 扩展头：可能包含 path=... 覆盖名字
                if (type == 'x' || type == 'X') {
                    String paxData = new String(readData(in, size), StandardCharsets.UTF_8);
                    for (String line : paxData.split("\n")) {
                        int eq = line.indexOf('=');
                        if (eq > 0) pax.put(line.substring(line.indexOf(' ') + 1, eq),
                                            line.substring(eq + 1).replaceAll("\n$", ""));
                    }
                    continue;
                }

                String finalName = longName != null ? longName
                                 : (pax.get("path") != null ? pax.get("path") : name);
                longName = null;
                pax.clear();
                if (finalName.isEmpty()) { skipData(in, size); continue; }

                Path out = dest.resolve(finalName).normalize();
                if (!out.startsWith(dest)) throw new Exception("非法 tar 条目: " + finalName);

                if (type == '5') {                       // 目录
                    Files.createDirectories(out);
                    skipData(in, size);
                } else if (type == '0' || type == '\0' || type == '7') { // 普通文件/连续文件
                    if (out.getParent() != null) Files.createDirectories(out.getParent());
                    try (var os = Files.newOutputStream(out)) { os.write(readData(in, size)); }
                    if ((mode & 0111) != 0) makeExecutable(out);
                } else {
                    skipData(in, size);                   // 符号链接等：跳过
                }
            }
        }
    }

    /** 从流读取恰好 512 字节；到文件尾返回 false。 */
    private static boolean readBlock(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < 512) {
            int n = in.read(buf, off, 512 - off);
            if (n < 0) return off > 0;
            off += n;
        }
        return true;
    }

    /** 读取 size 字节并消费 tar 的 512 字节对齐填充。 */
    private static byte[] readData(InputStream in, long size) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long remaining = size;
        while (remaining > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (n < 0) break;
            bos.write(buf, 0, n);
            remaining -= n;
        }
        long pad = (512 - (size % 512)) % 512;
        long skipped = 0;
        while (skipped < pad) {
            long n = in.skip(pad - skipped);
            if (n <= 0) { if (in.read() < 0) break; n = 1; }
            skipped += n;
        }
        return bos.toByteArray();
    }

    private static void skipData(InputStream in, long size) throws IOException { readData(in, size); }

    /** tar 头里的八进制数字段。 */
    private static long octal(byte[] b, int off, int len) {
        long v = 0;
        for (int i = 0; i < len; i++) {
            int c = b[off + i] & 0xFF;
            if (c < '0' || c > '7') continue;
            v = v * 8 + (c - '0');
        }
        return v;
    }

    /** tar 头里的以 \0 结尾的字符串字段。 */
    private static String cstr(byte[] b, int off, int len) {
        int end = len < 0 ? b.length : Math.min(off + len, b.length);
        int n = off;
        while (n < end && b[n] != 0) n++;
        return new String(b, off, n - off, StandardCharsets.UTF_8);
    }

    /** 尽量给文件加可执行位（Windows 上忽略）。 */
    private static void makeExecutable(Path p) {
        try {
            Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(p));
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(p, perms);
        } catch (Exception ignore) {
            try { p.toFile().setExecutable(true, false); } catch (Exception ignore2) {}
        }
    }
}
