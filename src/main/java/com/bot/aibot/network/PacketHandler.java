package com.bot.aibot.network;

import com.bot.aibot.network.packet.C2SReportMusicPacket;
import com.bot.aibot.network.packet.S2CMusicCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    private PacketHandler() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(C2SReportMusicPacket.TYPE, C2SReportMusicPacket.STREAM_CODEC,
                C2SReportMusicPacket::handle);
        registrar.playToClient(S2CMusicCommandPacket.TYPE, S2CMusicCommandPacket.STREAM_CODEC,
                S2CMusicCommandPacket::handle);
    }

    public static void sendToPlayer(S2CMusicCommandPacket message, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, message);
    }

    public static void sendToServer(C2SReportMusicPacket message) {
        PacketDistributor.sendToServer(message);
    }

    public static void sendToAll(S2CMusicCommandPacket message) {
        PacketDistributor.sendToAllPlayers(message);
    }
}
