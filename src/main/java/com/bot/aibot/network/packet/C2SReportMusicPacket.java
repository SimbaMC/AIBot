package com.bot.aibot.network.packet;

import com.bot.aibot.network.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

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
        this.url = buf.readUtf();
        this.songName = buf.readUtf();
        this.duration = buf.readLong();
        this.isGlobal = buf.readBoolean(); // 【新增】
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.url);
        buf.writeUtf(this.songName);
        buf.writeLong(this.duration);
        buf.writeBoolean(this.isGlobal); // 【新增】
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                System.out.println(">>> [Server] 收到歌曲上报: " + songName + " (全服: " + isGlobal + ")");

                // 构造播放指令
                S2CMusicCommandPacket playPacket = new S2CMusicCommandPacket(
                        S2CMusicCommandPacket.Action.PLAY_Direct,
                        url,
                        duration
                );

                if (isGlobal) {
                    // --- 情况 A: 全服广播 ---
                    PacketHandler.sendToAll(playPacket);

                    // 广播消息
                    String msg = "正在全服播放: §a" + songName;
                    sender.getServer().getPlayerList().broadcastSystemMessage(Component.literal(msg), false);

                } else {
                    // --- 情况 B: 私享播放 ---
                    PacketHandler.sendToPlayer(playPacket, sender);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}