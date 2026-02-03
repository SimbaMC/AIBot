package com.bot.aibot.network.packet;

import com.bot.aibot.client.ClientMusicManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CPlayMusicPacket {
    private final String songUrl;
    private final String songName;

    public S2CPlayMusicPacket(String songUrl, String songName) {
        this.songUrl = songUrl;
        this.songName = songName;
    }

    public static void encode(S2CPlayMusicPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.songUrl);
        buf.writeUtf(msg.songName);
    }

    public static S2CPlayMusicPacket decode(FriendlyByteBuf buf) {
        return new S2CPlayMusicPacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(S2CPlayMusicPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 这一步是在客户端执行的
            // 调用客户端音乐管理器播放
            ClientMusicManager.play(msg.songUrl, msg.songName);
        });
        ctx.get().setPacketHandled(true);
    }
}