package com.bot.aibot.network;

import com.bot.aibot.network.packet.C2SReportMusicPacket;
import com.bot.aibot.network.packet.S2CMusicCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PacketHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    public static void register() {}
    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        if (message instanceof S2CMusicCommandPacket musicPacket) {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                try {
                    Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
                    Object mc = minecraftClass.getMethod("getInstance").invoke(null);
                    if (mc != null) {
                        Class<?> actionClass = Class.forName("com.bot.aibot.network.packet.S2CMusicCommandPacket$Action");
                        Class<?> handlerClass = Class.forName("com.bot.aibot.client.ClientPacketHandler");
                        handlerClass.getMethod("handle", actionClass, String.class, long.class)
                                .invoke(null, musicPacket.getAction(), musicPacket.getData(), musicPacket.getExtra());
                    }
                } catch (Exception e) {
                    LOGGER.error(">>> [Packet] 客户端处理播放包失败: {}", e.getMessage());
                }
            }
            return;
        }
        LOGGER.warn(">>> [Packet] sendToPlayer 未处理的数据包类型: {}", message == null ? "null" : message.getClass().getSimpleName());
    }
    public static <MSG> void sendToServer(MSG message) {
        if (message instanceof C2SReportMusicPacket reportMusicPacket) {
            reportMusicPacket.handle(() -> null);
            return;
        }
        LOGGER.warn(">>> [Packet] sendToServer 未实现（迁移中）: {}", message == null ? "null" : message.getClass().getSimpleName());
    }
    public static <MSG> void sendToAll(MSG message) {}
}
