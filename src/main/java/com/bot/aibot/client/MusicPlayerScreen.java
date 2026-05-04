package com.bot.aibot.client;

import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.C2SMusicActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MusicPlayerScreen extends Screen {
    public static String EXPECTED_URL = "";
    public static void resetCooldown() { }
    public MusicPlayerScreen() { super(Component.literal("音乐播放器")); }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.addRenderableWidget(Button.builder(Component.literal("关闭播放器"), b -> this.onClose())
                .bounds(cx - 60, cy - 10, 120, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("停止播放"), b -> PacketHandler.sendToServer(new C2SMusicActionPacket(1)))
                .bounds(cx - 60, cy + 20, 120, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);
        g.drawCenteredString(this.font, "NeoForge 1.21.1 迁移中：播放器简化模式", this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }
}
