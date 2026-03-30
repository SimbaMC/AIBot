package com.bot.aibot.client;

import com.bot.aibot.utils.ApngUtils;
import com.bot.aibot.utils.GifUtils;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ImageCacheManager {

    private static final Map<String, Object> TEXTURE_CACHE = new HashMap<>();
    private static final Map<String, Boolean> LOADING_STATUS = new HashMap<>();
    private static final Set<String> FAILED_URLS = Collections.synchronizedSet(new HashSet<>());

    private static final HttpClient IMAGE_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static Object getTextureOrAnim(String url) {
        if (TEXTURE_CACHE.containsKey(url)) return TEXTURE_CACHE.get(url);
        if (FAILED_URLS.contains(url)) return null;
        if (!LOADING_STATUS.containsKey(url)) startDownload(url);
        return null;
    }

    public static ResourceLocation getTexture(String url) {
        Object obj = getTextureOrAnim(url);
        if (obj instanceof ResourceLocation) return (ResourceLocation) obj;
        if (obj instanceof AnimatedTexture) return ((AnimatedTexture) obj).getCurrentFrame();
        return null;
    }

    public static boolean isFailed(String url) {
        return FAILED_URLS.contains(url);
    }

    private static void startDownload(String rawUrl) {
        String url = rawUrl.replace("&amp;", "&");
        LOADING_STATUS.put(rawUrl, true);

        System.out.println(">>> [ImageCache] 开始下载: " + url);

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<byte[]> response = IMAGE_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() == 200) {
                    byte[] data = response.body();
                    if (data.length == 0) throw new RuntimeException("文件为空");

                    // 1. 优先尝试 GIF
                    if (isGif(data)) {
                        try {
                            GifUtils.ParsedGif gif = GifUtils.parseGif(data);
                            if (gif != null && !gif.images.isEmpty()) {
                                registerAnim(rawUrl, gif.images, gif.delays, "GIF");
                                return;
                            }
                        } catch (Exception e) {
                            System.err.println(">>> [ImageCache] GIF 解析异常: " + e.getMessage());
                        }
                    }

                    // 2. 【新增】尝试 APNG (QQ新表情)
                    else if (ApngUtils.isApng(data)) {
                        try {
                            System.out.println(">>> [ImageCache] 检测到 APNG，开始解析...");
                            ApngUtils.ParsedApng apng = ApngUtils.parse(data);
                            if (apng != null && !apng.images.isEmpty()) {
                                registerAnim(rawUrl, apng.images, apng.delays, "APNG");
                                return;
                            }
                        } catch (Exception e) {
                            System.err.println(">>> [ImageCache] APNG 解析异常，降级为静态图: " + e.getMessage());
                        }
                    }

                    // 3. 静态图 (包含 PNG/JPG/解析失败的动图第一帧)
                    try (ByteArrayInputStream bis = new ByteArrayInputStream(data)) {
                        NativeImage image = NativeImage.read(bis);
                        Minecraft.getInstance().execute(() -> {
                            try {
                                DynamicTexture texture = new DynamicTexture(image);
                                String safeId = "chat_img_" + Math.abs(url.hashCode());
                                ResourceLocation rl = Minecraft.getInstance().getTextureManager().register(safeId, texture);
                                TEXTURE_CACHE.put(rawUrl, rl);
                            } catch (Exception e) { markFailed(rawUrl, "纹理注册失败"); }
                        });
                    }
                } else {
                    throw new RuntimeException("HTTP " + response.statusCode());
                }
            } catch (Exception e) {
                if (e instanceof UnresolvedAddressException || e.getCause() instanceof UnresolvedAddressException) {
                    markFailed(rawUrl, "域名解析失败");
                } else if (e instanceof ConnectException) {
                    markFailed(rawUrl, "连接超时");
                } else {
                    markFailed(rawUrl, e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }).whenComplete((res, ex) -> LOADING_STATUS.remove(rawUrl));
    }

    private static void registerAnim(String rawUrl, java.util.List<NativeImage> imgs, java.util.List<Integer> delays, String type) {
        Minecraft.getInstance().execute(() -> {
            try {
                AnimatedTexture anim = new AnimatedTexture();
                String baseId = "chat_anim_" + Math.abs(rawUrl.hashCode());
                for (int i = 0; i < imgs.size(); i++) {
                    NativeImage img = imgs.get(i);
                    DynamicTexture texture = new DynamicTexture(img);
                    ResourceLocation rl = Minecraft.getInstance().getTextureManager().register(baseId + "_" + i, texture);
                    anim.addFrame(rl, delays.get(i));
                }
                TEXTURE_CACHE.put(rawUrl, anim);
                System.out.println(">>> [ImageCache] " + type + " 注册成功 (" + imgs.size() + "帧): " + rawUrl);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private static void markFailed(String url, String reason) {
        System.err.println(">>> [ImageCache] 加载失败 [" + reason + "] URL: " + url);
        FAILED_URLS.add(url);
    }

    private static boolean isGif(byte[] data) {
        if (data.length < 6) return false;
        return data[0] == 'G' && data[1] == 'I' && data[2] == 'F';
    }
}