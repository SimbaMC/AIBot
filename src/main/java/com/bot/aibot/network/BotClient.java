package com.bot.aibot.network;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.minecraft.util.ChatComponentText;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.bot.aibot.BottyMod;
import com.bot.aibot.config.BotConfig;
import com.google.gson.JsonObject;

public final class BotClient {

    private static final BotClient INSTANCE = new BotClient();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private volatile WebSocketClient socket;
    private volatile boolean intentionalClose;
    private volatile int reconnectDelay;

    public static BotClient getInstance() {
        return INSTANCE;
    }

    private BotClient() {}

    public synchronized void connect() {
        intentionalClose = false;
        reconnectDelay = BotConfig.reconnectInitialInterval;
        open();
    }

    private synchronized void open() {
        if (socket != null && socket.isOpen()) return;
        try {
            socket = new WebSocketClient(new URI(BotConfig.wsUrl)) {

                public void onOpen(ServerHandshake handshake) {
                    reconnectDelay = BotConfig.reconnectInitialInterval;
                    broadcast("§a[Bot] 连接成功！");
                    sendMessageToQQ(BotConfig.startMsgFormat.replace("%prefix%", BotConfig.mcPrefix));
                }

                public void onMessage(String message) {
                    WSListener.process(message);
                }

                public void onMessage(ByteBuffer bytes) {}

                public void onClose(int code, String reason, boolean remote) {
                    broadcast("§c[Bot] 连接断开！");
                    scheduleReconnect();
                }

                public void onError(Exception error) {
                    BottyMod.LOG.error("OneBot websocket error", error);
                }
            };
            if (!BotConfig.accessToken.isEmpty()) {
                socket.addHeader("Authorization", "Bearer " + BotConfig.accessToken);
            }
            socket.setConnectionLostTimeout(45);
            socket.connect();
        } catch (Exception e) {
            BottyMod.LOG.error(">>> [Bot] OneBot WebSocket 连接失败", e);
            scheduleReconnect();
        }
    }

    private synchronized void scheduleReconnect() {
        socket = null;
        if (intentionalClose || !BotConfig.reconnectEnabled) return;
        final int delay = reconnectDelay;
        reconnectDelay = Math
            .min(BotConfig.reconnectMaxInterval, Math.max(delay + 1, (int) (delay * BotConfig.reconnectMultiplier)));
        executor.schedule(new Runnable() {

            public void run() {
                open();
            }
        }, delay, TimeUnit.SECONDS);
    }

    public synchronized void close(String reason) {
        intentionalClose = true;
        if (socket != null) socket.close(1000, reason);
        socket = null;
    }

    public void reload() {
        close("Reloading");
        BotConfig.load();
        executor.schedule(new Runnable() {

            public void run() {
                connect();
            }
        }, 1, TimeUnit.SECONDS);
    }

    public void sendMessageToQQ(String message) {
        WebSocketClient ws = socket;
        if (ws == null || !ws.isOpen()) return;
        for (Long group : BotConfig.groupIds) {
            JsonObject params = new JsonObject();
            params.addProperty("group_id", group.longValue());
            params.addProperty("message", message);
            JsonObject root = new JsonObject();
            root.addProperty("action", "send_group_msg");
            root.add("params", params);
            ws.send(root.toString());
        }
    }

    private static void broadcast(String text) {
        if (BottyMod.serverInstance != null && BottyMod.serverInstance.getConfigurationManager() != null)
            BottyMod.serverInstance.getConfigurationManager()
                .sendChatMsg(new ChatComponentText(text));
    }
}
