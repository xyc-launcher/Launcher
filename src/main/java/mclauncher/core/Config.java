package com.mclauncher.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 启动器配置，持久化为 config.json。 */
public class Config {
    public Path root;
    public Path gameDir;
    public String javaPath = "";
    public String msClientId = "0add8caf-2cc6-4546-b798-c3d171217dd9"; // 第三方共享且经批准的 Azure AD 客户端 ID（旧 000000004C12AE6F 已被微软移除）
    public String downloadMirror = "bmclapi"; // bmclapi | mojang
    public int windowWidth = 960;
    public int windowHeight = 640;
    public int memoryMb = 2048;
    public String selectedVersion = "";
    /** -1=按游戏版本要求（默认，最稳）；0=使用最新版；>0=固定指定大版本（仅兼容时生效）。 */
    public int javaMajor = -1;
    public List<Map<String,Object>> accounts = new ArrayList<>();
    public int selectedAccount = -1;

    public static Config load(Path root) {
        Config c = new Config();
        c.root = root;
        Path file = root.resolve("config.json");
        if (Files.exists(file)) {
            try {
                Map<String,Object> m = Json.obj(Json.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
                String gd = Json.str(m, "gameDir");
                c.gameDir = gd == null ? root.resolve(".minecraft") : Path.of(gd);
                c.javaPath = Json.str(m, "javaPath");
                c.msClientId = Json.str(m, "msClientId");
                c.downloadMirror = Json.str(m, "downloadMirror");
                c.windowWidth = Json.intVal(m, "windowWidth", 960);
                c.windowHeight = Json.intVal(m, "windowHeight", 640);
                c.memoryMb = Json.intVal(m, "memoryMb", 2048);
                c.selectedVersion = Json.str(m, "selectedVersion");
                c.javaMajor = Json.intVal(m, "javaMajor", -1);
                c.accounts = new ArrayList<>();
                List<Object> a = Json.arr(m, "accounts");
                if (a != null) for (Object o : a) c.accounts.add(Json.obj(o));
                c.selectedAccount = Json.intVal(m, "selectedAccount", -1);
            } catch (Exception e) {
                c.defaults(root);
            }
        } else {
            c.defaults(root);
        }
        if (c.gameDir == null) c.gameDir = root.resolve(".minecraft");
        if (c.javaPath == null) c.javaPath = "";
        return c;
    }

    private void defaults(Path root) {
        this.root = root;
        this.gameDir = root.resolve(".minecraft");
    }

    public void save() {
        try {
            Files.createDirectories(root);
            Map<String,Object> m = Json.map(
                "gameDir", gameDir.toString(),
                "javaPath", javaPath == null ? "" : javaPath,
                "msClientId", msClientId,
                "downloadMirror", downloadMirror,
                "windowWidth", windowWidth,
                "windowHeight", windowHeight,
                "memoryMb", memoryMb,
                "selectedVersion", selectedVersion == null ? "" : selectedVersion,
                "javaMajor", javaMajor,
                "accounts", accounts,
                "selectedAccount", selectedAccount
            );
            Files.write(root.resolve("config.json"), Json.stringify(m).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("保存配置失败", e);
        }
    }

    public String mirrorBase() {
        return "mojang".equals(downloadMirror) ? "https://libraries.minecraft.net"
                                               : "https://bmclapi2.bangbang93.com";
    }

    public String manifestUrl() {
        return "mojang".equals(downloadMirror)
                ? "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
                : "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json";
    }

    public Path authlibInjectorPath() { return gameDir.resolve("authlib-injector.jar"); }
}
