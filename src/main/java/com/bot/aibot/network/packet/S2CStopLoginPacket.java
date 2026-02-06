package com.bot.aibot.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CStopLoginPacket {

    public S2CStopLoginPacket() {}
    public S2CStopLoginPacket(FriendlyByteBuf buf) {}
    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // --- 客户端执行 ---
            if (S2CRequestLoginPacket.isLoggingIn) {
                // 核心：强制解锁
                S2CRequestLoginPacket.isLoggingIn = false;
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§a[Bot] 已接收到停止指令，正在中断登录线程..."));
            } else {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§e[Bot] 当前没有正在进行的登录任务。"));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}