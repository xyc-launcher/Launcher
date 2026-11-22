package com.mclauncher.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * mod 加载器安装。
 *
 * - Fabric / Quilt：解析 meta 的启动 profile，直接拿 mainClass + 额外 JVM 参数 + 库列表。
 * - Forge / NeoForge：调用官方安装器 jar（`--installClient`），由安装器生成继承版本，
 *   随后交给 {@link Downloader#installLocal} 合并父版本并启动。
 */
public class ModLoaderManager {

    public static final String FABRIC = "fabric";
    public static final String QUILT = "quilt";
    public static final String FORGE = "forge";
    public static final String NEOFORGE = "neoforge";

    public static class ModProfile {
        public String kind;
        public String mcVersion;
        public String loaderVersion;
        public String mainClass;
        public List<String> jvmArgs = new ArrayList<>();
        public List<String> classpath = new ArrayList<>();
        /** Forge / NeoForge：安装器生成的版本 id，交给 Downloader.installLocal 使用。 */
        public String installedVersionId;
    }

    public static boolean isInstallerBased(String kind) {
        return FORGE.equals(kind) || NEOFORGE.equals(kind);
    }

    /* ---------- 版本列表 ---------- */

    /** 兼容旧调用：Fabric / Quilt 不需要 mc 版本。 */
    public static List<String> listLoaderVersions(String kind) throws Exception {
        return listLoaderVersions(kind, null);
    }

    public static List<String> listLoaderVersions(String kind, String mc) throws Exception {
        if (FORGE.equals(kind)) return forgeVersions(mc);
        if (NEOFORGE.equals(kind)) return neoForgeVersions(mc);

        List<Object> arr = Json.arr(Json.parse(fetchText(metaBase(kind) + "/versions/loader")));
        List<String> out = new ArrayList<>();
        if (arr != null) for (Object o : arr) {
            Map<String,Object> m = Json.obj(o);
            if (Json.bool(m, "stable")) out.add(Json.str(m, "version"));
        }
        if (out.isEmpty() && arr != null)
            for (Object o : arr) out.add(Json.str(Json.obj(o), "version"));
        return out;
    }

    /** Forge：官方 promotions 里每个 MC 版本的 recommended / latest。 */
    private static List<String> forgeVersions(String mc) throws Exception {
        if (mc == null || mc.isEmpty()) throw new Exception("请先在「原版游戏」页选中要装 Forge 的 MC 版本");
        Map<String,Object> root = Json.obj(Json.parse(fetchText(
                "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")));
        Map<String,Object> promos = Json.obj(root, "promos");
        List<String> out = new ArrayList<>();
        String rec = Json.str(promos, mc + "-recommended");
        String lat = Json.str(promos, mc + "-latest");
        if (rec != null) out.add(rec);
        if (lat != null && !lat.equals(rec)) out.add(lat);
        if (out.isEmpty()) throw new Exception("Forge 没有支持 " + mc + " 的版本");
        return out;
    }

    /** NeoForge：maven-metadata.xml 里按「MC 版本 → 编号前缀」筛选。 */
    private static List<String> neoForgeVersions(String mc) throws Exception {
        if (mc == null || mc.isEmpty()) throw new Exception("请先在「原版游戏」页选中要装 NeoForge 的 MC 版本");
        String prefix = neoForgePrefix(mc);
        String primary = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";
        String mirror = "https://bmclapi2.bangbang93.com/maven/net/neoforged/neoforge/maven-metadata.xml";
        String xml;
        try {
            xml = fetchText(primary);
        } catch (Exception e) {
            xml = fetchText(mirror);   // 官方 maven 被限流/墙时走 BMCLAPI 代理
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<version>([^<]+)</version>").matcher(xml);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            String v = m.group(1).trim();
            if (!v.startsWith(prefix)) continue;
            if (v.contains("alpha") || v.contains("beta")) continue;   // 只要正式版
            out.add(v);
        }
        if (out.isEmpty()) throw new Exception("NeoForge 没有支持 " + mc + " 的正式版（1.20.1 及更早请用 Forge）");
        // maven-metadata 按版本升序，倒过来让最新版排前面
        List<String> desc = new ArrayList<>();
        for (int i = out.size() - 1; i >= 0; i--) desc.add(out.get(i));
        return desc;
    }

    /**
     * MC 版本 → NeoForge 版本号前缀。
     * - 新编号方案（MC 26.3 这类）→ 直接 "26.3."（如 26.3.0.110）
     * - 老方案 1.X.Y → "X.Y."（1.21.1 → 21.1.，1.20.2 → 20.2.）
     * - 1.20.1 沿用 Forge 的 47.x 编号
     */
    private static String neoForgePrefix(String mc) throws Exception {
        if ("1.20.1".equals(mc)) return "47.";
        if (!mc.startsWith("1.")) return mc + ".";          // 2026 起的新版本号
        String[] p = mc.split("\\.");
        if (p.length < 2) throw new Exception("无法识别 MC 版本：" + mc);
        int minor, patch = 0;
        try {
            minor = Integer.parseInt(p[1]);
            if (p.length >= 3) patch = Integer.parseInt(p[2]);
        } catch (Exception e) { throw new Exception("无法识别 MC 版本：" + mc); }
        if (minor < 20 || "1.20.2".compareTo(mc) > 0)
            throw new Exception("NeoForge 从 1.20.2 起才有，1.20.1 请用 Forge");
        return minor + "." + patch + ".";
    }

    private static String metaBase(String kind) {
        return FABRIC.equals(kind) ? "https://meta.fabricmc.net/v2"
                                   : "https://meta.quiltmc.org/v3";
    }

    /** 这些镜像站偶尔 503，简单的 3 次退避重试能挡掉大部分抖动。 */
    private static String fetchText(String url) throws Exception {
        Exception last = null;
        for (int i = 0; i < 3; i++) {
            try { return Http.getText(url); }
            catch (Exception e) { last = e; Thread.sleep(800L * (i + 1)); }
        }
        throw last;
    }

    /* ---------- 安装：统一入口 ---------- */

    /** Fabric / Quilt 专用（无 java 参数）。 */
    public static ModProfile install(Config cfg, String kind, String mc, String loaderVersion,
                                     Downloader.Listener l) throws Exception {
        String url = metaBase(kind) + "/versions/loader/" + mc + "/" + loaderVersion + "/profile/json";
        Map<String,Object> pj = Json.obj(Json.parse(Http.getText(url)));

        ModProfile mp = new ModProfile();
        mp.kind = kind;
        mp.mcVersion = mc;
        mp.loaderVersion = loaderVersion;
        mp.mainClass = Json.str(pj, "mainClass");

        Map<String,Object> args = Json.obj(pj, "arguments");
        List<Object> jvm = args == null ? null : Json.arr(args, "jvm");
        if (jvm != null) for (Object o : jvm) if (o instanceof String) mp.jvmArgs.add((String) o);

        Path libRoot = cfg.gameDir.resolve("libraries");
        List<Object> libs = Json.arr(pj, "libraries");
        int total = libs == null ? 0 : libs.size(), done = 0;
        if (libs != null) for (Object o : libs) {
            Map<String,Object> lib = Json.obj(o);
            String name = Json.str(lib, "name");
            String base = Json.str(lib, "url");
            if (base == null) base = "https://maven.fabricmc.net/";
            String rel = mavenToPath(name);
            Path target = libRoot.resolve(rel.replace('/', java.io.File.separatorChar));
            l.status("正在下载 " + kind + " 库 (" + (++done) + "/" + total + ")");
            l.progress(done, total);
            String jarUrl = base.endsWith("/") ? base + rel : base + "/" + rel;
            downloadWithResume(jarUrl, target);
            mp.classpath.add(target.toString());
        }
        return mp;
    }

    /**
     * Forge / NeoForge：下载官方安装器并执行 `--installClient`。
     * 安装器会自己下依赖、跑处理器、生成带 inheritsFrom 的版本 json。
     */
    public static ModProfile installForge(Config cfg, String kind, String mc, String loaderVer,
                                          Path javaExe, Downloader.Listener l) throws Exception {
        String versionId = FORGE.equals(kind) ? mc + "-forge-" + loaderVer : "neoforge-" + loaderVer;
        Path vjson = cfg.gameDir.resolve("versions").resolve(versionId).resolve(versionId + ".json");

        ModProfile mp = new ModProfile();
        mp.kind = kind;
        mp.mcVersion = mc;
        mp.loaderVersion = loaderVer;
        mp.installedVersionId = versionId;

        if (Files.exists(vjson)) {
            l.status(kind + " 已安装：" + versionId);
            return mp;
        }

        String url = FORGE.equals(kind)
                ? "https://maven.minecraftforge.net/net/minecraftforge/forge/" + mc + "-" + loaderVer
                  + "/forge-" + mc + "-" + loaderVer + "-installer.jar"
                : "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + loaderVer
                  + "/neoforge-" + loaderVer + "-installer.jar";

        Path dir = cfg.gameDir.resolve("installers");
        Files.createDirectories(dir);
        Path jar = dir.resolve(kind + "-" + loaderVer + "-installer.jar");

        l.status("正在下载 " + kind + " " + loaderVer + " 安装器…");
        Exception last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            try { Http.download(url, jar, p -> {}); last = null; break; }
            catch (Exception e) {
                last = e;
                // 境外 maven 慢/被限流时，改走 BMCLAPI 的 /maven/ 代理
                if (attempt == 2 && !"mojang".equals(cfg.downloadMirror)) {
                    String mirror = cfg.mirrorBase() + "/maven/" + url.substring(url.indexOf("net/") + 4);
                    try { Http.download(mirror, jar, p -> {}); last = null; break; }
                    catch (Exception e2) { last = e2; }
                }
                Thread.sleep(600L * (attempt + 1));
            }
        }
        if (last != null) throw new Exception(kind + " 安装器下载失败: " + last.getMessage());

        l.status("正在执行 " + kind + " 安装器（首次安装需要几分钟，请勿关闭窗口）…");
        ProcessBuilder pb = new ProcessBuilder(javaExe.toString(), "-jar", jar.toAbsolutePath().toString(),
                                               "--installClient", cfg.gameDir.toAbsolutePath().toString());
        pb.directory(cfg.gameDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder tail = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                tail.append(line).append('\n');
                if (tail.length() > 6000) tail.delete(0, tail.length() - 6000);
            }
        } catch (Exception ignore) {}

        boolean finished = p.waitFor(10, TimeUnit.MINUTES);
        if (!finished) {
            p.destroyForcibly();
            throw new Exception(kind + " 安装器超时（10 分钟），已中止");
        }
        if (p.exitValue() != 0 || !Files.exists(vjson))
            throw new Exception(kind + " 安装器执行失败（退出码 " + p.exitValue() + "）：\n" + tailTail(tail.toString()));

        Files.deleteIfExists(jar);
        l.status(kind + " 安装完成：" + versionId);
        return mp;
    }

    private static String tailTail(String s) {
        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, lines.length - 12);
        for (int i = from; i < lines.length; i++) sb.append(lines[i]).append('\n');
        return sb.toString().trim();
    }

    private static void downloadWithResume(String url, Path target) throws Exception {
        if (Files.exists(target) && target.toFile().length() > 0) return;
        Exception last = null;
        for (int i = 0; i < 6; i++) {
            try { Http.download(url, target, p -> {}); last = null; break; }
            catch (Exception e) { last = e; Thread.sleep(500L * (i + 1)); }
        }
        if (last != null) throw new Exception("加载器库下载失败: " + last.getMessage());
    }

    /** maven 坐标 group:artifact:version[:classifier] → 相对 jar 路径。 */
    public static String mavenToPath(String name) {
        String[] p = name.split(":");
        String group = p[0].replace('.', '/');
        String art = p[1];
        String ver = p[2];
        String cls = p.length > 3 ? "-" + p[3] : "";
        return group + "/" + art + "/" + ver + "/" + art + "-" + ver + cls + ".jar";
    }
}
