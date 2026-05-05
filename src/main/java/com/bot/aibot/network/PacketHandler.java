package com.bot.aibot.network;

import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PacketHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    public static void register() {}
    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {}
    public static <MSG> void sendToServer(MSG message) {
        LOGGER.warn(">>> [Packet] sendToServer 未实现（迁移中）: {}", message == null ? "null" : message.getClass().getSimpleName());
    }
    public static <MSG> void sendToAll(MSG message) {}
}
