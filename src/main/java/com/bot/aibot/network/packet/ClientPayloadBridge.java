package com.bot.aibot.network.packet;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

final class ClientPayloadBridge {
    private ClientPayloadBridge() {}

    static void handle(S2CMusicCommandPacket packet) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        try {
            Class<?> handler = Class.forName("com.bot.aibot.client.ClientPacketHandler");
            handler.getMethod("handle", S2CMusicCommandPacket.Action.class, String.class, long.class)
                    .invoke(null, packet.action(), packet.data(), packet.extra());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to dispatch client music payload", e);
        }
    }
}
