package com.mclauncher.core;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Modrinth 整合包：搜索、版本列表、下载解析 .mrpack 并部署到独立实例目录。 */
public class ModpackManager {

    private static final String API = "https://api.modrinth.com/v2";

    public static class SearchHit {
        public String projectId, title, description, author, iconUrl;
        @Override public String toString() { return title; }
    }

    public static class PackVersion {
        public String versionId, name, mrpackUrl, mcVersion;
    }

    public static class InstalledPack {
        public String instanceId, title, mcVersion, loaderKind, loaderVersion;
        public Path instanceDir;
    }

    /* ---------- 搜索 ---------- */
    public static List<SearchHit> search(String query, int limit) throws Exception {
        String facets = enc("[[\"project_type:modpack\"]]");
        String url = API + "/search?query=" + enc(query) + "&limit=" + limit
                + "&facets=" + facets;
        Map<String,Object> root = Json.obj(Json.parse(Http.getText(url)));
        List<Object> hits = Json.arr(root, "hits");
        List<SearchHit> out = new ArrayList<>();
        if (hits != null) for (Object o : hits) {
            Map<String,Object> h = Json.obj(o);
            SearchHit s = new SearchHit();
            s.projectId = Json.str(h, "project_id");
            s.title = Json.str(h, "title");
            s.description = Json.str(h, "description");
            s.author = Json.str(h, "author");
            s.iconUrl = Json.str(h, "icon_url");
            out.add(s);
        }
        return out;
    }

    public static List<PackVersion> listVersions(String projectId) throws Exception {
        List<Object> arr = Json.arr(Json.parse(Http.getText(API + "/project/" + projectId + "/version")));
        List<PackVersion> out = new ArrayList<>();
        if (arr != null) for (Object o : arr) {
            Map<String,Object> v = Json.obj(o);
            List<Object> gv = Json.arr(v, "game_versions");
            List<Object> files = Json.arr(v, "files");
            String fileUrl = null;
            if (files != null) for (Object f : files) {
                Map<String,Object> fm = Json.obj(f);
                if (Json.bool(fm, "primary")) { fileUrl = Json.str(fm, "url"); break; }
                if (fileUrl == null) fileUrl = Json.str(fm, "url");
            }
            PackVersion pv = new PackVersion();
            pv.versionId = Json.str(v, "id");
            pv.name = Json.str(v, "name");
            pv.mrpackUrl = fileUrl;
            pv.mcVersion = gv != null && !gv.isEmpty() ? gv.get(gv.size() - 1).toString() : "";
            out.add(pv);
        }
        return out;
    }

    /* ---------- 下载并安装 ---------- */
    public static InstalledPack install(Config cfg, SearchHit hit, PackVersion pv,
                                        Downloader.Listener l) throws Exception {
        String instanceId = safeName(hit.title + "_" + pv.name);
        Path instanceDir = cfg.gameDir.resolve("instances").resolve(instanceId);
        Files.createDirectories(instanceDir);

        Path mrpack = cfg.gameDir.resolve("instances").resolve(instanceId + ".mrpack");
        l.status("正在下载整合包…");
        Exception last = null;
        for (int i = 0; i < 8; i++) {
            try { Http.download(pv.mrpackUrl, mrpack, p -> {}); last = null; break; }
            catch (Exception e) {
                last = e;
                Thread.sleep(e.getMessage() != null && e.getMessage().contains("403")
                        ? (long) (2000 * Math.pow(2, Math.min(i, 4))) : 600L * (i + 1));
            }
        }
        if (last != null) throw new Exception("整合包下载失败: " + last.getMessage());

        String indexText = null;
        try (ZipFile zf = new ZipFile(mrpack.toFile())) {
            // 1. 找索引
            ZipEntry indexEntry = zf.getEntry("modrinth.index.json");
            if (indexEntry == null) throw new Exception("mrpack 缺少 modrinth.index.json");
            try (InputStream in = zf.getInputStream(indexEntry)) {
                indexText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            // 2. 复制 overrides / client-overrides
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String n = e.getName();
                String prefix = null;
                if (n.startsWith("client-overrides/")) prefix = "client-overrides/";
                else if (n.startsWith("overrides/")) prefix = "overrides/";
                if (prefix == null || e.isDirectory()) continue;
                String rel = n.substring(prefix.length());
                if (rel.isEmpty()) continue;
                Path out = instanceDir.resolve(rel).normalize();
                if (!out.startsWith(instanceDir)) continue;
                Files.createDirectories(out.getParent());
                try (InputStream in = zf.getInputStream(e)) {
                    Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        Map<String,Object> idx = Json.obj(Json.parse(indexText));
        Map<String,Object> deps = Json.obj(idx, "dependencies");
        InstalledPack pack = new InstalledPack();
        pack.instanceId = instanceId;
        pack.title = hit.title;
        pack.instanceDir = instanceDir;
        pack.mcVersion = Json.str(deps, "minecraft");
        String fabric = Json.str(deps, "fabric-loader");
        String quilt = Json.str(deps, "quilt-loader");
        String forge = Json.str(deps, "forge");
        String neoforge = Json.str(deps, "neoforge");
        if (fabric != null) { pack.loaderKind = ModLoaderManager.FABRIC; pack.loaderVersion = fabric; }
        else if (quilt != null) { pack.loaderKind = ModLoaderManager.QUILT; pack.loaderVersion = quilt; }
        else if (neoforge != null) { pack.loaderKind = ModLoaderManager.NEOFORGE; pack.loaderVersion = neoforge; }
        else if (forge != null) { pack.loaderKind = ModLoaderManager.FORGE; pack.loaderVersion = forge; }
        else { pack.loaderKind = null; pack.loaderVersion = null; }

        // 3. 下载各文件（mods 等），跳过仅服务端
        List<Object> files = Json.arr(idx, "files");
        int total = files == null ? 0 : files.size(), done = 0;
        if (files != null) for (Object o : files) {
            Map<String,Object> f = Json.obj(o);
            Map<String,Object> env = Json.obj(f, "env");
            if (env != null && "disabled".equals(Json.str(env, "client"))) continue;
            String rel = Json.str(f, "path");
            Path target = instanceDir.resolve(rel).normalize();
            if (!target.startsWith(instanceDir)) continue;
            Map<String,Object> hashes = Json.obj(f, "hashes");
            if (Files.exists(target) && hashes != null && !hashMatch(target, hashes)) Files.deleteIfExists(target);
            if (!Files.exists(target)) {
                List<Object> downloads = Json.arr(f, "downloads");
                l.status("正在下载 mod 文件 (" + (++done) + "/" + total + ")");
                l.progress(done, total);
                Exception fl = null;
                if (downloads != null) for (Object d : downloads) {
                    try {
                        Http.download(d.toString(), target, p -> {});
                        fl = null; break;
                    } catch (Exception e) { fl = e; }
                }
                if (fl != null) throw new Exception("mod 文件下载失败: " + rel + " " + fl.getMessage());
                // mrpack 里给了 sha1 / sha512，下完必须核一次，坏文件当场重试而不是拖到启动才报错
                if (hashes != null && !hashMatch(target, hashes)) {
                    Files.deleteIfExists(target);
                    throw new Exception("mod 文件校验失败（sha1/sha512 不符，可能被截断）：" + rel);
                }
            }
        }
        Files.deleteIfExists(mrpack);
        return pack;
    }

    /**
     * 实例目录名：保留中英文与数字（中文整合包名不再被压成一串下划线），
     * 去掉路径分隔符等非法字符，并附一段短哈希，避免「名字相近/同长度」的整合包互相覆盖。
     */
    private static String safeName(String s) {
        if (s == null || s.isBlank()) return "pack";
        String clean = s.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").trim();
        while (clean.startsWith(".") || clean.startsWith("_")) clean = clean.substring(1);
        if (clean.isEmpty()) clean = "pack";
        if (clean.length() > 48) clean = clean.substring(0, 48);
        String hash = Integer.toHexString(s.hashCode() & 0x7fffffff);
        return clean + "-" + hash;
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** 按 mrpack 里的 hashes 字段核对文件（sha1 优先，其次 sha512）。 */
    private static boolean hashMatch(Path f, Map<String,Object> hashes) {
        try {
            String sha1 = Json.str(hashes, "sha1");
            if (sha1 != null && !sha1.isEmpty()) return digest(f, "SHA-1").equalsIgnoreCase(sha1);
            String sha512 = Json.str(hashes, "sha512");
            if (sha512 != null && !sha512.isEmpty()) return digest(f, "SHA-512").equalsIgnoreCase(sha512);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String digest(Path f, String algo) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance(algo);
        try (InputStream in = Files.newInputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
