package com.mclauncher.core;

import java.util.Map;

/**
 * 微软正版登录（设备码全链路）：
 * OAuth2 设备码 → Xbox Live → XSTS → Minecraft Services → 个人资料。
 */
public class MicrosoftAuth {

    public interface DevicePrompt { void show(String userCode, String verificationUri, long expiresIn, long interval); }
    public interface WaitHook { void tick(long remaining); }

    private static final String DEVICE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE = "https://api.minecraftservices.com/minecraft/profile";

    /** 完整登录，返回带 msa 类型的账号。 */
    public static Account login(Config cfg, DevicePrompt prompt, WaitHook hook) throws Exception {
        // 1. 设备码
        String dcBody = "client_id=" + enc(cfg.msClientId) + "&scope=" + enc("XboxLive.signin offline_access");
        Map<String,Object> dc = Json.obj(Json.parse(Http.postForm(DEVICE, dcBody)));
        String deviceCode = Json.str(dc, "device_code");
        if (deviceCode == null) throw new Exception("获取设备码失败：请检查网络（login.microsoftonline.com 是否可达）后重试");
        long interval = Json.intVal(dc, "interval", 5);
        long expiresIn = Json.intVal(dc, "expires_in", 900);
        prompt.show(Json.str(dc, "user_code"), Json.str(dc, "verification_uri"), expiresIn, interval);

        // 2. 轮询 token
        String tokenBody0 = "client_id=" + enc(cfg.msClientId) + "&grant_type=device_code&device_code=" + enc(deviceCode);
        String accessToken = null, refreshToken = null;
        long deadline = System.currentTimeMillis() + expiresIn * 1000;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval * 1000);
            hook.tick((deadline - System.currentTimeMillis()) / 1000);
            String resp;
            try { resp = Http.postForm(TOKEN, tokenBody0); }
            catch (Exception e) { continue; }
            Map<String,Object> tm = Json.obj(Json.parse(resp));
            if (tm.get("access_token") != null) {
                accessToken = Json.str(tm, "access_token");
                refreshToken = Json.str(tm, "refresh_token");
                break;
            }
            String err = Json.str(tm, "error");
            if ("authorization_pending".equals(err)) continue;
            if ("slow_down".equals(err)) { interval++; continue; }
            throw new Exception(loginError(err, Json.str(tm, "error_description")));
        }
        if (accessToken == null) throw new Exception("登录超时，请重新尝试");

        return finishLogin(accessToken, refreshToken);
    }

    /** 用 refreshToken 刷新，返回新账号（保留名称/UUID）。 */
    public static Account refresh(Config cfg, Account old) throws Exception {
        String body = "client_id=" + enc(cfg.msClientId)
                + "&grant_type=refresh_token&refresh_token=" + enc(old.refreshToken)
                + "&scope=" + enc("XboxLive.signin offline_access");
        Map<String,Object> tm = Json.obj(Json.parse(Http.postForm(TOKEN, body)));
        String access = Json.str(tm, "access_token");
        String refresh = Json.str(tm, "refresh_token");
        if (access == null) throw new Exception("刷新失败，请重新登录");
        Account a = finishLogin(access, refresh != null ? refresh : old.refreshToken);
        return a;
    }

    private static Account finishLogin(String oauthToken, String refreshToken) throws Exception {
        // 3. Xbox Live
        String xblReq = Json.stringify(Json.map(
            "Properties", Json.map("AuthMethod", "RPS", "SiteName", "user.auth.xboxlive.com", "RpsTicket", "d=" + oauthToken),
            "RelyingParty", "http://auth.xboxlive.com", "TokenType", "JWT"));
        Map<String,Object> xbl = Json.obj(Json.parse(Http.postJson(XBL, xblReq)));
        // 取 DisplayClaims.xui[0].uhs（防御性取值，避免结构缺失时报 null）
        java.util.List<Object> xui = xbl == null ? null : Json.arr(Json.obj(xbl, "DisplayClaims"), "xui");
        Map<String,Object> xui0 = (xui != null && !xui.isEmpty()) ? Json.obj(xui.get(0)) : null;
        String uhs = xui0 == null ? null : Json.str(xui0, "uhs");
        String xblToken = Json.str(xbl, "Token");
        if (uhs == null || xblToken == null)
            throw new Exception("Xbox Live 认证响应异常（未取得令牌）：请检查网络或稍后重试");

        // 4. XSTS
        String xstsReq = Json.stringify(Json.map(
            "Properties", Json.map("SandboxId", "RETAIL", "UserTokens", java.util.List.of(xblToken)),
            "RelyingParty", "rp://api.minecraftservices.com/", "TokenType", "JWT"));
        Map<String,Object> xsts;
        try {
            xsts = Json.obj(Json.parse(Http.postJson(XSTS, xstsReq)));
        } catch (Exception e) {
            throw new Exception("XSTS 认证失败（该 Xbox 账号可能未关联或地区受限）: " + e.getMessage());
        }
        String xstsToken = Json.str(xsts, "Token");

        // 5. Minecraft Services
        String mcReq = Json.stringify(Json.map("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken));
        Map<String,Object> mc = Json.obj(Json.parse(Http.postJson(MC_LOGIN, mcReq)));
        String mcToken = Json.str(mc, "access_token");
        if (mcToken == null) throw new Exception("Minecraft 服务认证响应异常：请确认该微软账号已购买 Minecraft Java 版，或稍后重试");
        long expiresIn = Json.intVal(mc, "expires_in", 86400);

        // 6. 个人资料
        Map<String,Object> profile;
        try {
            profile = Json.obj(Json.parse(getWithAuth(MC_PROFILE, mcToken)));
        } catch (Exception e) {
            throw new Exception("未获取到 Minecraft 资料：该微软账号可能未购买 Java 版（可先离线试玩）");
        }
        String profileName = Json.str(profile, "name");
        String profileId = Json.str(profile, "id");
        if (profileName == null || profileId == null)
            throw new Exception("未获取到 Minecraft 玩家资料：该账号可能没有 Java 版角色");
        Account a = new Account(profileName, "msa", uuidFromHex(profileId), mcToken);
        a.refreshToken = refreshToken;
        a.expiry = System.currentTimeMillis() + expiresIn * 1000;
        return a;
    }

    private static String getWithAuth(String url, String bearer) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setRequestProperty("Authorization", "Bearer " + bearer);
        c.setRequestProperty("User-Agent", Http.UA);
        c.setConnectTimeout(Http.CONNECT_TIMEOUT);
        c.setReadTimeout(Http.READ_TIMEOUT);
        int code = c.getResponseCode();
        java.io.InputStream in = code == 200 ? c.getInputStream() : c.getErrorStream();
        String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        c.disconnect();
        if (code != 200) throw new Exception("HTTP " + code);
        return text;
    }

    /** Mojang 返回无连字符的 32 位 hex，转成标准 UUID 字符串。 */
    private static String uuidFromHex(String hex) {
        if (hex == null || hex.length() != 32) return hex;
        return hex.replaceAll(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
    }

    private static String loginError(String code, String desc) {
        if ("expired_token".equals(code)) return "登录已过期，请重试";
        if ("access_denied".equals(code)) return "已拒绝授权";
        return "微软登录失败: " + (desc == null ? code : desc);
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
