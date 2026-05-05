package com.bot.aibot.network;

import com.bot.aibot.BottyMod;
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.utils.ChineseUtils;
import com.bot.aibot.utils.HttpUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BotClient {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final BotClient INSTANCE = new BotClient();
    public static BotClient getInstance() { return INSTANCE; }

    // Periodic watchdog tick for connection health checks.
    private static final long HEALTH_CHECK_INTERVAL_SECONDS = 15L;
    // Send one ping every 30s while connected.
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    // If a sent ping has no pong for 20s, treat the connection as stale.
    private static final long PONG_TIMEOUT_MS = 20_000L;
    // If no inbound traffic is observed for 3 minutes, force reconnect.
    private static final long SILENT_TIMEOUT_MS = 180_000L;

    private volatile WebSocket webSocket;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicBoolean pongPending = new AtomicBoolean(false);
    private volatile long lastInboundAtMs = 0L;
    private volatile long lastOutboundAtMs = 0L;
    private volatile long lastPongAtMs = 0L;
    private volatile long lastPingSentAtMs = 0L;

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

    private BotClient() {
        startConnectionWatchdog();
    }

    public void connect() {
        if (reconnectScheduler.isShutdown()) {
            reconnectScheduler = createReconnectScheduler();
        }
        LOGGER.info(">>> [Bot] connect() invoked, intentionalClose=false, 重置重连状态");
        intentionalClose = false;
        reconnectPending.set(false);
        currentReconnectDelay = BotConfig.SERVER.reconnectInitialInterval.get();
        doConnect();
    }

    private void doConnect() {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            LOGGER.info(">>> [Bot] 跳过连接：当前已有活动连接 ws=" + wsId(webSocket));
            return;
        }
        if (!isConnecting.compareAndSet(false, true)) {
            LOGGER.info(">>> [Bot] 跳过连接：已有连接任务在进行中");
            return;
        }

        LOGGER.info(">>> [Bot] 开始建立连接...");
        scheduler.submit(() -> {
            try {
                String url = BotConfig.SERVER.wsUrl.get();
                String token = BotConfig.SERVER.accessToken.get();
                LOGGER.info(">>> [Bot] 连接参数: url=" + url + ", authTokenConfigured=" + (token != null && !token.isEmpty()));

                HttpClient client = HttpUtils.getClient();
                WebSocket.Builder builder = client.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(10));

                if (token != null && !token.isEmpty()) {
                    builder.header("Authorization", "Bearer " + token);
                }

                CompletableFuture<WebSocket> wsFuture = builder.buildAsync(URI.create(url), new WSListener());
                WebSocket connected = wsFuture.get(15, TimeUnit.SECONDS);
                if (connected == null || connected.isOutputClosed()) {
                    throw new IllegalStateException("WebSocket opened then closed immediately");
                }
                webSocket = connected;
                if (this.webSocket != connected || connected.isOutputClosed()) {
                    throw new IllegalStateException("WebSocket became inactive before confirmation");
                }
                onConnected();
                if (this.webSocket == connected && !connected.isOutputClosed()) {
                    if (BottyMod.serverInstance != null) {
                        BottyMod.serverInstance.execute(() ->
                                BottyMod.serverInstance.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("§a[Bot] 连接成功！"), false)
                        );
                    }
                    sendStartMessageToQQ();
                    LOGGER.info(">>> [Bot] 连接成功！ws=" + wsId(connected));
                } else {
                    throw new IllegalStateException("WebSocket closed during success notification");
                }

            } catch (Exception e) {
                LOGGER.error(">>> [Bot] 连接失败: " + e.getMessage());
                webSocket = null;
                scheduleReconnect(true);
            } finally {
                isConnecting.set(false);
            }
        });
    }

    public void onConnected() {
        long now = System.currentTimeMillis();
        lastInboundAtMs = now;
        lastOutboundAtMs = now;
        lastPongAtMs = now;
        lastPingSentAtMs = 0L;
        pongPending.set(false);
        reconnectPending.set(false);
        currentReconnectDelay = BotConfig.SERVER.reconnectInitialInterval.get();
        LOGGER.info(">>> [Bot] onConnected: 已重置重连退避参数");
    }

    public boolean isCurrentActiveSocket(WebSocket ws) {
        return this.webSocket == ws && isWebSocketActive(ws);
    }

    public void clearWebSocket() {
        LOGGER.info(">>> [Bot] clearWebSocket: 主动清理引用, oldWs=" + wsId(webSocket));
        webSocket = null;
    }

    public void scheduleReconnect() {
        scheduleReconnect(false);
    }

    private void scheduleReconnect(boolean ignoreConnectingState) {
        if (intentionalClose) {
            LOGGER.info(">>> [Bot] 跳过重连：当前为主动关闭状态");
            return;
        }
        if (!BotConfig.SERVER.reconnectEnabled.get()) {
            LOGGER.info(">>> [Bot] 跳过重连：配置已关闭自动重连");
            return;
        }
        if (!ignoreConnectingState && (isConnecting.get() || (webSocket != null && !webSocket.isOutputClosed()))) {
            LOGGER.info(">>> [Bot] 跳过重连：连接任务进行中或已有活动连接");
            return;
        }
        if (!reconnectPending.compareAndSet(false, true)) {
            LOGGER.info(">>> [Bot] 跳过重连：已有待执行重连任务");
            return;
        }

        long delay = currentReconnectDelay;
        LOGGER.info(">>> [Bot] 将在 " + delay + " 秒后尝试重新连接...");

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
            LOGGER.info(">>> [Bot] 忽略旧连接回调: callbackWs=" + wsId(ws) + ", activeWs=" + wsId(this.webSocket) + ", reason=" + reason);
            return false;
        }
        LOGGER.info(">>> [Bot] 活动连接断开: ws=" + wsId(ws) + ", reason=" + reason);
        this.webSocket = null;
        scheduleReconnect(true);
        return true;
    }

    public void reload() {
        LOGGER.info(">>> [Bot] reload() invoked: 开始热重载");
        close("Reloading");
        ChineseUtils.load();
        LOGGER.info(">>> [Bot] reload() scheduled: 1秒后重新连接");
        scheduler.schedule(this::connect, 1, TimeUnit.SECONDS);
    }

    public void close(String reason) {
        LOGGER.info(">>> [Bot] close() invoked: reason=" + reason + ", ws=" + wsId(webSocket));
        intentionalClose = true;
        reconnectScheduler.shutdownNow();
        pendingReconnectTask = null;
        reconnectPending.set(false);
        pongPending.set(false);
        lastInboundAtMs = 0L;
        lastOutboundAtMs = 0L;
        lastPongAtMs = 0L;
        lastPingSentAtMs = 0L;
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

    public void markInboundActivity(WebSocket ws) {
        if (this.webSocket == ws) {
            lastInboundAtMs = System.currentTimeMillis();
        }
    }

    public void onPong(WebSocket ws) {
        if (this.webSocket != ws) {
            return;
        }
        long now = System.currentTimeMillis();
        lastPongAtMs = now;
        lastInboundAtMs = now;
        pongPending.set(false);
    }

    private void startConnectionWatchdog() {
        scheduler.scheduleAtFixedRate(this::monitorConnectionHealth,
                HEALTH_CHECK_INTERVAL_SECONDS,
                HEALTH_CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    private void monitorConnectionHealth() {
        try {
            WebSocket ws = this.webSocket;
            if (intentionalClose || !isWebSocketActive(ws)) {
                return;
            }
            long now = System.currentTimeMillis();
            if (isPongTimedOut(now)) {
                handleConnectionStale(ws, "心跳 PONG 超时");
                return;
            }
            if (lastInboundAtMs > 0 && now - lastInboundAtMs > SILENT_TIMEOUT_MS) {
                handleConnectionStale(ws, "连接静默超时");
                return;
            }
            if (!pongPending.get() && (lastPingSentAtMs == 0L || now - lastPingSentAtMs >= HEARTBEAT_INTERVAL_MS)) {
                sendHeartbeat(ws, now);
            }
        } catch (Throwable t) {
            LOGGER.error(">>> [Bot] 健康检查异常: " + t.getMessage());
        }
    }

    private void sendHeartbeat(WebSocket ws, long now) {
        if (this.webSocket != ws || !isWebSocketActive(ws)) {
            return;
        }
        lastPingSentAtMs = now;
        pongPending.set(true);
        ws.sendPing(ByteBuffer.wrap("aibot-hb".getBytes(StandardCharsets.UTF_8)))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        pongPending.set(false);
                        LOGGER.error(">>> [Bot] 心跳发送失败: " + error.getMessage());
                        handleConnectionStale(ws, "心跳发送失败");
                    }
                });
    }

    private boolean isPongTimedOut(long now) {
        return pongPending.get() && lastPingSentAtMs > 0 && now - lastPingSentAtMs > PONG_TIMEOUT_MS;
    }

    private void handleConnectionStale(WebSocket ws, String reason) {
        if (!onDisconnect(ws, "watchdog: " + reason)) {
            return;
        }
        long now = System.currentTimeMillis();
        LOGGER.error(">>> [Bot] 触发连接自愈重连: ws=" + wsId(ws)
                + ", reason=" + reason
                + ", inboundIdleMs=" + elapsed(now, lastInboundAtMs)
                + ", outboundIdleMs=" + elapsed(now, lastOutboundAtMs)
                + ", pongIdleMs=" + elapsed(now, lastPongAtMs));
        try {
            ws.abort();
        } catch (Exception ignored) {
        }
    }

    private long elapsed(long now, long ts) {
        return ts <= 0 ? -1L : (now - ts);
    }

    private boolean isWebSocketActive(WebSocket ws) {
        return ws != null && !ws.isOutputClosed() && !ws.isInputClosed();
    }

    private void sendTextWithFailureLog(WebSocket ws, String payload, String context) {
        lastOutboundAtMs = System.currentTimeMillis();
        ws.sendText(payload, true).whenComplete((ignored, error) -> {
            if (error != null) {
                LOGGER.error(">>> [Bot] 发送失败 [" + context + "]: " + error.getMessage());
            }
        });
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
                sendMessageToQQ(groupIdNum.longValue(), message);
            }
        }
    }

    public void sendMessageToQQ(long groupId, String message) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            JsonObject params = new JsonObject();
            params.addProperty("group_id", groupId);
            params.addProperty("message", message);

            JsonObject root = new JsonObject();
            root.addProperty("action", "send_group_msg");
            root.add("params", params);

            sendTextWithFailureLog(webSocket, new Gson().toJson(root), "send_group_msg gid=" + groupId);
        }
    }

    public void sendRawJson(String json) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            sendTextWithFailureLog(webSocket, json, "send_raw_json");
        }
    }

    private void sendStartMessageToQQ() {
        try {
            String template = BotConfig.SERVER.startMsgFormat.get();
            String prefix = BotConfig.SERVER.mcPrefix.get();
            String msg = template.replace("%prefix%", prefix);
            sendMessageToQQ(msg);
        } catch (Exception e) {
            LOGGER.error(">>> [Bot] 发送启动消息失败: " + e.getMessage());
        }
    }
}
