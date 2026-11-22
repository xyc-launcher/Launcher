package com.mclauncher.core;

import java.util.Map;

/** 皮肤站外置登录（Yggdrasil）：POST /authserver/authenticate。 */
public class SkinStationAuth {

    public static String normalize(String api) {
        if (api == null) return "";
        api = api.trim();
        while (api.endsWith("/")) api = api.substring(0, api.length() - 1);
        return api;
    }

    public static Account login(String api, String username, String password) throws Exception {
        String base = normalize(api);
        String req = Json.stringify(Json.map(
            "agent", Json.map("name", "Minecraft", "version", 1),
            "username", username,
            "password", password,
            "requestUser", true,
            "clientToken", java.util.UUID.randomUUID().toString()));
        String resp;
        try {
            resp = Http.postJson(base + "/authserver/authenticate", req);
        } catch (Exception e) {
            throw new Exception("无法连接皮肤站 " + base + "：" + e.getMessage());
        }
        Map<String,Object> m = Json.obj(Json.parse(resp));
        if (m.get("error") != null) {
            String err = Json.str(m, "errorMessage");
            throw new Exception("皮肤站登录失败: " + (err == null ? Json.str(m, "error") : err));
        }
        Map<String,Object> profile = Json.obj(m, "selectedProfile");
        if (profile == null) throw new Exception("皮肤站未返回角色资料");
        String id = Json.str(profile, "id");
        Account a = new Account(Json.str(profile, "name"), "skinstation", formatUuid(id),
                                Json.str(m, "accessToken"));
        a.skinServer = base;
        return a;
    }

    /** 兼容 32 位无连字符 id。 */
    private static String formatUuid(String id) {
        if (id != null && id.length() == 32)
            return id.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
        return id;
    }
}
