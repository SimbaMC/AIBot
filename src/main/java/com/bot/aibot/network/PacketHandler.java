package com.bot.aibot.network;

import net.minecraft.server.level.ServerPlayer;

public class PacketHandler {
    public static void register() {}
    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {}
    public static <MSG> void sendToServer(MSG message) {}
    public static <MSG> void sendToAll(MSG message) {}
}
