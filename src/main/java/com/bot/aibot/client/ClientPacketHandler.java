package com.bot.aibot.client;

import net.minecraft.client.Minecraft;

public final class ClientPacketHandler {

    public static void handle(final int action, final String data, long extra) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                public void run() {
                    if (action == 0) ClientMusicManager.play(data, "正在播放...", extra);
                    else if (action == 1) ClientMusicManager.stop();
                    else if (action >= 2 && action <= 4) Minecraft.getMinecraft()
                        .displayGuiScreen(new MusicPlayerScreen());
                    else if (action == 5) MusicPlayerScreen.resetBroadcastCooldown();
                }
            });
    }
}
