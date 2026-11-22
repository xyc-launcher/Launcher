package com.mclauncher.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** 账号：offline 离线 / msa 微软正版 / skinstation 皮肤站外置登录。 */
public class Account {
    public String name;
    public String type;
    public String uuid;
    public String accessToken;
    public String refreshToken;
    public long expiry;
    public String skinServer;

    public Account(String name, String type, String uuid, String accessToken) {
        this.name = name;
        this.type = type;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.refreshToken = null;
        this.expiry = 0;
        this.skinServer = null;
    }

    public static Account offline(String name) {
        String uuid = java.util.UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        return new Account(name, "offline", uuid, "0");
    }

    public Map<String,Object> toMap() {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("name", name == null ? "" : name);
        m.put("type", type == null ? "" : type);
        m.put("uuid", uuid == null ? "" : uuid);
        m.put("accessToken", accessToken == null ? "" : accessToken);
        m.put("refreshToken", refreshToken == null ? "" : refreshToken);
        m.put("expiry", expiry);
        m.put("skinServer", skinServer == null ? "" : skinServer);
        return m;
    }

    public static Account fromMap(Map<String,Object> m) {
        Account a = new Account(Json.str(m, "name"), Json.str(m, "type"),
                                Json.str(m, "uuid"), Json.str(m, "accessToken"));
        a.refreshToken = Json.str(m, "refreshToken");
        Number e = Json.num(m, "expiry");
        a.expiry = e == null ? 0 : e.longValue();
        a.skinServer = Json.str(m, "skinServer");
        return a;
    }

    public boolean tokenExpired() {
        return "msa".equals(type) && expiry > 0 && System.currentTimeMillis() > expiry - 60000;
    }

    public String displayType() {
        if ("msa".equals(type)) return "微软正版";
        if ("skinstation".equals(type)) return "皮肤站";
        return "离线";
    }
}
