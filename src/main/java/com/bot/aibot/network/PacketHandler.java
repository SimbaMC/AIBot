package com.bot.aibot.network;

import com.bot.aibot.network.packet.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("aibot", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;

        INSTANCE.messageBuilder(C2SReportMusicPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SReportMusicPacket::new)
                .encoder(C2SReportMusicPacket::encode)
                .consumerMainThread(C2SReportMusicPacket::handle)
                .add();

        // 【新增】注册 C2SMusicActionPacket
        INSTANCE.messageBuilder(C2SMusicActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SMusicActionPacket::new)
                .encoder(C2SMusicActionPacket::encode)
                .consumerMainThread(C2SMusicActionPacket::handle)
                .add();

        // --- 【新增】注册新包 ---
        INSTANCE.messageBuilder(S2CMusicCommandPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CMusicCommandPacket::new)
                .encoder(S2CMusicCommandPacket::encode)
                .consumerMainThread(S2CMusicCommandPacket::handle)
                .add();

    }

    // --- 新增以下方法 ---

    /**
     * 发送给指定玩家 (服务端 -> 客户端)
     */
    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    // 【新增】广播给所有人
    public static <MSG> void sendToAll(MSG message) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }
}