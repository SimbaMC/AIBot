package com.bot.aibot.network;

import net.minecraft.server.level.ServerPlayer;

/**
 * NeoForge 1.21.1 临时兼容层：
 * 先保证编译通过，后续再切换到 CustomPayload 注册实现。
 */
public class PacketHandler {

    public static void register() {
        // TODO: migrate to RegisterPayloadHandlersEvent + CustomPacketPayload.
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        // TODO: implement via NeoForge payload distributor
    }

    public static <MSG> void sendToServer(MSG message) {
        // TODO: implement via NeoForge payload distributor
    }

    public static <MSG> void sendToAll(MSG message) {
        // TODO: implement via NeoForge payload distributor
    }
}
