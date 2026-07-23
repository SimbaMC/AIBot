package com.bot.aibot.network.packet;

import java.nio.charset.StandardCharsets;

import com.bot.aibot.security.MusicReportService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class C2SReportMusicPacket implements IMessage {

    private String url = "", songName = "";
    private long duration;
    private boolean global;

    public C2SReportMusicPacket() {}

    public C2SReportMusicPacket(String url, String songName, long duration, boolean global) {
        this.url = url;
        this.songName = songName;
        this.duration = duration;
        this.global = global;
        validate();
    }

    public void fromBytes(ByteBuf b) {
        url = read(b, 2048);
        songName = read(b, 256);
        duration = b.readLong();
        global = b.readBoolean();
        validate();
    }

    public void toBytes(ByteBuf b) {
        validate();
        write(b, url, 2048);
        write(b, songName, 256);
        b.writeLong(duration);
        b.writeBoolean(global);
    }

    private void validate() {
        if (url.length() == 0 || songName.trim()
            .length() == 0 || duration <= 0 || duration > 86400000L)
            throw new IllegalArgumentException("Invalid music report");
    }

    private static String read(ByteBuf b, int max) {
        if (b.readableBytes() < 4) throw new IllegalArgumentException("Truncated string");
        int n = b.readInt();
        if (n < 0 || n > max || n > b.readableBytes()) throw new IllegalArgumentException("Invalid string length");
        byte[] v = new byte[n];
        b.readBytes(v);
        return new String(v, StandardCharsets.UTF_8);
    }

    private static void write(ByteBuf b, String s, int max) {
        byte[] v = s.getBytes(StandardCharsets.UTF_8);
        if (v.length > max) throw new IllegalArgumentException("String too long");
        b.writeInt(v.length);
        b.writeBytes(v);
    }

    public static final class Handler implements IMessageHandler<C2SReportMusicPacket, IMessage> {

        public IMessage onMessage(C2SReportMusicPacket m, MessageContext c) {
            MusicReportService.submit(c.getServerHandler().playerEntity, m.url, m.songName, m.duration, m.global);
            return null;
        }
    }
}
