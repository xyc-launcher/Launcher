package com.mclauncher.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** 管理 authlib-injector.jar（皮肤站外置登录所需的 -javaagent）。 */
public class AuthlibInjector {
    private static final String META = "https://authlib-injector.yushi.moe/artifact/latest.json";

    public static Path jarPath(Config cfg) { return cfg.authlibInjectorPath(); }

    public static Path ensure(Config cfg) throws Exception {
        Path target = jarPath(cfg);
        if (Files.exists(target) && target.toFile().length() > 1000) return target;

        Map<String,Object> meta;
        try {
            meta = Json.obj(Json.parse(Http.getText(META)));
        } catch (Exception e) {
            throw new Exception("无法获取 authlib-injector 元数据: " + e.getMessage());
        }
        String url = Json.str(meta, "download_url");
        if (url == null) throw new Exception("authlib-injector 元数据缺少 download_url");
        // 断点续传重试
        Exception last = null;
        for (int i = 0; i < 6; i++) {
            try { Http.download(url, target, p -> {}); last = null; break; }
            catch (Exception e) { last = e; Thread.sleep(500L * (i + 1)); }
        }
        if (last != null) throw new Exception("authlib-injector 下载失败: " + last.getMessage());
        return target;
    }
}
