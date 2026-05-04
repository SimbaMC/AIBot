package com.bot.aibot.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 -> 客户端
 * 统一音乐指令包：包含播放、搜索、控制、GUI操作等所有指令
 */
public record S2CMusicCommandPayload(Action action, String data, long extra)
        implements CustomPacketPayload {

    public enum Action {
        PLAY_Direct,
        STOP,
        SEARCH_AND_PLAY,
        OPEN_GUI,
        PLAY_MY_LIKE,
        RESET_COOLDOWN
    }

    public S2CMusicCommandPayload(Action action) {
        this(action, "", 0);
    }

    public S2CMusicCommandPayload(Action action, String data) {
        this(action, data, 0);
    }

    public static final Type<S2CMusicCommandPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aibot", "music_command"));

    public static final StreamCodec<FriendlyByteBuf, S2CMusicCommandPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeEnum(payload.action());
                        buf.writeUtf(payload.data());
                        buf.writeLong(payload.extra());
                    },
                    buf -> new S2CMusicCommandPayload(
                            buf.readEnum(Action.class),
                            buf.readUtf(),
                            buf.readLong()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
