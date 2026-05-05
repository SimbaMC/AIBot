package com.bot.aibot.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 -> 客户端
 * 统一音乐指令包：包含播放、搜索、控制、GUI 操作等指令。
 */
public class S2CMusicCommandPacket implements CustomPacketPayload {
    public static final Type<S2CMusicCommandPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aibot", "music_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CMusicCommandPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CMusicCommandPacket::encode, S2CMusicCommandPacket::decode);

    public enum Action {
        PLAY_Direct,
        STOP,
        SEARCH_AND_PLAY,
        OPEN_GUI,
        PLAY_MY_LIKE,
        RESET_COOLDOWN
    }

    private final Action action;
    private final String data;
    private final long extra;

    public S2CMusicCommandPacket(Action action) {
        this(action, "", 0);
    }

    public S2CMusicCommandPacket(Action action, String data) {
        this(action, data, 0);
    }

    public S2CMusicCommandPacket(Action action, String data, long extra) {
        this.action = action;
        this.data = data == null ? "" : data;
        this.extra = extra;
    }

    private static S2CMusicCommandPacket decode(RegistryFriendlyByteBuf buf) {
        return new S2CMusicCommandPacket(buf.readEnum(Action.class), buf.readUtf(), buf.readLong());
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(this.action);
        buf.writeUtf(this.data);
        buf.writeLong(this.extra);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public Action getAction() {
        return action;
    }

    public String getData() {
        return data;
    }

    public long getExtra() {
        return extra;
    }
}
