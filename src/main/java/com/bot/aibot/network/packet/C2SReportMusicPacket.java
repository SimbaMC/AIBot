package com.bot.aibot.network.packet;

import com.bot.aibot.network.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Supplier;

public class C2SReportMusicPacket {
    private final String url;
    private final String songName;
    private final long duration;
    private final boolean isGlobal; // 【新增】标记是否为全服播放

    public C2SReportMusicPacket(String url, String songName, long duration, boolean isGlobal) {
        this.url = url;
        this.songName = songName;
        this.duration = duration;
        this.isGlobal = isGlobal;
    }

    public C2SReportMusicPacket(FriendlyByteBuf buf) {
        this.url = buf.readUtf();
        this.songName = buf.readUtf();
        this.duration = buf.readLong();
        this.isGlobal = buf.readBoolean(); // 【新增】
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.url);
        buf.writeUtf(this.songName);
        buf.writeLong(this.duration);
        buf.writeBoolean(this.isGlobal); // 【新增】
    }

    public void handle(Supplier<?> ctx) {
        // NeoForge payload migration pending
    }
}