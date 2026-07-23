package com.bot.aibot.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.bot.aibot.config.ClientMusicConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class NeteaseApi {

    private static final String BASE = "https://music.163.com", UA = "Mozilla/5.0 AiBot/1.5.0";
    private static final CookieManager COOKIES = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private static final String MOD = "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";

    private NeteaseApi() {}

    public static final class LoginResult {

        public final int code;
        public final String cookie, message;

        LoginResult(int c, String k, String m) {
            code = c;
            cookie = k;
            message = m;
        }
    }

    public static synchronized void loadCookies() {
        try {
            URI u = new URI(BASE);
            COOKIES.getCookieStore()
                .removeAll();
            add(u, "os", "pc");
            add(u, "appver", "2.7.1.198277");
            String saved = ClientMusicConfig.cookie();
            for (String p : saved.split(";")) {
                String[] kv = p.trim()
                    .split("=", 2);
                if (kv.length == 2) add(u, kv[0], kv[1]);
            }
        } catch (Exception ignored) {}
    }

    private static void add(URI u, String n, String v) {
        HttpCookie c = new HttpCookie(n, v);
        c.setDomain(".music.163.com");
        c.setPath("/");
        COOKIES.getCookieStore()
            .add(u, c);
    }

    private static String send(String url, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Referer", BASE);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        Map<String, List<String>> hs = COOKIES.get(new URI(url), Collections.<String, List<String>>emptyMap());
        List<String> ck = hs.get("Cookie");
        if (ck != null && !ck.isEmpty()) c.setRequestProperty("Cookie", ck.get(0));
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            try (OutputStream o = c.getOutputStream()) {
                o.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        COOKIES.put(new URI(url), c.getHeaderFields());
        InputStream raw = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8))) {
            StringBuilder s = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null && s.length() < 2_000_000) s.append(l);
            return s.toString();
        } finally {
            c.disconnect();
        }
    }

    private static JsonObject post(String url, Map<String, Object> d) throws Exception {
        String text = new Gson().toJson(d), key = randomKey();
        String params = aes(aes(text, "0CoJUm6Qyw8W8jud"), key);
        String reversed = new StringBuilder(key).reverse()
            .toString();
        String enc = new BigInteger(1, reversed.getBytes(StandardCharsets.UTF_8))
            .modPow(new BigInteger("010001", 16), new BigInteger(MOD, 16))
            .toString(16);
        while (enc.length() < 256) enc = "0" + enc;
        return parse(send(url, "params=" + URLEncoder.encode(params, "UTF-8") + "&encSecKey=" + enc));
    }

    private static String randomKey() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom r = new SecureRandom();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 16; i++) s.append(chars.charAt(r.nextInt(chars.length())));
        return s.toString();
    }

    private static String aes(String text, String key) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(
            Cipher.ENCRYPT_MODE,
            new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
            new IvParameterSpec("0102030405060708".getBytes(StandardCharsets.UTF_8)));
        return Base64.getEncoder()
            .encodeToString(c.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static JsonObject parse(String s) {
        return new JsonParser().parse(s)
            .getAsJsonObject();
    }

    public static String getLoginKey() {
        try {
            JsonObject o = parse(send(BASE + "/api/login/qrcode/unikey", "type=1"));
            return o.has("unikey") ? o.get("unikey")
                .getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getLoginQrUrl(String key) {
        return BASE + "/login?codekey=" + key;
    }

    public static LoginResult checkLoginStatus(String key) {
        try {
            JsonObject o = parse(
                send(BASE + "/api/login/qrcode/client/login", "key=" + URLEncoder.encode(key, "UTF-8") + "&type=1"));
            int code = o.has("code") ? o.get("code")
                .getAsInt() : 800;
            String cookie = cookieString();
            if (code == 803) ClientMusicConfig.saveCookie(cookie);
            return new LoginResult(
                code,
                cookie,
                o.has("message") ? o.get("message")
                    .getAsString() : "");
        } catch (Exception e) {
            return new LoginResult(800, "", "接口异常");
        }
    }

    private static String cookieString() {
        StringBuilder s = new StringBuilder();
        for (HttpCookie c : COOKIES.getCookieStore()
            .getCookies())
            s.append(c.getName())
                .append('=')
                .append(c.getValue())
                .append("; ");
        return s.toString();
    }

    public static long getMyUid() {
        try {
            JsonObject o = parse(send(BASE + "/api/nuser/account/get", null));
            return o.has("account") && !o.get("account")
                .isJsonNull() ? o.getAsJsonObject("account")
                    .get("id")
                    .getAsLong() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static Map<String, Object> map(Object... v) {
        Map<String, Object> m = new HashMap<String, Object>();
        for (int i = 0; i < v.length; i += 2) m.put((String) v[i], v[i + 1]);
        m.put("csrf_token", "");
        return m;
    }

    public static List<SongInfo> searchList(String q) {
        try {
            return songs(
                post(BASE + "/weapi/cloudsearch/get/web", map("s", q, "type", 1, "limit", 50, "offset", 0))
                    .getAsJsonObject("result")
                    .getAsJsonArray("songs"));
        } catch (Exception e) {
            return new ArrayList<SongInfo>();
        }
    }

    public static String getSongUrl(String id) {
        try {
            JsonArray a = post(
                BASE + "/weapi/song/enhance/player/url/v1",
                map("ids", "[" + id + "]", "level", "standard", "encodeType", "mp3")).getAsJsonArray("data");
            return a.size() > 0 && !a.get(0)
                .getAsJsonObject()
                .get("url")
                .isJsonNull() ? a.get(0)
                    .getAsJsonObject()
                    .get("url")
                    .getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static JsonArray getUserPlaylists(long uid) {
        try {
            return post(BASE + "/weapi/user/playlist", map("uid", uid, "limit", 100, "offset", 0))
                .getAsJsonArray("playlist");
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Long> getPlaylistSongIds(long id) {
        List<Long> r = new ArrayList<Long>();
        try {
            JsonArray a = post(BASE + "/weapi/v6/playlist/detail", map("id", id, "n", 0)).getAsJsonObject("playlist")
                .getAsJsonArray("trackIds");
            for (JsonElement e : a) r.add(
                e.getAsJsonObject()
                    .get("id")
                    .getAsLong());
        } catch (Exception ignored) {}
        return r;
    }

    public static List<SongInfo> getSongsDetail(List<Long> ids) {
        try {
            List<Map<String, Object>> c = new ArrayList<Map<String, Object>>();
            for (Long id : ids) c.add(map("id", id));
            return songs(post(BASE + "/weapi/v3/song/detail", map("c", new Gson().toJson(c))).getAsJsonArray("songs"));
        } catch (Exception e) {
            return new ArrayList<SongInfo>();
        }
    }

    private static List<SongInfo> songs(JsonArray a) {
        List<SongInfo> r = new ArrayList<SongInfo>();
        if (a == null) return r;
        for (JsonElement e : a) {
            JsonObject s = e.getAsJsonObject();
            JsonArray ar = s.has("ar") ? s.getAsJsonArray("ar") : s.getAsJsonArray("artists");
            String artist = ar != null && ar.size() > 0 ? ar.get(0)
                .getAsJsonObject()
                .get("name")
                .getAsString() : "未知歌手";
            r.add(
                new SongInfo(
                    s.get("id")
                        .getAsString(),
                    s.get("name")
                        .getAsString(),
                    artist,
                    s.has("dt") ? s.get("dt")
                        .getAsLong()
                        : s.has("duration") ? s.get("duration")
                            .getAsLong() : 0));
        }
        return r;
    }
}
