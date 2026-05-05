package com.bot.aibot.network.packet;

import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CMusicCommandPacket.Action;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.bot.aibot.BottyMod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

public class C2SReportMusicPacket {
    private static final Logger LOGGER = LogManager.getLogger();
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

    public void handle(Supplier<?> ctx) {
        if (BottyMod.serverInstance == null) {
            LOGGER.warn(">>> [Packet] 未检测到可用的 Server 实例，广播包无法在当前进程处理。");
            return;
        }
        BottyMod.serverInstance.execute(() -> {
            if (isGlobal) {
                for (ServerPlayer player : BottyMod.serverInstance.getPlayerList().getPlayers()) {
                    PacketHandler.sendToPlayer(new S2CMusicCommandPacket(Action.PLAY_Direct, url, duration), player);
                }
                BottyMod.serverInstance.getPlayerList().broadcastSystemMessage(
                        Component.literal("§a[Bot] 正在全服播放: " + songName), false
                );
            } else {
                ServerPlayer first = BottyMod.serverInstance.getPlayerList().getPlayers().stream().findFirst().orElse(null);
                if (first != null) {
                    PacketHandler.sendToPlayer(new S2CMusicCommandPacket(Action.PLAY_Direct, url, duration), first);
                }
            }
        });
    }
}
