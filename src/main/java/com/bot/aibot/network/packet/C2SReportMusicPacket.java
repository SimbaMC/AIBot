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

    public C2SReportMusicPacket(String url, String songName) {
        this.url = url;
        this.songName = songName;
    }

    public C2SReportMusicPacket(FriendlyByteBuf buf) {
        this.url = buf.readUtf();
        this.songName = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.url);
        buf.writeUtf(this.songName);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // --- 这里的代码在【服务端】执行 ---
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                System.out.println(">>> [Server] 收到客户端上报的链接，准备广播...");

                // 1. 复用你原来的 S2CPlayMusicPacket 进行广播
                S2CPlayMusicPacket broadcastPacket = new S2CPlayMusicPacket(url, songName);
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), broadcastPacket);

                // 2. 发送全服提示消息
                sender.getServer().getPlayerList().broadcastSystemMessage(
                        Component.literal("§6[Bot全服广播] §f点歌人: §e" + sender.getName().getString() + " §f正在播放: §a" + songName),
                        false
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}