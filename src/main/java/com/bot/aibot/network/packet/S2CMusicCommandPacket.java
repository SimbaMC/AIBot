package com.bot.aibot.network.packet;

import java.nio.charset.StandardCharsets;

import com.bot.aibot.client.ClientPacketHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class S2CMusicCommandPacket implements IMessage {

    private static final int MAX_DATA_BYTES = 8192;

    public enum Action {
        PLAY_Direct,
        STOP,
        SEARCH_AND_PLAY,
        OPEN_GUI,
        PLAY_MY_LIKE,
        RESET_COOLDOWN
    }

    public Action action;
    public String data = "";
    public long extra;

    public S2CMusicCommandPacket() {}

    public S2CMusicCommandPacket(Action action) {
        this(action, "", 0);
    }

    public S2CMusicCommandPacket(Action action, String data, long extra) {
        this.action = action;
        this.data = data;
        this.extra = extra;
    }

    public void fromBytes(ByteBuf buf) {
        int actionId = buf.readInt();
        if (actionId < 0 || actionId >= Action.values().length)
            throw new IllegalArgumentException("Unknown music action: " + actionId);
        action = Action.values()[actionId];
        int n = buf.readInt();
        if (n < 0 || n > MAX_DATA_BYTES || n > buf.readableBytes() - Long.BYTES)
            throw new IllegalArgumentException("Invalid music payload length: " + n);
        byte[] b = new byte[n];
        buf.readBytes(b);
        data = new String(b, StandardCharsets.UTF_8);
        extra = buf.readLong();
    }

    public void toBytes(ByteBuf buf) {
        byte[] b = data.getBytes(StandardCharsets.UTF_8);
        if (b.length > MAX_DATA_BYTES)
            throw new IllegalArgumentException("Music payload exceeds " + MAX_DATA_BYTES + " bytes");
        buf.writeInt(action.ordinal());
        buf.writeInt(b.length);
        buf.writeBytes(b);
        buf.writeLong(extra);
    }

    public static class Handler implements IMessageHandler<S2CMusicCommandPacket, IMessage> {

        public IMessage onMessage(final S2CMusicCommandPacket msg, MessageContext ctx) {
            ClientPacketHandler.handle(msg.action.ordinal(), msg.data, msg.extra);
            return null;
        }
    }
}
