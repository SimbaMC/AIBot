package com.bot.aibot.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChatImageRenderer {

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof ChatScreen)) return;

        Minecraft mc = Minecraft.getInstance();
        Style style = mc.gui.getChat().getClickedComponentStyleAt(event.getMouseX(), event.getMouseY());

        if (isImageStyle(style)) {
            String url = style.getClickEvent().getValue();
            renderImagePreview(event.getGuiGraphics(), url, event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.getButton() != 0) return;

        Style style = mc.gui.getChat().getClickedComponentStyleAt(event.getMouseX(), event.getMouseY());
        if (isImageStyle(style)) {
            String url = style.getClickEvent().getValue();
            event.setCanceled(true);
            mc.setScreen(new ImagePreviewScreen(url));
        }
    }

    private static boolean isImageStyle(Style style) {
        if (style == null || style.getClickEvent() == null) return false;
        if (style.getClickEvent().getAction() != ClickEvent.Action.OPEN_URL) return false;
        String url = style.getClickEvent().getValue();
        return url.startsWith("http") && (
                url.contains("multimedia.nt.qq.com") ||
                        url.contains("gchat.qpic.cn") ||
                        url.contains("c2cpic") ||
                        url.contains("chatimg") ||
                        url.endsWith(".jpg") ||
                        url.endsWith(".png") ||
                        url.endsWith(".gif") || // 加上gif
                        url.contains("url=")
        );
    }

    private static void renderImagePreview(net.minecraft.client.gui.GuiGraphics g, String url, int mx, int my) {
        // --- 这里的逻辑变了 ---
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