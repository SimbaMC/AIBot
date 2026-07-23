package com.bot.aibot.network;

import net.minecraft.network.FriendlyByteBuf;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class PacketStrings {
    private PacketStrings() {}

    public static String readUtf8(FriendlyByteBuf buf, int maxBytes, int maxChars) {
        int length = buf.readVarInt();
        if (length < 0 || length > maxBytes || length > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid UTF-8 field length");
        }
        byte[] encoded = new byte[length];
        buf.readBytes(encoded);
        final String value;
        try {
            value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Malformed UTF-8 field", e);
        }
        if (value.length() > maxChars) throw new IllegalArgumentException("UTF-8 field is too long");
        return value;
    }

    public static void writeUtf8(FriendlyByteBuf buf, String value, int maxBytes, int maxChars) {
        if (value.length() > maxChars) throw new IllegalArgumentException("UTF-8 field is too long");
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maxBytes) throw new IllegalArgumentException("UTF-8 field is too long");
        buf.writeVarInt(encoded.length);
        buf.writeBytes(encoded);
    }
}
