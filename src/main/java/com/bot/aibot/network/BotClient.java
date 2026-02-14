package com.bot.aibot.network;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.utils.ChineseUtils;
import com.bot.aibot.utils.HttpUtils;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BotClient {

    public volatile boolean isLoggingIn = false;

    // 单例模式
    private static final BotClient INSTANCE = new BotClient();
    public static BotClient getInstance() { return INSTANCE; }

    private WebSocket webSocket;
    private Thread connectionThread;

    // 防止重复重连的标志位
    private volatile boolean isReconnecting = false;

    // 连接逻辑
    public void connect() {
        // 如果正在连接中，直接跳过
        if (connectionThread != null && connectionThread.isAlive()) return;

        connectionThread = new Thread(() -> {
            try {
                String url = BotConfig.SERVER.wsUrl.get();
                String token = BotConfig.SERVER.accessToken.get(); // 获取 Token

                System.out.println(">>> [Bot] 正在后台尝试连接: " + url);

                HttpClient client = HttpUtils.getClient(); // 使用全局单例

                // 1. 创建 Builder
                WebSocket.Builder builder = client.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(10)); // 延长一点超时时间到10秒

                // 2. 【关键修改】添加鉴权头 (NapCat/OneBot 标准)
                if (token != null && !token.isEmpty()) {
                    builder.header("Authorization", "Bearer " + token);
                    System.out.println(">>> [Bot] 已启用 Token 鉴权");
                }

                // 3. 发起异步连接
                CompletableFuture<WebSocket> wsFuture = builder.buildAsync(URI.create(url), new WSListener());

                try {
                    // 4. 等待连接结果 (阻塞这个后台线程)
                    webSocket = wsFuture.get(10, TimeUnit.SECONDS);
                    System.out.println(">>> [Bot] 连接成功！");
                    isReconnecting = false; // 重置重连标志
                } catch (Exception e) {
                    System.err.println(">>> [Bot] 连接失败: " + e.getCause().getMessage());
                    webSocket = null;
                    // 触发重连
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

    // 【新增】自动重连机制
    public void scheduleReconnect() {
        if (isReconnecting) return;
        isReconnecting = true;

        System.out.println(">>> [Bot] 5秒后尝试重连...");
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                if (isReconnecting) {
                    // 清理旧线程状态
                    connectionThread = null;
                    connect();
                }
            } catch (InterruptedException ignored) {}
        }, "Bot-Reconnector").start();
    }

    // 供 WSListener 监听到断开时调用
    public void onDisconnect() {
        this.webSocket = null;
        scheduleReconnect();
    }

    // 重载逻辑
    public void reload() {
        // 主动重载时，先停止自动重连，防止逻辑冲突
        isReconnecting = false;
        close("Reloading");

        // 重新加载配置
        // 注意：如果是 Forge 配置，通常会自动热重载，这里主要是加载你的自定义词库
        ChineseUtils.load();

        // 重新连接
        connectionThread = null; // 确保 connect 能启动新线程
        connect();
    }

    // 关闭逻辑
    public void close(String reason) {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, reason);
            webSocket = null;
        }
    }

    // 发送消息逻辑 (供外部调用)
    public void sendMessageToQQ(String message) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            List<? extends Number> groups = BotConfig.SERVER.groupIds.get();
            for (Number groupId : groups) {
                long gid = groupId.longValue();

                JsonObject params = new JsonObject();
                params.addProperty("group_id", gid);
                params.addProperty("message", message);

                JsonObject root = new JsonObject();
                root.addProperty("action", "send_group_msg");
                root.add("params", params);

                // 使用你的 HttpUtils 工具类
                String json = HttpUtils.getGson().toJson(root);

                webSocket.sendText(json, true);
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