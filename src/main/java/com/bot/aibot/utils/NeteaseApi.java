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
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NeteaseApi {

    private static final String BASE_URL = "https://music.163.com";

    // 1. Cookie 管理器
    private static final CookieManager cookieManager = new CookieManager();
    static {
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    }

    // 2. Client 初始化
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .cookieHandler(cookieManager)
            .proxy(ProxySelector.of(null))
            .build();

    // 3. User-Agent (Chrome 96 - Mod 同款)
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36";

    // ================= 内部类 =================
    public static class LoginResult {
        public int code;
        public String cookie;
        public String message;

        public LoginResult(int code, String cookie, String message) {
            this.code = code;
            this.cookie = cookie;
            this.message = message;
        }
    }

    // ================= 4. 登录流程 (Mod Copy Mode) =================

    public static String getLoginKey() {
        System.out.println(">>> [Debug] 1. 获取 Key (Mod Copy Mode - No Host Header)");
        try {
            initBaseCookie();

            // 接口: /api/login/qrcode/unikey
            String url = "https://music.163.com/api/login/qrcode/unikey";
            String jsonResp = requestRaw(url, "type=1");

            System.out.println(">>> [Debug] Key Resp: " + jsonResp);

            if (jsonResp == null) return null;
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();

            if (root.has("unikey")) {
                return root.get("unikey").getAsString();
            } else if (root.has("code") && root.get("code").getAsInt() == 200) {
                return root.get("unikey").getAsString();
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public static String getLoginQrUrl(String key) {
        return "https://music.163.com/login?codekey=" + key;
    }

    public static LoginResult checkLoginStatus(String key) {
        try {
            // Mod 用的是 .../client/login
            String url = "https://music.163.com/api/login/qrcode/client/login";
            String formData = "key=" + key + "&type=1";

            return requestRawForLogin(url, formData);
        } catch (Exception e) { e.printStackTrace(); }
        return new LoginResult(800, null, "接口异常");
    }

    // ================= 5. 请求逻辑 =================

    private static void initBaseCookie() {
        try {
            URI uri = URI.create("https://music.163.com");
            Map<String, List<String>> cookies = new HashMap<>();
            // Cookie: appver=2.7.1.198277; os=pc;
            cookies.put("Set-Cookie", List.of(
                    "os=pc; Path=/; Domain=.music.163.com",
                    "appver=2.7.1.198277; Path=/; Domain=.music.163.com"
            ));
            cookieManager.put(uri, cookies);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static String requestRaw(String url, String formData) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "http://music.163.com")
                // .header("Host", "music.163.com")  <-- 【删掉了这行】Java 会自动填，手动填会报错
                .header("Accept", "*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.8,gl;q=0.6,zh-TW;q=0.4")
                .header("Content-Type", "application/x-www-form-urlencoded");

        if (formData != null) {
            builder.POST(HttpRequest.BodyPublishers.ofString(formData));
        } else {
            builder.GET();
        }

        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private static LoginResult requestRawForLogin(String url, String formData) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "http://music.163.com")
                // .header("Host", "music.163.com") <-- 【删掉了这行】
                .header("Accept", "*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.8,gl;q=0.6,zh-TW;q=0.4")
                .header("Content-Type", "application/x-www-form-urlencoded");

        if (formData != null) {
            builder.POST(HttpRequest.BodyPublishers.ofString(formData));
        } else {
            builder.GET();
        }

        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        String body = resp.body();

        System.out.println(">>> [Debug-Check] " + body);

        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        int code = root.has("code") ? root.get("code").getAsInt() : 500;
        String cookie = "";

        if (code == 803) {
            StringBuilder sb = new StringBuilder();
            cookieManager.getCookieStore().getCookies().forEach(c -> {
                sb.append(c.getName()).append("=").append(c.getValue()).append("; ");
            });
            cookie = sb.toString();
            if (cookie.isEmpty() && root.has("cookie")) {
                cookie = root.get("cookie").getAsString();
            }
        }
        return new LoginResult(code, cookie, root.has("message") ? root.get("message").getAsString() : "");
    }

    // ================= 6. 搜索 (保留) =================
    // 请务必保留 search, getSongUrl 和 encrypt 代码

    public static String search(String keyword) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("s", keyword); data.put("type", 1); data.put("limit", 1); data.put("offset", 0); data.put("csrf_token", "");
            String jsonResp = request("https://music.163.com/weapi/cloudsearch/get/web", data);
            if (jsonResp == null) return null;
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.has("result") && !root.get("result").isJsonNull() && root.getAsJsonObject("result").has("songs")) {
                return root.getAsJsonObject("result").getAsJsonArray("songs").get(0).getAsJsonObject().get("id").getAsString();
            }
        } catch (Exception e) {} return null;
    }
    public static String getSongUrl(String songId) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("ids", "[" + songId + "]"); data.put("level", "standard"); data.put("encodeType", "mp3"); data.put("csrf_token", "");
            String jsonResp = request("https://music.163.com/weapi/song/enhance/player/url/v1", data);
            if (jsonResp == null) return null;
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.get("code").getAsInt() == 200 && root.getAsJsonArray("data").size() > 0) return root.getAsJsonArray("data").get(0).getAsJsonObject().get("url").getAsString();
        } catch (Exception e) {} return null;
    }
    private static String request(String url, Map<String, Object> data) throws Exception {
        String jsonText = new com.google.gson.Gson().toJson(data);
        Map<String, String> encrypted = encrypt(jsonText);
        String formData = "params=" + URLEncoder.encode(encrypted.get("params"), StandardCharsets.UTF_8) +
                "&encSecKey=" + URLEncoder.encode(encrypted.get("encSecKey"), StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded").header("User-Agent", USER_AGENT).header("Referer", "https://music.163.com/").POST(HttpRequest.BodyPublishers.ofString(formData)).build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }
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