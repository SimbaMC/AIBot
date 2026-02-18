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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BotClient {

    private static final BotClient INSTANCE = new BotClient();
    public static BotClient getInstance() { return INSTANCE; }

    private WebSocket webSocket;

    // 使用原子变量控制状态，确保线程安全
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    // 单线程执行器，专门处理连接和重连任务，避免多线程混乱
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Bot-Connection-Manager");
        t.setDaemon(true);
        return t;
    });

    // 连接逻辑
    public void connect() {
        // 如果已经连接或正在连接，直接返回
        if (webSocket != null || isConnecting.get()) {
            return;
        }

        isConnecting.set(true);
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

                // 异步建立连接
                CompletableFuture<WebSocket> wsFuture = builder.buildAsync(URI.create(url), new WSListener());

                // 等待连接结果 (设置15秒超时)
                this.webSocket = wsFuture.get(15, TimeUnit.SECONDS);
                System.out.println(">>> [Bot] 连接成功！");

            } catch (Exception e) {
                System.err.println(">>> [Bot] 连接失败: " + e.getMessage());
                this.webSocket = null;
                // 失败后延迟重连
                scheduleReconnect();
            } finally {
                isConnecting.set(false);
            }
        });
    }

    // 自动重连机制
    public void scheduleReconnect() {
        // 如果正在连接，或者当前已有活跃连接，则无需重连
        if (isConnecting.get() || (webSocket != null && !webSocket.isOutputClosed())) {
            return;
        }

        System.out.println(">>> [Bot] 5秒后尝试重连...");
        // 使用 scheduler 延迟执行，而不是新建线程
        scheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
    }

    // 供 WSListener 监听到断开时调用
    public void onDisconnect(WebSocket ws, String reason) {
        System.out.println(">>> [Bot] 连接断开: " + reason);
        // 只有当前持有的 websocket 是断开的那个时，才清空并重连
        if (this.webSocket == ws) {
            this.webSocket = null;
            scheduleReconnect();
        }
    }

    // 关闭逻辑
    public void close(String reason) {
        if (webSocket != null) {
            System.out.println(">>> [Bot] 主动关闭连接: " + reason);
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, reason);
            webSocket = null;
        }
        // 防止关闭时触发重连
        isConnecting.set(false);
    }

    // 重载逻辑
    public void reload() {
        close("Reloading");
        ChineseUtils.load();
        // 给一点时间让连接彻底断开
        scheduler.schedule(this::connect, 1, TimeUnit.SECONDS);
    }

    // 发送消息逻辑
    public void sendMessageToQQ(String message) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            // 【修正】这里根据 BotConfig.java 的定义，使用 List<? extends Number>
            List<? extends Number> groups = BotConfig.SERVER.groupIds.get();

            for (Number groupIdNum : groups) {
                long gid = groupIdNum.longValue(); // 安全转换为 long

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

    // 供 Listener 回复消息用
    public void sendRawJson(String json) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            webSocket.sendText(json, true);
        }
    }
}