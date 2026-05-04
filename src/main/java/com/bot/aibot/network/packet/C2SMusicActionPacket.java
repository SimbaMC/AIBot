package com.bot.aibot.network.packet;

import com.bot.aibot.network.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SMusicActionPacket {
    private final int action; // 0: Stop

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
            // 【修复】服务端收到控制指令，广播给全服
            // 目前无论 action 是 0 还是 1，我们都统一处理为 STOP
            // 如果后续你想支持 PAUSE，可以在 S2CMusicCommandPacket 枚举里加一个 PAUSE

            S2CMusicCommandPacket stopPacket = new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.STOP);
            PacketHandler.sendToAll(stopPacket);
        });
        ctx.get().setPacketHandled(true);
    }
}