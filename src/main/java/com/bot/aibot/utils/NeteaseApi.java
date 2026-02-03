package com.bot.aibot.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.RandomStringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class NeteaseApi {

    private static final String BASE_URL = "https://music.163.com/weapi";
    private static final HttpClient client = HttpClient.newHttpClient();

    private static final String MODULUS = "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";
    private static final String NONCE = "0CoJUm6Qyw8W8jud";
    private static final String PUB_KEY = "010001";
    private static final String IV = "0102030405060708";

    // --- 扫码登录专用接口 ---

    // 3. 获取登录 Key (第一步)
    public static String getLoginKey() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("type", 1);
            String jsonResp = request(BASE_URL + "/login/qr/key", data);

            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.get("code").getAsInt() == 200) {
                return root.get("data").getAsJsonObject().get("unikey").getAsString();
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // 4. 获取二维码内容 (第二步)
    // 返回的是一个 URL，为了方便，我们稍后会把它转成二维码图片的在线链接
    public static String getLoginQrUrl(String key) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("key", key);
            data.put("qrimg", true); // 请求 base64 图片数据 (这里我们主要用 url)
            String jsonResp = request(BASE_URL + "/login/qr/create", data);

            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.get("code").getAsInt() == 200) {
                return root.get("data").getAsJsonObject().get("qrurl").getAsString();
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // 5. 检查扫码状态 (第三步 - 轮询)
    // 返回值: 800=过期, 801=等待, 802=待确认, 803=成功(带Cookie)
    public static LoginResult checkLoginStatus(String key) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("key", key);
            data.put("type", 1);
            // 这一步非常特殊，必须获取 Response Headers 里的 Set-Cookie
            // 所以我们不能复用原来的 request 方法，得单独写一个能返回 Cookie 的
            return requestForLogin(BASE_URL + "/login/qr/check", data);
        } catch (Exception e) { e.printStackTrace(); }
        return new LoginResult(800, null, "接口异常");
    }

    // 简单的结果封装类
    public static class LoginResult {
        public int code;
        public String cookie;
        public String message;
        public LoginResult(int code, String cookie, String message) {
            this.code = code; this.cookie = cookie; this.message = message;
        }
    }

    // --- 登录专用的请求方法 (需要抓取 Set-Cookie) ---
    private static LoginResult requestForLogin(String url, Map<String, Object> data) throws Exception {
        String jsonText = new com.google.gson.Gson().toJson(data);
        Map<String, String> encrypted = encrypt(jsonText);
        String formData = "params=" + URLEncoder.encode(encrypted.get("params"), StandardCharsets.UTF_8) +
                "&encSecKey=" + URLEncoder.encode(encrypted.get("encSecKey"), StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://music.163.com/")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        int code = root.get("code").getAsInt();
        String cookie = null;

        // 如果登录成功 (803)，提取 Cookie
        if (code == 803) {
            // 从响应头里提取 Set-Cookie
            // Java 11 HttpClient 的 headers 处理比较特殊
            StringBuilder cookieBuilder = new StringBuilder();
            resp.headers().allValues("set-cookie").forEach(v -> {
                // 简单处理：提取分号前的部分
                cookieBuilder.append(v.split(";")[0]).append("; ");
            });
            cookie = cookieBuilder.toString();
        }

        return new LoginResult(code, cookie, root.get("message").getAsString());
    }

    // 1. 搜索歌曲
    public static String search(String keyword) {
        System.out.println(">>> [Netease] 正在搜索: " + keyword);
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("s", keyword);
            data.put("type", 1);
            data.put("limit", 1);
            data.put("offset", 0);
            data.put("csrf_token", ""); // 有时候需要空 token

            String jsonResp = request(BASE_URL + "/cloudsearch/get/web", data);

            // 【DEBUG】打印原始返回结果，这一步最关键！
            System.out.println(">>> [Netease] 搜索返回: " + jsonResp);

            if (jsonResp == null) return null;

            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.has("code") && root.get("code").getAsInt() != 200) {
                System.out.println(">>> [Netease] 错误代码: " + root.get("code").getAsInt());
                return null;
            }

            // 安全解析 result
            if (!root.has("result") || root.get("result").isJsonNull()) {
                System.out.println(">>> [Netease] 未找到 result 字段");
                return null;
            }

            JsonObject result = root.getAsJsonObject("result");
            if (!result.has("songs") || result.get("songs").getAsJsonArray().size() == 0) {
                System.out.println(">>> [Netease] songs 列表为空");
                return null;
            }

            String id = result.getAsJsonArray("songs").get(0).getAsJsonObject().get("id").getAsString();
            System.out.println(">>> [Netease] 找到 ID: " + id);
            return id;

        } catch (Exception e) {
            System.out.println(">>> [Netease] 搜索报错:");
            e.printStackTrace();
            return null;
        }
    }

    // 2. 获取 MP3 直链
    public static String getSongUrl(String songId) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("ids", "[" + songId + "]");
            data.put("level", "standard");
            data.put("encodeType", "mp3");
            data.put("csrf_token", "");

            String jsonResp = request(BASE_URL + "/song/enhance/player/url/v1", data);
            System.out.println(">>> [Netease] 获取URL返回: " + jsonResp);

            if (jsonResp == null) return null;

            JsonObject root = JsonParser.parseString(jsonResp).getAsJsonObject();
            if (root.get("code").getAsInt() != 200) return null;

            JsonArray dataArr = root.getAsJsonArray("data");
            if (dataArr.size() == 0) return null;

            String url = dataArr.get(0).getAsJsonObject().get("url").getAsString();
            return url;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String request(String url, Map<String, Object> data) throws Exception {
        String jsonText = new com.google.gson.Gson().toJson(data);
        Map<String, String> encrypted = encrypt(jsonText);

        String formData = "params=" + URLEncoder.encode(encrypted.get("params"), StandardCharsets.UTF_8) +
                "&encSecKey=" + URLEncoder.encode(encrypted.get("encSecKey"), StandardCharsets.UTF_8);

        // 【直接填入你刚才获取的 Cookie】
        // 这一大串是你提供的真实身份凭证，能通过 VIP 和风控检查
        String myCookie = "timing_user_id=time_dOIefJKJvk; JSESSIONID-WYYY=MC9m0g%2FIAznUudiUyWuaMNYqRcbaKZxRiN3HmekWOCWsnwZaoVm8oi866hw3Ctvxq%2F3rekCI7ENJ3t%2FgX0cpSDKZBC%2BCWGE%2F0Bvm%2BddnRax7%5C%2FrHn%2F6U7O%5CpbGxU8UVHPR2Dz2R%2BHRrpYCQShaYx1EN37pe4Idz7aliW2ZU3FbZXrpna%3A1770108555554; _iuqxldmzr_=32; _ntes_nnid=aeb9bab07f585514dfa7f1e52190109d,1770106755566; _ntes_nuid=aeb9bab07f585514dfa7f1e52190109d; Hm_lvt_d94b7d26fa25db7f6413fb58d7a438c7=1770106756; HMACCOUNT=51EDB68D3F2A8F32; NMTID=00OIUFTDm1y_Z-OEkVlhj10CZ6mvzcAAAGcIpWaMA; WM_NI=lupqreUMJ%2BziXEvgQg3PYn2fhPq4pPs%2FnaXj9xZjGfaeT8XJQ%2FCP07nqsbU0WQIHsDBmU9ypfRcuaLpLfpIwX3GUVsoUcuv5P6CL7UWaRF4t2Pm9E3kRLUDJl6xY8IsENHQ%3D; WM_NIKE=9ca17ae2e6ffcda170e2e6ee88ca4ead9df7afb2218bef8ea2d54e928b9fb1c63f93b59798ec5ca88a86a2fb2af0fea7c3b92ab0abc092f16f979386ccf9639c9bba88d46df190978fb16eac90bfd5f35cb1f1a282db62fba8968bc66ea3bb9cd9db4293f18db1b33daeafbd83d83c958f87a9f25082b39bdafc7ca68ead96e448b494aa91cc7d9ced89b6d579ad8bad96f53f94ea8dadb65f95bf9ea7b780f290a3dac839a98e84b6c742b0eab8babb43b19b82a9d437e2a3; WM_TID=pjGVvno6cl1FERQQUAbHtF0I02ONENVh; sDeviceId=YD-YPegy3v%2Bt5xFAhUFAAbD9AxcknLZZqRI; __snaker__id=6ZVMalzVCvWUJCU9; ntes_utid=tid._.SB%252Fx6PVwa1VARkQEBEKHtUwYgibJdsRP._.0; gdxidpyhxdE=nP26Ym5bU0ef72cy4HHwCpDQA0Y7K6PsB1wpSd0vDG%5CRfes%5C%5CANKBUKsKLkNpq6D%5CwhV5RNxxKwgAfzyf50EpmZV9v4pLHiArOVvD9Cvqbg8G3h%5ChY%2BHSsTzmMPRXc%5CafjAOTxejgzoy6n5ckS9Z9LWMLE%5CVyoAEWdG%5CbpHu4%5CnR%2BO80%3A1770108020470; __csrf=e9c7c8d41369978f1deac808da08eef6; MUSIC_U=00FE11B1593FF7BEB1A76D5C9D71AF4050DD501F73F17B0ACF89E0C8C38F21374F40EB97D70408570F221BBE7152DD68D956B73E1D624E631106F7D49163048FAF73D8E37A9230C0CB4B72F7A94A0B85887874E9B4344D35AACF546649A45D3C95A0753BD9EFA7159D0AB39451E38FAA41E12A48C91102198B84762C8260416C8D3BEBCF61770ABEE3CBC7499A307D6D8871C8279DA7B608C7A351CDDC7BBC300C0750F7F853E699D4995F25B5E8B69FDFD788E1C1C55655098A3BD0DB081EB04904651F40A4023560A164F524869C917D0341B09032DBF4AC10F21BE3FFB351A4C4A5CFF68DDAE81EF47E62EC2BBF63F44ADA31A75D8F1FD6760B52A13DF311E862996E365CA4CAA4BE453CE9261F48B163625FB14FF63B172BC3976A5BE08FF9631A251B0EECCF126381CDBC02DE9AF952EBB397A0B72A95D7AB9C8AB15099F71889CACEA26205530785D0EBC1B11035E30CFECB76DF7D8B3099ABB9625B619F7711F162AADC3B95C908DE5FB8F2633F6E8B4E54F17754A065D3B8509884E360F5F999B99FEB8CAD266030F7C992C60629B03F6B33B15D0FA937DD4B448990D0; Hm_lpvt_d94b7d26fa25db7f6413fb58d7a438c7=1770107133; ntes_kaola_ad=1";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://music.163.com/")
                .header("Cookie", myCookie) // 核心修改：使用你的 Cookie
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    // ... 加密方法 encrypt, aesEncrypt, rsaEncrypt 保持不变 ...
    // (请保留你原来文件底部的加密算法部分，那部分没问题)
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