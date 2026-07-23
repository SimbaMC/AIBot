package com.bot.aibot.network.packet;

import com.bot.aibot.security.MusicReportService;
import com.bot.aibot.network.PacketStrings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

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
        this.url = PacketStrings.readUtf8(buf, 2048, 2048);
        this.songName = PacketStrings.readUtf8(buf, 1024, 256);
        this.duration = buf.readLong();
        this.isGlobal = buf.readBoolean(); // 【新增】
        validateFields();
    }

    public void encode(FriendlyByteBuf buf) {
        validateFields();
        PacketStrings.writeUtf8(buf, this.url, 2048, 2048);
        PacketStrings.writeUtf8(buf, this.songName, 1024, 256);
        buf.writeLong(this.duration);
        buf.writeBoolean(this.isGlobal); // 【新增】
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) context.enqueueWork(() -> MusicReportService.submit(sender, url, songName, duration, isGlobal));
        context.setPacketHandled(true);
    }

    private void validateFields() {
        if (url.isEmpty() || url.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2048
                || songName.isBlank() || songName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256
                || duration <= 0 || duration > 86_400_000L) {
            throw new IllegalArgumentException("Invalid music report");
        }
    }
}