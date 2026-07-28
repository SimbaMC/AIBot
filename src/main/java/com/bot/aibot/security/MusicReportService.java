package com.bot.aibot.security;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.bot.aibot.BottyMod;
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.ServerTaskQueue;
import com.bot.aibot.network.packet.S2CMusicCommandPacket;

public final class MusicReportService {

    private static ThreadPoolExecutor validator;
    private static long nextGlobalAt;

    private MusicReportService() {}

    public static void submit(final EntityPlayerMP session, final String url, final String name, final long duration,
        final boolean global) {
        final UUID id = session.getUniqueID();
        try {
            executor().execute(new Runnable() {

                public void run() {
                    try {
                        final URI accepted = MusicUrlPolicy.validate(url);
                        ServerTaskQueue.submit(new Runnable() {

                            public void run() {
                                distribute(id, session, accepted.toASCIIString(), name, duration, global);
                            }
                        });
                    } catch (Exception e) {
                        BottyMod.LOG.warn("Rejected a music URL reported by {}: {}", id, e.getMessage());
                        ServerTaskQueue.submit(new Runnable() {

                            public void run() {
                                rejectCurrent(id, session, "音乐地址未通过安全检查");
                            }
                        });
                    }
                }
            });
        } catch (RuntimeException e) {
            ServerTaskQueue.submit(new Runnable() {

                public void run() {
                    rejectCurrent(id, session, "音乐地址验证繁忙，请稍后重试");
                }
            });
        }
    }

    private static void distribute(UUID id, EntityPlayerMP session, String url, String name, long duration,
        boolean global) {
        EntityPlayerMP sender = current(id, session);
        if (sender == null) return;
        S2CMusicCommandPacket packet = new S2CMusicCommandPacket(
            global ? S2CMusicCommandPacket.Action.PLAY_DIRECT_BROADCAST : S2CMusicCommandPacket.Action.PLAY_DIRECT,
            url,
            duration);
        if (!global) {
            PacketHandler.sendToPlayer(packet, sender);
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextGlobalAt) {
            reject(sender, "全服播放冷却中，还需 " + Math.max(1, (nextGlobalAt - now + 999) / 1000) + " 秒");
            return;
        }
        nextGlobalAt = now + Math.max(0, BotConfig.broadcastCooldown) * 1000L;
        PacketHandler.sendToAll(packet);
        BottyMod.serverInstance.getConfigurationManager()
            .sendChatMsg(new ChatComponentText("正在全服播放: §a" + name));
    }

    private static EntityPlayerMP current(UUID id, EntityPlayerMP session) {
        if (BottyMod.serverInstance == null) return null;
        for (Object o : BottyMod.serverInstance.getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (p.getUniqueID()
                .equals(id))
                return p == session && p.playerNetServerHandler != null
                    && p.playerNetServerHandler.netManager.isChannelOpen() ? p : null;
        }
        return null;
    }

    private static void rejectCurrent(UUID id, EntityPlayerMP s, String m) {
        EntityPlayerMP p = current(id, s);
        if (p != null) reject(p, m);
    }

    private static void reject(EntityPlayerMP p, String m) {
        PacketHandler.sendToPlayer(new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.PLAY_REJECTED, m, 0), p);
        p.addChatMessage(new ChatComponentText("§c[Bot] " + m));
    }

    public static synchronized void shutdown() {
        if (validator != null) validator.shutdownNow();
        validator = null;
        nextGlobalAt = 0;
    }

    public static void resetBroadcastCooldown() {
        nextGlobalAt = 0;
    }

    private static synchronized ThreadPoolExecutor executor() {
        if (validator == null) validator = new ThreadPoolExecutor(
            1,
            2,
            30,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(32),
            new ThreadFactory() {

                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "AiBot-Music-URL-Validator");
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.AbortPolicy());
        return validator;
    }
}
