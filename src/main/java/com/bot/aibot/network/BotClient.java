package com.bot.aibot.network;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.utils.ChineseUtils;
import com.bot.aibot.utils.HttpUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BotClient {

    private static final BotClient INSTANCE = new BotClient();
    public static BotClient getInstance() { return INSTANCE; }

    private volatile WebSocket webSocket;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Bot-Connection-Manager");
        t.setDaemon(true);
        return t;
    });

    private volatile ScheduledExecutorService reconnectScheduler = createReconnectScheduler();

    private static ScheduledExecutorService createReconnectScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Bot-Reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    private final AtomicBoolean reconnectPending = new AtomicBoolean(false);
    private volatile long currentReconnectDelay = 5L;
    private volatile boolean intentionalClose = false;
    private volatile ScheduledFuture<?> pendingReconnectTask = null;

    public void connect() {
        if (reconnectScheduler.isShutdown()) {
            reconnectScheduler = createReconnectScheduler();
        }
        intentionalClose = false;
        reconnectPending.set(false);
        currentReconnectDelay = BotConfig.SERVER.reconnectInitialInterval.get();
        doConnect();
    }

    private void doConnect() {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            return;
        }
        if (!isConnecting.compareAndSet(false, true)) {
            return;
        }

        System.out.println(">>> [Bot] 开始建立连接...");
        scheduler.submit(() -> {
            try {
                String url = BotConfig.SERVER.wsUrl.get();
                String token = BotConfig.SERVER.accessToken.get();

                HttpClient client = HttpUtils.getClient();
                WebSocket.Builder builder = client.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(10));

                if (token != null && !token.isEmpty()) {
                    builder.header("Authorization", "Bearer " + token);
                }

                CompletableFuture<WebSocket> wsFuture = builder.buildAsync(URI.create(url), new WSListener());
                webSocket = wsFuture.get(15, TimeUnit.SECONDS);
                System.out.println(">>> [Bot] 连接成功！");

            } catch (Exception e) {
                System.err.println(">>> [Bot] 连接失败: " + e.getMessage());
                webSocket = null;
                scheduleReconnect();
            } finally {
                isConnecting.set(false);
            }
        });
    }

    public void onConnected() {
        reconnectPending.set(false);
        currentReconnectDelay = BotConfig.SERVER.reconnectInitialInterval.get();
    }

    public void clearWebSocket() {
        webSocket = null;
    }

    public void scheduleReconnect() {
        if (intentionalClose) return;
        if (!BotConfig.SERVER.reconnectEnabled.get()) return;
        if (isConnecting.get() || (webSocket != null && !webSocket.isOutputClosed())) return;
        if (!reconnectPending.compareAndSet(false, true)) return;

        long delay = currentReconnectDelay;
        System.out.println(">>> [Bot] 将在 " + delay + " 秒后尝试重新连接...");

        double multiplier = BotConfig.SERVER.reconnectMultiplier.get();
        long maxDelay = BotConfig.SERVER.reconnectMaxInterval.get();
        currentReconnectDelay = Math.min((long) (delay * multiplier), maxDelay);

        pendingReconnectTask = null;
        try {
            pendingReconnectTask = reconnectScheduler.schedule(() -> {
                reconnectPending.set(false);
                if (!intentionalClose) {
                    doConnect();
                }
            }, delay, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            reconnectPending.set(false);
        }
    }

    public void onDisconnect(WebSocket ws, String reason) {
        System.out.println(">>> [Bot] 连接断开: " + reason);
        if (this.webSocket == ws) {
            this.webSocket = null;
            scheduleReconnect();
        }
    }

    public void reload() {
        close("Reloading");
        ChineseUtils.load();
        scheduler.schedule(this::connect, 1, TimeUnit.SECONDS);
    }

    public void close(String reason) {
        intentionalClose = true;
        reconnectScheduler.shutdownNow();
        pendingReconnectTask = null;
        reconnectPending.set(false);
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, reason);
            } catch (Exception ignored) {
            }
            webSocket = null;
        }
        isConnecting.set(false);
    }

    public void sendMessageToQQ(String message) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            List<? extends Number> groups = BotConfig.SERVER.groupIds.get();

            for (Number groupIdNum : groups) {
                long gid = groupIdNum.longValue();

                JsonObject params = new JsonObject();
                params.addProperty("group_id", gid);
                params.addProperty("message", message);

                JsonObject root = new JsonObject();
                root.addProperty("action", "send_group_msg");
                root.add("params", params);

                webSocket.sendText(new Gson().toJson(root), true);
            }
        }
    }

    public void sendRawJson(String json) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            webSocket.sendText(json, true);
        }
    }
}
