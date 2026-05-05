package com.bot.aibot.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "aibot", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ChatImageRenderer {

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof ChatScreen)) return;

        Minecraft mc = Minecraft.getInstance();
        Style style = mc.gui.getChat().getClickedComponentStyleAt(event.getMouseX(), event.getMouseY());

        String url = getImageUrl(style);
        if (url != null) {
            renderImagePreview(event.getGuiGraphics(), url, event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.getButton() != 0) return;

        Style style = mc.gui.getChat().getClickedComponentStyleAt(event.getMouseX(), event.getMouseY());
        String url = getImageUrl(style);
        if (url != null) {
            event.setCanceled(true);
            mc.setScreen(new ImagePreviewScreen(url));
        }
    }

    private static String getImageUrl(Style style) {
        if (style == null) return null;
        String insertion = style.getInsertion();
        if (insertion != null && insertion.startsWith("aibot:image:")) {
            String markedUrl = insertion.substring("aibot:image:".length());
            return normalizeUrl(markedUrl);
        }
        if (style.getClickEvent() == null) return null;
        if (style.getClickEvent().getAction() != ClickEvent.Action.OPEN_URL) return null;
        String url = style.getClickEvent().getValue();
        if (url == null) return null;
        url = normalizeUrl(url);
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        boolean isImageUrl = lower.startsWith("http") && (
                lower.contains("multimedia.nt.qq.com") ||
                        lower.contains("qq.com.cn/download") ||
                        lower.contains("gchat.qpic.cn") ||
                        lower.contains("c2cpic") ||
                        lower.contains("chatimg") ||
                        lower.contains("qpic.cn") ||
                        lower.contains("koishi.js.org/qface") ||
                        lower.endsWith(".jpg") ||
                        lower.endsWith(".jpeg") ||
                        lower.endsWith(".png") ||
                        lower.endsWith(".gif") ||
                        lower.endsWith(".webp") ||
                        lower.contains("url=")
        );
        return isImageUrl ? url : null;
    }

    private static String normalizeUrl(String rawUrl) {
        return rawUrl.trim()
                .replace("&amp;", "&")
                .replace("&#44;", ",")
                .replace("&#91;", "[")
                .replace("&#93;", "]")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private static void renderImagePreview(net.minecraft.client.gui.GuiGraphics g, String url, int mx, int my) {

        Object obj = ImageCacheManager.getTextureOrAnim(url);
        ResourceLocation tex = null;

        if (obj instanceof ResourceLocation) {
            tex = (ResourceLocation) obj; // 静态图
        } else if (obj instanceof AnimatedTexture) {
            tex = ((AnimatedTexture) obj).getCurrentFrame(); // 动图：自动获取当前帧
        }

        int size = 120;
        int x = mx + 10;
        int y = my - size - 10;
        if (y < 0) y = my + 10;
        if (x + size > Minecraft.getInstance().getWindow().getGuiScaledWidth()) x = mx - size - 10;

        g.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xEE000000);

        if (tex != null) {
            RenderSystem.enableBlend();
            g.blit(tex, x, y, 0, 0, size, size, size, size);
        } else {
            g.drawCenteredString(Minecraft.getInstance().font, "加载中...", x + size/2, y + size/2 - 4, 0xFFFFFF);
        }
    }
}
