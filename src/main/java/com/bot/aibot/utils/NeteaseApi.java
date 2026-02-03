package com.bot.aibot.utils;

import com.bot.aibot.config.BotConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.RandomStringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class NeteaseApi {

    // 注意：基础 URL 这里的 /weapi 可能会在下面被覆盖
    private static final String BASE_URL = "https://music.163.com/weapi";

    // 强制直连，修复 Timeout
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(ProxySelector.of(null))
            .build();

    // 1. 搜索歌曲 (保持原样，这个通常能通)
    public static String search(String keyword) {
        System.out.println(">>> [Netease] 正在搜索: " + keyword);
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("s", keyword);
            data.put("type", 1);
            data.put("limit", 1);
            data.put("offset", 0);
            data.put("csrf_token", "");
            // 搜索还是走加密通道，因为这个接口很少封 IP
            String jsonResp = request(BASE_URL + "/cloudsearch/get/web", data);

            if (jsonResp == null) return null;
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.has("result") && !root.get("result").isJsonNull()) {
                JsonObject result = root.getAsJsonObject("result");
                if (result.has("songs") && result.get("songs").getAsJsonArray().size() > 0) {
                    String id = result.getAsJsonArray("songs").get(0).getAsJsonObject().get("id").getAsString();
                    System.out.println(">>> [Netease] 找到 ID: " + id);
                    return id;
                }
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 2. 获取 MP3 直链 (保持原样)
    public static String getSongUrl(String songId) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("ids", "[" + songId + "]");
            data.put("level", "standard");
            data.put("encodeType", "mp3");
            data.put("csrf_token", "");

            String jsonResp = request(BASE_URL + "/song/enhance/player/url/v1", data);

            if (jsonResp == null) return null;
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.get("code").getAsInt() != 200) return null;
            JsonArray dataArr = root.getAsJsonArray("data");
            if (dataArr.size() == 0) return null;
            return dataArr.get(0).getAsJsonObject().get("url").getAsString();
        } catch (Exception e) { return null; }
    }

    // ================= [核心修改] 扫码登录 (Raw API /api/ 通道) =================
    // 这种通道不走 weapi 加密，极大降低 404 概率

    // 3. 获取登录 Key (旧版 UUID 模式)
    public static String getLoginKey() {
        System.out.println(">>> [Debug] 1. 获取 Key (Raw Mode)");
        try {
            // 直接请求 /api/ 接口，不加密
            // url: https://music.163.com/api/login/qrcode/unikey
            String url = "https://music.163.com/api/login/qrcode/unikey?type=1";

            String jsonResp = requestRaw(url, "type=1");
            System.out.println(">>> [Debug] Key Resp: " + jsonResp);

            if (jsonResp == null) return null;
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();

            if (root.get("code").getAsInt() == 200) {
                return root.get("unikey").getAsString();
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // 4. 获取二维码链接
    public static String getLoginQrUrl(String key) {
        // 旧版 UUID 对应的二维码链接
        return "https://music.163.com/login?codekey=" + key;
    }

    // 5. 检查扫码状态 (Raw Mode)
    public static LoginResult checkLoginStatus(String key) {
        try {
            // 【修改】去掉 /client，并加上时间戳防止缓存
            // 原来: https://music.163.com/api/login/qrcode/client/check
            // 现在: https://music.163.com/api/login/qrcode/check
            String url = "https://music.163.com/api/login/qrcode/check?timerstamp=" + System.currentTimeMillis();

            String rawParams = "key=" + key + "&type=1";

            return requestRawForLogin(url, rawParams);
        } catch (Exception e) { e.printStackTrace(); }
        return new LoginResult(800, null, "接口异常");
    }

    // ================= 辅助方法 =================

    public static class LoginResult {
        public int code;
        public String cookie;
        public String message;
        public LoginResult(int code, String cookie, String message) {
            this.code = code; this.cookie = cookie; this.message = message;
        }
    }

    // [新增] 纯文本请求，不加密，专治 404
    private static String requestRaw(String url, String formData) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36") // 伪装成 Chrome
                .header("Referer", "https://music.163.com/")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    // [新增] 纯文本请求，带 Cookie 解析
    private static LoginResult requestRawForLogin(String url, String formData) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .header("Referer", "https://music.163.com/")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        String body = resp.body();

         System.out.println(">>> [Debug-Check] " + body); // 调试用

        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        int code = root.get("code").getAsInt();
        String cookie = "";

        if (code == 803) {
            StringBuilder cookieBuilder = new StringBuilder();
            resp.headers().allValues("set-cookie").forEach(v -> {
                cookieBuilder.append(v.split(";")[0]).append("; ");
            });
            cookie = cookieBuilder.toString();
            if (cookie.isEmpty() && root.has("cookie")) {
                cookie = root.get("cookie").getAsString();
            }
        }
        return new LoginResult(code, cookie, root.has("message") ? root.get("message").getAsString() : "");
    }

    // [保留] 旧的加密请求 (给 Search 和 getSongUrl 用)
    private static String request(String url, Map<String, Object> data) throws Exception {
        String jsonText = new com.google.gson.Gson().toJson(data);
        Map<String, String> encrypted = encrypt(jsonText);
        String formData = "params=" + URLEncoder.encode(encrypted.get("params"), StandardCharsets.UTF_8) +
                "&encSecKey=" + URLEncoder.encode(encrypted.get("encSecKey"), StandardCharsets.UTF_8);

        String cookie = "";
        try {
            if (BotConfig.SERVER != null && BotConfig.SERVER.neteaseCookie != null) {
                cookie = BotConfig.SERVER.neteaseCookie.get();
            }
        } catch (Exception e) { }
        if (cookie == null || cookie.isEmpty()) cookie = "os=pc; appver=2.9.7";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .header("Referer", "https://music.163.com/")
                .header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    // 加密算法保持不变...
    private static final String MODULUS = "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";
    private static final String NONCE = "0CoJUm6Qyw8W8jud";
    private static final String PUB_KEY = "010001";
    private static final String IV = "0102030405060708";

    private static Map<String, String> encrypt(String text) throws Exception {
        String secKey = RandomStringUtils.randomAlphanumeric(16);
        String encText = aesEncrypt(aesEncrypt(text, NONCE), secKey);
        String encSecKey = rsaEncrypt(secKey, PUB_KEY, MODULUS);
        Map<String, String> map = new HashMap<>();
        map.put("params", encText);
        map.put("encSecKey", encSecKey);
        return map;
    }
    private static String aesEncrypt(String text, String key) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec iv = new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, iv);
        byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }
    private static String rsaEncrypt(String text, String pubKey, String modulus) {
        StringBuilder reversedText = new StringBuilder(text).reverse();
        BigInteger biText = new BigInteger(1, reversedText.toString().getBytes(StandardCharsets.UTF_8));
        BigInteger biPubKey = new BigInteger(pubKey, 16);
        BigInteger biModulus = new BigInteger(modulus, 16);
        BigInteger result = biText.modPow(biPubKey, biModulus);
        String hex = result.toString(16);
        while (hex.length() < 256) hex = "0" + hex;
        return hex;
    }
}