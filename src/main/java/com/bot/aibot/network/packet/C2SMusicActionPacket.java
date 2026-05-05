package com.bot.aibot.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class C2SMusicActionPacket implements CustomPacketPayload {
    public static final Type<C2SMusicActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aibot", "music_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SMusicActionPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SMusicActionPacket::encode, C2SMusicActionPacket::decode);

    private final int action;

    public C2SMusicActionPacket(int action) {
        this.action = action;
    }

    private static C2SMusicActionPacket decode(RegistryFriendlyByteBuf buf) {
        return new C2SMusicActionPacket(buf.readInt());
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.action);
    }

    public static void handle(C2SMusicActionPacket packet, IPayloadContext context) {
        // Reserved for future server-side music controls.
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
