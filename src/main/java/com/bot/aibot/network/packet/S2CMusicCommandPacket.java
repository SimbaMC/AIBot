package com.bot.aibot.network.packet;

import com.bot.aibot.network.PacketStrings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;

public record S2CMusicCommandPacket(Action action, String data, long extra) implements CustomPacketPayload {
    public enum Action { PLAY_DIRECT, STOP, SEARCH_AND_PLAY, OPEN_GUI, PLAY_MY_LIKE, RESET_COOLDOWN }

    public static final Type<S2CMusicCommandPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("aibot", "music_command"));
    public static final StreamCodec<FriendlyByteBuf, S2CMusicCommandPacket> STREAM_CODEC =
            StreamCodec.of(S2CMusicCommandPacket::encode, S2CMusicCommandPacket::decode);

    public S2CMusicCommandPacket(Action action) { this(action, "", 0); }
    public S2CMusicCommandPacket(Action action, String data) { this(action, data, 0); }
    public S2CMusicCommandPacket { validate(action, data, extra); }

    private static S2CMusicCommandPacket decode(FriendlyByteBuf buffer) {
        int actionId = buffer.readVarInt();
        if (actionId < 0 || actionId >= Action.values().length) throw new IllegalArgumentException("Invalid music action");
        return new S2CMusicCommandPacket(Action.values()[actionId],
                PacketStrings.readUtf8(buffer, 2048, 2048), buffer.readLong());
    }

    private static void encode(FriendlyByteBuf buffer, S2CMusicCommandPacket packet) {
        validate(packet.action, packet.data, packet.extra);
        buffer.writeVarInt(packet.action.ordinal());
        PacketStrings.writeUtf8(buffer, packet.data, 2048, 2048);
        buffer.writeLong(packet.extra);
    }

    public static void handle(S2CMusicCommandPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadBridge.handle(packet));
    }

    private static void validate(Action action, String data, long extra) {
        if (action == null || data == null || data.getBytes(StandardCharsets.UTF_8).length > 2048)
            throw new IllegalArgumentException("Invalid music command");
        if (action == Action.PLAY_DIRECT && (data.isEmpty() || extra <= 0 || extra > 86_400_000L))
            throw new IllegalArgumentException("Invalid direct-play command");
        if ((action == Action.SEARCH_AND_PLAY || action == Action.PLAY_MY_LIKE)
                && data.getBytes(StandardCharsets.UTF_8).length > 256)
            throw new IllegalArgumentException("Music command data too long");
        if (action == Action.SEARCH_AND_PLAY && extra != 0 && extra != 1)
            throw new IllegalArgumentException("Invalid search mode");
        if (action != Action.PLAY_DIRECT && action != Action.SEARCH_AND_PLAY && extra != 0)
            throw new IllegalArgumentException("Unexpected music command data");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
