package com.bot.aibot.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

public final class ClientPacketHandler {

    public static void handle(final int action, final String data, long extra) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                public void run() {
                    if (action == 0) ClientMusicManager.play(data);
                    else if (action == 1) ClientMusicManager.stop();
                    else if (Minecraft.getMinecraft().thePlayer != null)
                        Minecraft.getMinecraft().thePlayer.addChatMessage(
                            new ChatComponentText(
                                "§e[AiBot] Music GUI/QR login is unavailable in this 1.7.10 build; use direct play commands."));
                }
            });
    }
}
