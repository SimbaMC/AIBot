package com.bot.aibot.network.packet;

import com.bot.aibot.network.PacketHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SMusicActionPacket implements IMessage {

    public int action;

    public C2SMusicActionPacket() {}

    public C2SMusicActionPacket(int action) {
        this.action = action;
    }

    public void fromBytes(ByteBuf b) {
        action = b.readInt();
    }

    public void toBytes(ByteBuf b) {
        b.writeInt(action);
    }

    public static class Handler implements IMessageHandler<C2SMusicActionPacket, IMessage> {

        public IMessage onMessage(C2SMusicActionPacket m, MessageContext c) {
            PacketHandler.sendToAll(new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.STOP));
            return null;
        }
    }
}
