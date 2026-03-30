package com.bot.aibot.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ImagePreviewScreen extends Screen {
    private final String imageUrl;
    private final Component title;

    public ImagePreviewScreen(String imageUrl) {
        super(Component.literal("图片预览"));
        this.imageUrl = imageUrl;
        this.title = Component.literal("图片预览 (点击任意处关闭)");
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);

        // 绘制标题
        g.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // 获取纹理
        ResourceLocation tex = null;
        Object obj = ImageCacheManager.getTextureOrAnim(imageUrl);
        if (obj instanceof ResourceLocation) tex = (ResourceLocation) obj;
        else if (obj instanceof AnimatedTexture) tex = ((AnimatedTexture) obj).getCurrentFrame();

        if (tex != null) {
            RenderSystem.enableBlend();
            // 简单渲染：居中显示，保持一定的边距
            int padding = 40;
            int maxW = this.width - padding * 2;
            int maxH = this.height - padding * 2;


            g.blit(tex, padding, padding, 0, 0, maxW, maxH, maxW, maxH);
        } else {
            g.drawCenteredString(this.font, "图片加载中...", this.width / 2, this.height / 2, 0xAAAAAA);
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // 点击任意位置关闭
        this.onClose();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        // 按 ESC 关闭
        if (key == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(key, scancode, mods);
    }
}