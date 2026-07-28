package com.bot.aibot.network.packet;

import java.nio.charset.StandardCharsets;

import com.bot.aibot.BottyMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class S2CMusicCommandPacket implements IMessage {

    private static final int MAX_DATA_BYTES = 2048;

    public enum Action {
        PLAY_DIRECT,
        STOP,
        SEARCH_AND_PLAY,
        OPEN_GUI,
        PLAY_MY_LIKE,
        RESET_COOLDOWN,
        PLAY_DIRECT_BROADCAST,
        PLAY_REJECTED
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
        validate();
    }

    public void toBytes(ByteBuf buf) {
        validate();
        byte[] b = data.getBytes(StandardCharsets.UTF_8);
        if (b.length > MAX_DATA_BYTES)
            throw new IllegalArgumentException("Music payload exceeds " + MAX_DATA_BYTES + " bytes");
        buf.writeInt(action.ordinal());
        buf.writeInt(b.length);
        buf.writeBytes(b);
        buf.writeLong(extra);
    }

    private void validate() {
        if (action == null || data == null) throw new IllegalArgumentException("Invalid music command");
        int size = data.getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_DATA_BYTES) throw new IllegalArgumentException("Music command data is too long");
        boolean direct = action == Action.PLAY_DIRECT || action == Action.PLAY_DIRECT_BROADCAST;
        if (direct && (size == 0 || extra <= 0 || extra > 86400000L))
            throw new IllegalArgumentException("Invalid direct-play command");
        if (action == Action.SEARCH_AND_PLAY && (size == 0 || size > 256 || extra != 0 && extra != 1))
            throw new IllegalArgumentException("Invalid search command");
        if (action == Action.PLAY_MY_LIKE && (size != 0 || extra != 0))
            throw new IllegalArgumentException("Invalid likes command");
        if (action == Action.PLAY_REJECTED && (size == 0 || size > 256 || extra != 0))
            throw new IllegalArgumentException("Invalid rejection message");
        if ((action == Action.STOP || action == Action.OPEN_GUI || action == Action.RESET_COOLDOWN)
            && (size != 0 || extra != 0)) throw new IllegalArgumentException("Unexpected music command data");
        if (!direct && action != Action.SEARCH_AND_PLAY && extra != 0)
            throw new IllegalArgumentException("Unexpected music command data");
    }

    public static class Handler implements IMessageHandler<S2CMusicCommandPacket, IMessage> {

        public IMessage onMessage(final S2CMusicCommandPacket msg, MessageContext ctx) {
            BottyMod.proxy.handleMusicPacket(msg.action.ordinal(), msg.data, msg.extra);
            return null;
        }
    }
}
