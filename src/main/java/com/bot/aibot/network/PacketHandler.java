package com.bot.aibot.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.bot.aibot.network.packet.C2SMusicActionPacket;
import com.bot.aibot.network.packet.C2SReportMusicPacket;
import com.bot.aibot.network.packet.S2CMusicCommandPacket;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class PacketHandler {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("aibot");

    private PacketHandler() {}

    public static void register() {
        INSTANCE.registerMessage(C2SReportMusicPacket.Handler.class, C2SReportMusicPacket.class, 0, Side.SERVER);
        INSTANCE.registerMessage(C2SMusicActionPacket.Handler.class, C2SMusicActionPacket.class, 1, Side.SERVER);
        INSTANCE.registerMessage(S2CMusicCommandPacket.Handler.class, S2CMusicCommandPacket.class, 2, Side.CLIENT);
    }

    public static void sendToPlayer(S2CMusicCommandPacket message, EntityPlayerMP player) {
        INSTANCE.sendTo(message, player);
    }

    public static void sendToAll(S2CMusicCommandPacket message) {
        INSTANCE.sendToAll(message);
    }

    public static void sendToServer(Object message) {
        INSTANCE.sendToServer((cpw.mods.fml.common.network.simpleimpl.IMessage) message);
    }
}
