package com.bot.aibot.network;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.utils.ChineseUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BotClient {

    public volatile boolean isLoggingIn = false;

    // 单例模式：确保全局只有一个 Client 管理器
    private static final BotClient INSTANCE = new BotClient();
    public static BotClient getInstance() { return INSTANCE; }

    private WebSocket webSocket;
    private Thread connectionThread;

    // 连接逻辑
    public void connect() {
        if (connectionThread != null && connectionThread.isAlive()) return;

        connectionThread = new Thread(() -> {
            try {
                System.out.println(">>> [Bot] 正在后台尝试连接...");
                String url = BotConfig.SERVER.wsUrl.get();
                HttpClient client = HttpClient.newHttpClient();

                CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(5))
                        .buildAsync(URI.create(url), new WSListener()); // 使用分离出去的 Listener

                try {
                    webSocket = wsFuture.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    System.out.println(">>> [Bot] 连接失败: " + e.getMessage());
                    webSocket = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Bot-Connector");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    // 重载逻辑
    public void reload() {
        close("Reloading");
        ChineseUtils.load();
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