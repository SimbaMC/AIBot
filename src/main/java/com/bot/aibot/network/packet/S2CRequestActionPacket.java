package com.bot.aibot.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CRequestActionPacket {

    private final String action;

    public S2CRequestActionPacket(String action) {
        this.action = action;
    }

    public S2CRequestActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.action);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 使用 DistExecutor 安全地调用客户端代码
            // 只有在客户端时，才会去加载 ClientPacketHandler 类
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.bot.aibot.client.ClientPacketHandler.handle(action)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}