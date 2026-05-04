package com.bot.aibot.network;

import com.bot.aibot.client.ClientPacketHandler;
import com.bot.aibot.network.payload.C2SMusicActionPayload;
import com.bot.aibot.network.payload.C2SReportMusicPayload;
import com.bot.aibot.network.payload.S2CMusicCommandPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class PacketHandler {

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                C2SReportMusicPayload.TYPE,
                C2SReportMusicPayload.STREAM_CODEC,
                PacketHandler::handleReportMusic
        );

        registrar.playToServer(
                C2SMusicActionPayload.TYPE,
                C2SMusicActionPayload.STREAM_CODEC,
                PacketHandler::handleMusicAction
        );

        registrar.playToClient(
                S2CMusicCommandPayload.TYPE,
                S2CMusicCommandPayload.STREAM_CODEC,
                PacketHandler::handleMusicCommand
        );
    }

    private static void handleReportMusic(C2SReportMusicPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;

            S2CMusicCommandPayload playPacket = new S2CMusicCommandPayload(
                    S2CMusicCommandPayload.Action.PLAY_Direct,
                    payload.url(),
                    payload.duration()
            );

            if (payload.isGlobal()) {
                sender.getServer().getPlayerList().getPlayers()
                        .forEach(p -> PacketDistributor.sendToPlayer(p, playPacket));
                String msg = "正在全服播放: §a" + payload.songName();
                sender.getServer().getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
            } else {
                PacketDistributor.sendToPlayer(sender, playPacket);
            }
        });
    }

    private static void handleMusicAction(C2SMusicActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;
            S2CMusicCommandPayload stopPacket = new S2CMusicCommandPayload(S2CMusicCommandPayload.Action.STOP);
            sender.getServer().getPlayerList().getPlayers()
                    .forEach(p -> PacketDistributor.sendToPlayer(p, stopPacket));
        });
    }

    private static void handleMusicCommand(S2CMusicCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handle(payload.action(), payload.data(), payload.extra()));
    }

    public static void sendToPlayer(S2CMusicCommandPayload payload, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToServer(C2SReportMusicPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToServer(C2SMusicActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}