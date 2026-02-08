package com.bot.aibot.client;

import com.bot.aibot.utils.GifUtils;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ImageCacheManager {

    private static final Map<String, Object> TEXTURE_CACHE = new HashMap<>();
    private static final Map<String, Boolean> LOADING_STATUS = new HashMap<>();

    private static final HttpClient IMAGE_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public static Object getTextureOrAnim(String url) {
        if (TEXTURE_CACHE.containsKey(url)) return TEXTURE_CACHE.get(url);
        if (!LOADING_STATUS.containsKey(url)) startDownload(url);
        return null;
    }

    // 兼容接口
    public static ResourceLocation getTexture(String url) {
        Object obj = getTextureOrAnim(url);
        if (obj instanceof ResourceLocation) return (ResourceLocation) obj;
        if (obj instanceof AnimatedTexture) return ((AnimatedTexture) obj).getCurrentFrame();
        return null;
    }

    private static void startDownload(String rawUrl) {
        String url = rawUrl.replace("&amp;", "&");
        LOADING_STATUS.put(rawUrl, true);

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .GET()
                        .build();

                HttpResponse<byte[]> response = IMAGE_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() == 200) {
                    byte[] data = response.body();
                    System.out.println(">>> [ImageCache] 下载成功: " + url + " (大小: " + data.length + ")");

                    // --- GIF 处理分支 ---
                    if (isGif(data)) {
                        System.out.println(">>> [ImageCache] 识别为 GIF，开始解析...");
                        try {
                            GifUtils.ParsedGif gif = GifUtils.parseGif(data);
                            if (gif != null && !gif.images.isEmpty()) {
                                Minecraft.getInstance().execute(() -> {
                                    AnimatedTexture anim = new AnimatedTexture();
                                    String baseId = "chat_anim_" + Math.abs(url.hashCode());

                                    for (int i = 0; i < gif.images.size(); i++) {
                                        NativeImage img = gif.images.get(i);
                                        DynamicTexture texture = new DynamicTexture(img);
                                        ResourceLocation rl = Minecraft.getInstance().getTextureManager().register(baseId + "_" + i, texture);
                                        anim.addFrame(rl, gif.delays.get(i));
                                    }
                                    TEXTURE_CACHE.put(rawUrl, anim);
                                    LOADING_STATUS.remove(rawUrl);
                                    System.out.println(">>> [ImageCache] GIF 注册成功，帧数: " + gif.images.size());
                                });
                                return; // 成功退出
                            } else {
                                System.err.println(">>> [ImageCache] GIF 解析失败或只有0帧，尝试作为静态图加载");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    // --- 静态图降级处理 ---
                    try (ByteArrayInputStream bis = new ByteArrayInputStream(data)) {
                        NativeImage image = NativeImage.read(bis);
                        Minecraft.getInstance().execute(() -> {
                            DynamicTexture texture = new DynamicTexture(image);
                            String safeId = "chat_img_" + Math.abs(url.hashCode());
                            ResourceLocation rl = Minecraft.getInstance().getTextureManager().register(safeId, texture);
                            TEXTURE_CACHE.put(rawUrl, rl);
                            LOADING_STATUS.remove(rawUrl);
                        });
                    }
                } else {
                    System.err.println(">>> [ImageCache] 下载失败 Code: " + response.statusCode());
                    LOADING_STATUS.remove(rawUrl);
                }
            } catch (Exception e) {
                e.printStackTrace();
                LOADING_STATUS.remove(rawUrl);
            }
        });
    }

    private static boolean isGif(byte[] data) {
        if (data.length < 6) return false;
        return data[0] == 'G' && data[1] == 'I' && data[2] == 'F';
    }
}