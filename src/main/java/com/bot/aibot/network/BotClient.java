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
        System.out.println(">>> [Bot] connect() invoked, intentionalClose=false, 重置重连状态");
        intentionalClose = false;
        reconnectPending.set(false);
        currentReconnectDelay = BotConfig.SERVER.reconnectInitialInterval.get();
        doConnect();
    }

    private void doConnect() {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            System.out.println(">>> [Bot] 跳过连接：当前已有活动连接 ws=" + wsId(webSocket));
            return;
        }
        if (!isConnecting.compareAndSet(false, true)) {
            System.out.println(">>> [Bot] 跳过连接：已有连接任务在进行中");
            return;
        }

        System.out.println(">>> [Bot] 开始建立连接...");
        scheduler.submit(() -> {
            try {
                String url = BotConfig.SERVER.wsUrl.get();
                String token = BotConfig.SERVER.accessToken.get();
                System.out.println(">>> [Bot] 连接参数: url=" + url + ", authTokenConfigured=" + (token != null && !token.isEmpty()));

                HttpClient client = HttpUtils.getClient();
                WebSocket.Builder builder = client.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(10));

                if (token != null && !token.isEmpty()) {
                    builder.header("Authorization", "Bearer " + token);
                }

                CompletableFuture<WebSocket> wsFuture = builder.buildAsync(URI.create(url), new WSListener());
                webSocket = wsFuture.get(15, TimeUnit.SECONDS);
                System.out.println(">>> [Bot] 连接成功！ws=" + wsId(webSocket));

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
        System.out.println(">>> [Bot] onConnected: 已重置重连退避参数");
    }

    public void clearWebSocket() {
        System.out.println(">>> [Bot] clearWebSocket: 主动清理引用, oldWs=" + wsId(webSocket));
        webSocket = null;
    }

    public void scheduleReconnect() {
        if (intentionalClose) {
            System.out.println(">>> [Bot] 跳过重连：当前为主动关闭状态");
            return;
        }
        if (!BotConfig.SERVER.reconnectEnabled.get()) {
            System.out.println(">>> [Bot] 跳过重连：配置已关闭自动重连");
            return;
        }
        if (isConnecting.get() || (webSocket != null && !webSocket.isOutputClosed())) {
            System.out.println(">>> [Bot] 跳过重连：连接任务进行中或已有活动连接");
            return;
        }
        if (!reconnectPending.compareAndSet(false, true)) {
            System.out.println(">>> [Bot] 跳过重连：已有待执行重连任务");
            return;
        }

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

    /**
     * Handles disconnect callbacks and returns whether the callback belongs to the current active socket.
     * true = active socket disconnected; false = stale/old callback ignored.
     */
    public boolean onDisconnect(WebSocket ws, String reason) {
        if (this.webSocket != ws) {
            System.out.println(">>> [Bot] 忽略旧连接回调: callbackWs=" + wsId(ws) + ", activeWs=" + wsId(this.webSocket) + ", reason=" + reason);
            return false;
        }
        System.out.println(">>> [Bot] 活动连接断开: ws=" + wsId(ws) + ", reason=" + reason);
        this.webSocket = null;
        scheduleReconnect();
        return true;
    }

    public void reload() {
        System.out.println(">>> [Bot] reload() invoked: 开始热重载");
        close("Reloading");
        ChineseUtils.load();
        System.out.println(">>> [Bot] reload() scheduled: 1秒后重新连接");
        scheduler.schedule(this::connect, 1, TimeUnit.SECONDS);
    }

    public void close(String reason) {
        System.out.println(">>> [Bot] close() invoked: reason=" + reason + ", ws=" + wsId(webSocket));
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

    public boolean isIntentionalClose() {
        return intentionalClose;
    }

    /**
     * Returns a stable hex identity string for diagnostic logs to distinguish socket instances.
     */
    private String wsId(WebSocket ws) {
        return ws == null ? "null" : Integer.toHexString(System.identityHashCode(ws));
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
