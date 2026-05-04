package com.bot.aibot.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SReportMusicPayload(String url, String songName, long duration, boolean isGlobal)
        implements CustomPacketPayload {

    public static final Type<C2SReportMusicPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aibot", "report_music"));

    public static final StreamCodec<FriendlyByteBuf, C2SReportMusicPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.url());
                        buf.writeUtf(payload.songName());
                        buf.writeLong(payload.duration());
                        buf.writeBoolean(payload.isGlobal());
                    },
                    buf -> new C2SReportMusicPayload(
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readLong(),
                            buf.readBoolean()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
