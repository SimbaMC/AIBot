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
import java.util.*;

public class NeteaseApi {

    private static final String BASE_URL = "https://music.163.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36";

    // 1. Cookie 管理器
    private static final CookieManager cookieManager = new CookieManager();
    static {
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    }

    // 2. 独立的 Client (必须独立，因为需要绑定 CookieManager)
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .cookieHandler(cookieManager)
            .proxy(ProxySelector.getDefault())
            .build();

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

    // ================= Cookie 管理 =================

    public static void loadCookies() {
        try {
            initBaseCookie();
            if (BotConfig.CLIENT == null) return;

            String savedCookie = BotConfig.CLIENT.neteaseCookie.get();
            if (savedCookie == null || savedCookie.isEmpty()) return;

            System.out.println(">>> [API] 正在恢复登录状态...");
            URI uri = URI.create(BASE_URL);
            CookieStore store = cookieManager.getCookieStore();

            String[] parts = savedCookie.split(";");
            for (String part : parts) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2) {
                    HttpCookie cookie = new HttpCookie(kv[0], kv[1]);
                    cookie.setPath("/");
                    cookie.setDomain(".music.163.com");
                    store.add(uri, cookie);
                }
            }
            System.out.println(">>> [API] 登录状态恢复成功！");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void initBaseCookie() {
        try {
            URI uri = URI.create(BASE_URL);
            Map<String, List<String>> cookies = new HashMap<>();
            cookies.put("Set-Cookie", List.of(
                    "os=pc; Path=/; Domain=.music.163.com",
                    "appver=2.7.1.198277; Path=/; Domain=.music.163.com"
            ));
            cookieManager.put(uri, cookies);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ================= 核心请求封装 (本次优化重点) =================

    /**
     * 通用底层请求方法
     */
    private static HttpResponse<String> sendInternal(String url, String postData) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Referer", BASE_URL)
                .header("Accept", "*/*")
                .header("Content-Type", "application/x-www-form-urlencoded");

        if (postData != null) {
            builder.POST(HttpRequest.BodyPublishers.ofString(postData));
        } else {
            builder.GET();
        }

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // 普通请求（返回 Body）
    private static String requestRaw(String url, String formData) throws Exception {
        return sendInternal(url, formData).body();
    }

    // 加密请求
    private static String request(String url, Map<String, Object> data) throws Exception {
        String jsonText = new com.google.gson.Gson().toJson(data);
        Map<String, String> encrypted = encrypt(jsonText);
        String formData = "params=" + URLEncoder.encode(encrypted.get("params"), StandardCharsets.UTF_8) +
                "&encSecKey=" + URLEncoder.encode(encrypted.get("encSecKey"), StandardCharsets.UTF_8);
        return requestRaw(url, formData);
    }

    // ================= 业务功能 =================

    public static String getLoginKey() {
        try {
            initBaseCookie();
            String jsonResp = requestRaw("https://music.163.com/api/login/qrcode/unikey", "type=1");
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.has("unikey")) return root.get("unikey").getAsString();
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public static String getLoginQrUrl(String key) {
        return "https://music.163.com/login?codekey=" + key;
    }

    public static LoginResult checkLoginStatus(String key) {
        try {
            String url = "https://music.163.com/api/login/qrcode/client/login";
            String formData = "key=" + key + "&type=1";

            // 这里逻辑稍微特殊，需要处理 Set-Cookie，所以不直接调 requestRaw
            HttpResponse<String> resp = sendInternal(url, formData);
            String body = resp.body();

            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            int code = root.has("code") ? root.get("code").getAsInt() : 500;
            String cookie = "";

            if (code == 803) {
                StringBuilder sb = new StringBuilder();
                cookieManager.getCookieStore().getCookies().forEach(c ->
                        sb.append(c.getName()).append("=").append(c.getValue()).append("; ")
                );
                cookie = sb.toString();
                if (cookie.isEmpty() && root.has("cookie")) {
                    cookie = root.get("cookie").getAsString();
                }
            }
            return new LoginResult(code, cookie, root.has("message") ? root.get("message").getAsString() : "");
        } catch (Exception e) { e.printStackTrace(); }
        return new LoginResult(800, null, "接口异常");
    }

    // 搜索 (返回第一首 ID)
    public static String search(String keyword) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("s", keyword); data.put("type", 1); data.put("limit", 1); data.put("offset", 0); data.put("csrf_token", "");
            String jsonResp = request("https://music.163.com/weapi/cloudsearch/get/web", data);
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.has("result") && !root.get("result").isJsonNull()) {
                JsonObject result = root.getAsJsonObject("result");
                if(result.has("songs")) {
                    return result.getAsJsonArray("songs").get(0).getAsJsonObject().get("id").getAsString();
                }
            }
        } catch (Exception e) {} return null;
    }

    // 获取播放链接
    public static String getSongUrl(String songId) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("ids", "[" + songId + "]"); data.put("level", "standard"); data.put("encodeType", "mp3"); data.put("csrf_token", "");
            String jsonResp = request("https://music.163.com/weapi/song/enhance/player/url/v1", data);
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.get("code").getAsInt() == 200) {
                JsonArray arr = root.getAsJsonArray("data");
                if(arr.size() > 0) return arr.get(0).getAsJsonObject().get("url").getAsString();
            }
        } catch (Exception e) {} return null;
    }

    // 获取 UID
    public static long getMyUid() {
        try {
            String jsonResp = requestRaw("https://music.163.com/api/nuser/account/get", null);
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.has("account") && !root.get("account").isJsonNull()) {
                return root.getAsJsonObject("account").get("id").getAsLong();
            }
        } catch (Exception e) { System.err.println(">>> [API] 获取 UID 失败: " + e.getMessage()); }
        return 0;
    }

    // 获取歌单列表
    public static JsonArray getUserPlaylists(long uid) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("uid", uid); data.put("limit", 30); data.put("offset", 0); data.put("csrf_token", "");
            String jsonResp = request("https://music.163.com/weapi/user/playlist", data);
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.get("code").getAsInt() == 200) return root.getAsJsonArray("playlist");
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // 获取歌单内歌曲 ID
    public static List<Long> getPlaylistSongIds(long playlistId) {
        List<Long> ids = new ArrayList<>();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("id", playlistId); data.put("n", 0); data.put("csrf_token", "");
            String jsonResp = request("https://music.163.com/weapi/v6/playlist/detail", data);
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.get("code").getAsInt() == 200) {
                JsonArray trackIds = root.getAsJsonObject("playlist").getAsJsonArray("trackIds");
                for (int i = 0; i < trackIds.size(); i++) {
                    ids.add(trackIds.get(i).getAsJsonObject().get("id").getAsLong());
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return ids;
    }

    // 批量获取详情
    public static List<SongInfo> getSongsDetail(List<Long> songIds) {
        List<SongInfo> list = new ArrayList<>();
        if (songIds.isEmpty()) return list;
        try {
            Map<String, Object> data = new HashMap<>();
            List<Map<String, Object>> cList = new ArrayList<>();
            for (Long id : songIds) {
                Map<String, Object> item = new HashMap<>(); item.put("id", id); cList.add(item);
            }
            data.put("c", new com.google.gson.Gson().toJson(cList)); data.put("csrf_token", "");
            String jsonResp = request("https://music.163.com/weapi/v3/song/detail", data);
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.has("songs")) {
                JsonArray songs = root.getAsJsonArray("songs");
                for (int i = 0; i < songs.size(); i++) {
                    JsonObject song = songs.get(i).getAsJsonObject();
                    String id = song.get("id").getAsString();
                    String name = song.get("name").getAsString();
                    long duration = song.has("dt") ? song.get("dt").getAsLong() : 0;
                    String artist = "未知";
                    if (song.has("ar")) artist = song.getAsJsonArray("ar").get(0).getAsJsonObject().get("name").getAsString();
                    list.add(new SongInfo(id, name, artist, duration));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // GUI 搜索列表 (新增)
    public static List<SongInfo> searchList(String keyword) {
        List<SongInfo> list = new ArrayList<>();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("s", keyword); data.put("type", 1); data.put("limit", 20); data.put("offset", 0); data.put("csrf_token", "");
            String jsonResp = request("https://music.163.com/weapi/cloudsearch/get/web", data);
            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.has("result") && !root.get("result").isJsonNull()) {
                JsonObject result = root.getAsJsonObject("result");
                if (result.has("songs")) {
                    JsonArray songs = result.getAsJsonArray("songs");
                    for (int i = 0; i < songs.size(); i++) {
                        JsonObject song = songs.get(i).getAsJsonObject();
                        String id = song.get("id").getAsString();
                        String name = song.get("name").getAsString();
                        long duration = song.has("dt") ? song.get("dt").getAsLong() : 0;
                        String artist = "未知歌手";
                        if (song.has("ar")) {
                            JsonArray ar = song.getAsJsonArray("ar");
                            if (ar.size() > 0) artist = ar.get(0).getAsJsonObject().get("name").getAsString();
                        }
                        list.add(new SongInfo(id, name, artist, duration));
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // ================= 加密算法 (保持不变) =================
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