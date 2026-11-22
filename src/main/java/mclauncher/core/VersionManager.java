package com.mclauncher.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 版本清单：远程版本列表 + 本地已安装版本。 */
public class VersionManager {

    public static class VersionEntry {
        public String id;
        public String type;
        public String url;
        public String releaseTime;

        @Override public String toString() { return id; }
    }

    /** 拉取远程清单（release 在前，snapshot 在后）。 */
    public static List<VersionEntry> fetchManifest(String manifestUrl) throws Exception {
        Map<String,Object> root = Json.obj(Json.parse(Http.getText(manifestUrl)));
        List<Object> versions = Json.arr(root, "versions");
        List<VersionEntry> list = new ArrayList<>();
        if (versions != null) {
            for (Object o : versions) {
                Map<String,Object> v = Json.obj(o);
                VersionEntry e = new VersionEntry();
                e.id = Json.str(v, "id");
                e.type = Json.str(v, "type");
                e.url = Json.str(v, "url");
                e.releaseTime = Json.str(v, "releaseTime");
                list.add(e);
            }
        }
        return list;
    }

    /** 扫描本地 versions 目录，返回已安装版本 id。 */
    public static List<String> localVersions(Path gameDir) {
        List<String> out = new ArrayList<>();
        Path vdir = gameDir.resolve("versions");
        if (!Files.exists(vdir)) return out;
        try (var stream = Files.list(vdir)) {
            stream.filter(Files::isDirectory).forEach(d -> {
                String id = d.getFileName().toString();
                if (Files.exists(d.resolve(id + ".json"))) out.add(id);
            });
        } catch (Exception ignore) {}
        return out;
    }

    /** 在清单中按 id 查找。 */
    public static VersionEntry find(List<VersionEntry> list, String id) {
        for (VersionEntry e : list) if (e.id.equals(id)) return e;
        return null;
    }
}
