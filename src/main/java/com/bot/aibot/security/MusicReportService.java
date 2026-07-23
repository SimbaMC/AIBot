package com.bot.aibot.security;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CMusicCommandPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class MusicReportService {
    private static final int MAX_DURATION_MS = 24 * 60 * 60 * 1000;
    private static ThreadPoolExecutor validator;
    private static long nextGlobalBroadcastAt;

    private MusicReportService() {}

    public static void submit(ServerPlayer sender, String url, String songName, long duration, boolean global) {
        if (duration <= 0 || duration > MAX_DURATION_MS || songName.isBlank() || songName.length() > 256) {
            reject(sender, "歌曲信息无效");
            return;
        }
        MinecraftServer server = sender.getServer();
        UUID playerId = sender.getUUID();
        try {
            validator().execute(() -> {
                final URI accepted;
                try {
                    accepted = MusicUrlPolicy.validate(url);
                } catch (MusicUrlPolicy.MusicUrlException e) {
                    server.execute(() -> rejectCurrent(server, playerId, sender, "音乐地址未通过安全检查"));
                    return;
                }
                server.execute(() -> distribute(server, playerId, sender, accepted.toASCIIString(), songName, duration, global));
            });
        } catch (RuntimeException e) {
            reject(sender, "音乐地址验证繁忙，请稍后重试");
        }
    }

    private static void distribute(MinecraftServer server, UUID id, ServerPlayer session, String url,
                                   String songName, long duration, boolean global) {
        ServerPlayer sender = currentPlayer(server, id, session);
        if (sender == null) return;
        S2CMusicCommandPacket packet = new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.PLAY_DIRECT, url, duration);
        if (!global) {
            PacketHandler.sendToPlayer(packet, sender);
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextGlobalBroadcastAt) {
            long seconds = Math.max(1, (nextGlobalBroadcastAt - now + 999) / 1000);
            reject(sender, "全服播放冷却中，还需 " + seconds + " 秒");
            return;
        }
        long cooldownMillis = Math.max(0L, BotConfig.SERVER.broadcastCooldown.get()) * 1000L;
        nextGlobalBroadcastAt = now + cooldownMillis;
        PacketHandler.sendToAll(packet);
        server.getPlayerList().broadcastSystemMessage(Component.literal("正在全服播放: §a" + songName), false);
    }

    private static ServerPlayer currentPlayer(MinecraftServer server, UUID id, ServerPlayer session) {
        ServerPlayer current = server.getPlayerList().getPlayer(id);
        return current == session && current.connection.connection.isConnected() ? current : null;
    }

    private static void rejectCurrent(MinecraftServer server, UUID id, ServerPlayer session, String message) {
        ServerPlayer current = currentPlayer(server, id, session);
        if (current != null) reject(current, message);
    }

    private static void reject(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("§c[Bot] " + message));
    }

    public static void shutdown() {
        synchronized (MusicReportService.class) {
            if (validator != null) validator.shutdownNow();
            validator = null;
        }
        nextGlobalBroadcastAt = 0;
    }

    private static synchronized ThreadPoolExecutor validator() {
        if (validator == null) {
            validator = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(32), runnable -> {
                Thread thread = new Thread(runnable, "AiBot-Music-URL-Validator");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
        }
        return validator;
    }
}
