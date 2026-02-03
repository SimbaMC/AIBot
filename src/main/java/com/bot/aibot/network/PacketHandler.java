package com.bot.aibot.network;

import com.bot.aibot.network.packet.S2CPlayMusicPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
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
    }
}