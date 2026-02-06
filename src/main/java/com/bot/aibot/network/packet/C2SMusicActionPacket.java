package com.bot.aibot.network.packet;

import com.bot.aibot.network.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SMusicActionPacket {
    private final int action; // 0: Stop, 1: TogglePause

    public C2SMusicActionPacket(int action) {
        this.action = action;
    }

    public C2SMusicActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.action);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 服务端收到控制指令，广播给全服
            PacketHandler.sendToAll(new S2CMusicControlPacket(action));
        });
        ctx.get().setPacketHandled(true);
    }
}