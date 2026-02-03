package com.bot.aibot.network;

import com.bot.aibot.network.packet.S2CMusicControlPacket;
import com.bot.aibot.network.packet.S2CPlayMusicPacket;
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
        INSTANCE.messageBuilder(S2CPlayMusicPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CPlayMusicPacket::new)
                .encoder(S2CPlayMusicPacket::encode)
                .consumerMainThread(S2CPlayMusicPacket::handle)
                .add();

        INSTANCE.messageBuilder(S2CMusicControlPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CMusicControlPacket::new)
                .encoder(S2CMusicControlPacket::encode)
                .consumerMainThread(S2CMusicControlPacket::handle)
                .add();
    }

    // --- 新增以下方法 ---

    /**
     * 发送给指定玩家 (服务端 -> 客户端)
     */
    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /**
     * 发送给服务器 (客户端 -> 服务端)
     * 虽然你现在没用到，但以后做 UI 交互时会用到
     */
    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }
}