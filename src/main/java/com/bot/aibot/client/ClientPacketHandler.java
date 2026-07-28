package com.bot.aibot.client;

import net.minecraft.client.Minecraft;

import com.bot.aibot.network.packet.S2CMusicCommandPacket;

public final class ClientPacketHandler {

    public static void handle(final int action, final String data, long extra) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                public void run() {
                    if (action == S2CMusicCommandPacket.Action.PLAY_DIRECT.ordinal()
                        || action == S2CMusicCommandPacket.Action.PLAY_DIRECT_BROADCAST.ordinal()) {
                        boolean broadcast = action == S2CMusicCommandPacket.Action.PLAY_DIRECT_BROADCAST.ordinal();
                        if (broadcast) ClientMusicManager.onTrackFinishedCallback = null;
                        ClientMusicManager.play(data, broadcast ? "全服广播" : "正在播放...", extra);
                        MusicPlayerScreen.showPlaybackStarted(broadcast);
                    } else if (action == S2CMusicCommandPacket.Action.STOP.ordinal()) ClientMusicManager.stop();
                    else if (action == S2CMusicCommandPacket.Action.SEARCH_AND_PLAY.ordinal()
                        || action == S2CMusicCommandPacket.Action.OPEN_GUI.ordinal()
                        || action == S2CMusicCommandPacket.Action.PLAY_MY_LIKE.ordinal())
                        Minecraft.getMinecraft()
                            .displayGuiScreen(new MusicPlayerScreen());
                    else if (action == S2CMusicCommandPacket.Action.RESET_COOLDOWN.ordinal())
                        MusicPlayerScreen.resetBroadcastCooldown();
                    else if (action == S2CMusicCommandPacket.Action.PLAY_REJECTED.ordinal())
                        MusicPlayerScreen.showPlaybackRejected(data);
                }
            });
    }
}
