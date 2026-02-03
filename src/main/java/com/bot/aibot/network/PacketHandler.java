package com.bot.aibot.network;

import com.bot.aibot.network.packet.S2CPlayMusicPacket;
import net.minecraft.resources.ResourceLocation;
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
        INSTANCE.registerMessage(id++, S2CPlayMusicPacket.class,
                S2CPlayMusicPacket::encode,
                S2CPlayMusicPacket::decode,
                S2CPlayMusicPacket::handle);
    }
}