package com.bot.aibot.network.packet;

import com.bot.aibot.network.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;

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

    public void handle(Supplier<?> ctx) {
        // NeoForge payload migration pending
    }
}