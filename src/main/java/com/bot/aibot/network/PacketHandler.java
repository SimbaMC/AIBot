package com.bot.aibot.network;

import com.bot.aibot.network.packet.C2SMusicActionPacket;
import com.bot.aibot.network.packet.C2SReportMusicPacket;
import com.bot.aibot.network.packet.S2CMusicCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PacketHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PROTOCOL_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(S2CMusicCommandPacket.TYPE, S2CMusicCommandPacket.STREAM_CODEC, PacketHandler::handleMusicCommand);
        registrar.playToServer(C2SReportMusicPacket.TYPE, C2SReportMusicPacket.STREAM_CODEC, C2SReportMusicPacket::handle);
        registrar.playToServer(C2SMusicActionPacket.TYPE, C2SMusicActionPacket.STREAM_CODEC, C2SMusicActionPacket::handle);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        if (message instanceof S2CMusicCommandPacket musicPacket) {
            PacketDistributor.sendToPlayer(player, musicPacket);
            return;
        }
        LOGGER.warn(">>> [Packet] sendToPlayer 未处理的数据包类型: {}", message == null ? "null" : message.getClass().getSimpleName());
    }

    public static <MSG> void sendToServer(MSG message) {
        if (message instanceof C2SReportMusicPacket reportMusicPacket) {
            PacketDistributor.sendToServer(reportMusicPacket);
            return;
        }
        if (message instanceof C2SMusicActionPacket musicActionPacket) {
            PacketDistributor.sendToServer(musicActionPacket);
            return;
        }
        LOGGER.warn(">>> [Packet] sendToServer 未处理的数据包类型: {}", message == null ? "null" : message.getClass().getSimpleName());
    }

    public static <MSG> void sendToAll(MSG message) {
        if (message instanceof S2CMusicCommandPacket musicPacket) {
            PacketDistributor.sendToAllPlayers(musicPacket);
            return;
        }
        LOGGER.warn(">>> [Packet] sendToAll 未处理的数据包类型: {}", message == null ? "null" : message.getClass().getSimpleName());
    }

    private static void handleMusicCommand(S2CMusicCommandPacket packet, IPayloadContext context) {
        try {
            Class<?> actionClass = Class.forName("com.bot.aibot.network.packet.S2CMusicCommandPacket$Action");
            Class<?> handlerClass = Class.forName("com.bot.aibot.client.ClientPacketHandler");
            handlerClass.getMethod("handle", actionClass, String.class, long.class)
                    .invoke(null, packet.getAction(), packet.getData(), packet.getExtra());
        } catch (Exception e) {
            LOGGER.error(">>> [Packet] 客户端处理播放包失败: {}", e.getMessage());
        }
    }
}
