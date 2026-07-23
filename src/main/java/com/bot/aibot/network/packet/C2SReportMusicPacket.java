package com.bot.aibot.network.packet;

import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayerMP;

import com.bot.aibot.network.PacketHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SReportMusicPacket implements IMessage {

    public String url = "", songName = "";
    public long duration;
    public boolean global;

    public C2SReportMusicPacket() {}

    public C2SReportMusicPacket(String u, String n, long d, boolean g) {
        url = u;
        songName = n;
        duration = d;
        global = g;
    }

    private static String read(ByteBuf b) {
        int n = b.readInt();
        byte[] x = new byte[n];
        b.readBytes(x);
        return new String(x, StandardCharsets.UTF_8);
    }

    private static void write(ByteBuf b, String s) {
        byte[] x = s.getBytes(StandardCharsets.UTF_8);
        b.writeInt(x.length);
        b.writeBytes(x);
    }

    public void fromBytes(ByteBuf b) {
        url = read(b);
        songName = read(b);
        duration = b.readLong();
        global = b.readBoolean();
    }

    public void toBytes(ByteBuf b) {
        write(b, url);
        write(b, songName);
        b.writeLong(duration);
        b.writeBoolean(global);
    }

    public static class Handler implements IMessageHandler<C2SReportMusicPacket, IMessage> {

        public IMessage onMessage(C2SReportMusicPacket m, MessageContext c) {
            EntityPlayerMP p = c.getServerHandler().playerEntity;
            S2CMusicCommandPacket x = new S2CMusicCommandPacket(
                S2CMusicCommandPacket.Action.PLAY_Direct,
                m.url,
                m.duration);
            if (m.global) {
                PacketHandler.sendToAll(x);
                p.mcServer.getConfigurationManager()
                    .sendChatMsg(new net.minecraft.util.ChatComponentText("§b♪ §f正在播放: §a" + m.songName + " §b♪"));
            } else PacketHandler.sendToPlayer(x, p);
            return null;
        }
    }
}
