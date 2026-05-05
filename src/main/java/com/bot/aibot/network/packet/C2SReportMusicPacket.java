package com.bot.aibot.network.packet;

import com.bot.aibot.BottyMod;
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CMusicCommandPacket.Action;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class C2SReportMusicPacket implements CustomPacketPayload {
    public static final Type<C2SReportMusicPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aibot", "report_music"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SReportMusicPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SReportMusicPacket::encode, C2SReportMusicPacket::decode);

    private static final Logger LOGGER = LogManager.getLogger();
    private static long lastGlobalBroadcastTime = 0L;

    private final String url;
    private final String songName;
    private final long duration;
    private final boolean isGlobal;

    public C2SReportMusicPacket(String url, String songName, long duration, boolean isGlobal) {
        this.url = url == null ? "" : url;
        this.songName = songName == null ? "" : songName;
        this.duration = duration;
        this.isGlobal = isGlobal;
    }

    private static C2SReportMusicPacket decode(RegistryFriendlyByteBuf buf) {
        return new C2SReportMusicPacket(buf.readUtf(), buf.readUtf(), buf.readLong(), buf.readBoolean());
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.url);
        buf.writeUtf(this.songName);
        buf.writeLong(this.duration);
        buf.writeBoolean(this.isGlobal);
    }

    public static void handle(C2SReportMusicPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender)) {
            LOGGER.warn(">>> [Packet] 收到音乐汇报包，但上下文里没有服务端玩家");
            return;
        }
        if (BottyMod.serverInstance == null) {
            LOGGER.warn(">>> [Packet] 未检测到可用的 Server 实例，广播包无法处理。");
            return;
        }

        if (packet.isGlobal) {
            long now = System.currentTimeMillis();
            int cooldownSec = BotConfig.SERVER.broadcastCooldown.get();
            long cooldownMs = cooldownSec * 1000L;
            long elapsed = now - lastGlobalBroadcastTime;
            if (elapsed < cooldownMs) {
                long remain = Math.max(1L, (cooldownMs - elapsed + 999L) / 1000L);
                sender.sendSystemMessage(Component.literal("§c[Bot] 全服广播冷却中: " + remain + "s"));
                return;
            }
            lastGlobalBroadcastTime = now;
            for (ServerPlayer player : BottyMod.serverInstance.getPlayerList().getPlayers()) {
                PacketHandler.sendToPlayer(new S2CMusicCommandPacket(Action.PLAY_Direct, packet.url, packet.duration), player);
            }
            BottyMod.serverInstance.getPlayerList().broadcastSystemMessage(
                    Component.literal("§a[Bot] 正在全服播放: " + packet.songName), false
            );
        } else {
            PacketHandler.sendToPlayer(new S2CMusicCommandPacket(Action.PLAY_Direct, packet.url, packet.duration), sender);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void resetGlobalCooldown() {
        lastGlobalBroadcastTime = 0L;
    }
}
