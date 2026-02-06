package com.bot.aibot.client;

import com.bot.aibot.API.QrCode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LoginQrScreen extends Screen {
    private final String url;
    private QrCode qrCache;
    private final int scale = 5; // 二维码放大倍数

    public LoginQrScreen(String url) {
        super(Component.literal("扫码登录"));
        this.url = url;
    }

    @Override
    protected void init() {
        super.init();
        try {
            // 利用你现有的工具类生成矩阵
            this.qrCache = QrCode.encodeText(url, QrCode.Ecc.LOW);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. 绘制半透明黑色背景
        this.renderBackground(graphics);

        if (qrCache != null) {
            int qrSize = qrCache.size * scale;
            int startX = (this.width - qrSize) / 2;
            int startY = (this.height - qrSize) / 2;
            int border = 10;

            // 2. 绘制白色底板 (比二维码大一圈)
            graphics.fill(
                    startX - border, startY - border,
                    startX + qrSize + border, startY + qrSize + border,
                    0xFFFFFFFF // 白色
            );

            // 3. 绘制二维码黑点
            for (int y = 0; y < qrCache.size; y++) {
                for (int x = 0; x < qrCache.size; x++) {
                    // getModule: true=黑, false=白
                    if (qrCache.getModule(x, y)) {
                        int px = startX + x * scale;
                        int py = startY + y * scale;
                        graphics.fill(px, py, px + scale, py + scale, 0xFF000000); // 黑色
                    }
                }
            }
        }

        // 4. 绘制提示文字
        graphics.drawCenteredString(this.font, "§l请使用网易云音乐APP扫码", this.width / 2, this.height / 2 - 100, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "登录成功后窗口将自动关闭", this.width / 2, this.height / 2 + 100, 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true; // 允许按 ESC 关闭
    }
}