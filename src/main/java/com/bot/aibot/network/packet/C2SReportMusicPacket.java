package com.bot.aibot.network.packet;

import com.bot.aibot.security.MusicReportService;
import com.bot.aibot.network.PacketStrings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;

public record C2SReportMusicPacket(String url, String songName, long duration, boolean global)
        implements CustomPacketPayload {
    public static final Type<C2SReportMusicPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("aibot", "report_music"));
    public static final StreamCodec<FriendlyByteBuf, C2SReportMusicPacket> STREAM_CODEC =
            StreamCodec.of(C2SReportMusicPacket::encode, C2SReportMusicPacket::decode);

    public C2SReportMusicPacket {
        validate(url, songName, duration);
    }

    private static C2SReportMusicPacket decode(FriendlyByteBuf buffer) {
        return new C2SReportMusicPacket(PacketStrings.readUtf8(buffer, 2048, 2048),
                PacketStrings.readUtf8(buffer, 256, 256),
                buffer.readLong(), buffer.readBoolean());
    }

    private static void encode(FriendlyByteBuf buffer, C2SReportMusicPacket packet) {
        validate(packet.url, packet.songName, packet.duration);
        PacketStrings.writeUtf8(buffer, packet.url, 2048, 2048);
        PacketStrings.writeUtf8(buffer, packet.songName, 256, 256);
        buffer.writeLong(packet.duration);
        buffer.writeBoolean(packet.global);
    }

    public static void handle(C2SReportMusicPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer sender) {
            context.enqueueWork(() -> MusicReportService.submit(sender, packet.url, packet.songName,
                    packet.duration, packet.global));
        }
    }

    private static void validate(String url, String songName, long duration) {
        if (url == null || url.isEmpty() || url.getBytes(StandardCharsets.UTF_8).length > 2048
                || songName == null || songName.isBlank()
                || songName.getBytes(StandardCharsets.UTF_8).length > 256
                || duration <= 0 || duration > 86_400_000L) {
            throw new IllegalArgumentException("Invalid music report");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
