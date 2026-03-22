package com.bot.aibot.network;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.utils.ChineseUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BotClient {

    public volatile boolean isLoggingIn = false;

    // 单例模式：确保全局只有一个 Client 管理器
    private static final BotClient INSTANCE = new BotClient();
    public static BotClient getInstance() { return INSTANCE; }

    private volatile WebSocket webSocket;
    private Thread connectionThread;

    // 重连相关状态
    private volatile ScheduledExecutorService reconnectScheduler = createScheduler();

    private static ScheduledExecutorService createScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Bot-Reconnect");
            t.setDaemon(true);
            return t;
        });
    }
    private final AtomicBoolean reconnectPending = new AtomicBoolean(false);
    private volatile long currentReconnectDelay;
    private volatile boolean intentionalClose = false;
    private volatile ScheduledFuture<?> pendingReconnectTask = null;

    // 连接逻辑（公共入口，重置重连状态）
    public void connect() {
        if (reconnectScheduler.isShutdown()) {
            reconnectScheduler = createScheduler();
        }
        intentionalClose = false;
        reconnectPending.set(false);
        currentReconnectDelay = BotConfig.SERVER.reconnectInitialInterval.get();
        doConnect();
    }

    // 实际执行连接的内部方法
    private void doConnect() {
        if (connectionThread != null && connectionThread.isAlive()) return;

        connectionThread = new Thread(() -> {
            try {
                System.out.println(">>> [Bot] 正在后台尝试连接...");
                String url = BotConfig.SERVER.wsUrl.get();
                HttpClient client = HttpClient.newHttpClient();

                CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(5))
                        .buildAsync(URI.create(url), new WSListener());

                try {
                    webSocket = wsFuture.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    System.out.println(">>> [Bot] 连接失败: " + e.getMessage());
                    webSocket = null;
                    scheduleReconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
                scheduleReconnect();
            }
        }, "Bot-Connector");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    /**
     * 由 WSListener.onOpen() 调用，重置重连延迟计数器。
     */
    public void onConnected() {
        reconnectPending.set(false);
        currentReconnectDelay = BotConfig.SERVER.reconnectInitialInterval.get();
    }

    /**
     * 由 WSListener.onClose() / onError() 调用，清除本地 WebSocket 引用。
     */
    public void clearWebSocket() {
        webSocket = null;
    }

    /**
     * 安排一次带指数退避的重连尝试。
     * 若已有挂起的重连任务、或本次关闭是主动触发的、或配置禁用了重连，则跳过。
     */
    public void scheduleReconnect() {
        if (intentionalClose) return;
        if (!BotConfig.SERVER.reconnectEnabled.get()) return;
        if (!reconnectPending.compareAndSet(false, true)) return;

        long delay = currentReconnectDelay;
        System.out.println(">>> [Bot] 将在 " + delay + " 秒后尝试重新连接...");

        // 为下一次失败预先计算新延迟（乘以倍率，并截断到上限）
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
            // 调度器已被关闭（极少数竞争情况），忽略即可
            reconnectPending.set(false);
        }
    }

    // 重载逻辑
    public void reload() {
        close("Reloading");
        ChineseUtils.load();
        connect();
    }

    // 关闭逻辑（主动关闭，取消所有挂起的重连并释放调度器）
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
    }

    // 发送消息逻辑 (供外部调用)
    public void sendMessageToQQ(String message) {
        if (webSocket != null) {
            List<? extends Number> groups = BotConfig.SERVER.groupIds.get();
            for (Number groupId : groups) {
                long gid = groupId.longValue();
                String json = "{\"action\":\"send_group_msg\",\"params\":{\"group_id\":" + gid + ",\"message\":\"" + message + "\"}}";
                webSocket.sendText(json, true);
            }
        }
    }

    // 供 Listener 回复消息用
    public void sendRawJson(String json) {
        if (webSocket != null) {
            webSocket.sendText(json, true);
        }
    }
}