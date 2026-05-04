package com.bot.aibot.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SMusicActionPayload(int action)
        implements CustomPacketPayload {

    public static final Type<C2SMusicActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aibot", "music_action"));

    public static final StreamCodec<FriendlyByteBuf, C2SMusicActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeInt(payload.action()),
                    buf -> new C2SMusicActionPayload(buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
