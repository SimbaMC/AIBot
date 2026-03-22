package com.bot.aibot.network;

import com.bot.aibot.BottyMod;
import com.bot.aibot.config.BotConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class WSListener implements WebSocket.Listener {

    // 【新增】用来暂存分片消息的缓冲区
    private final StringBuilder buffer = new StringBuilder();

    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println(">>> [Bot] 连接成功！等待消息...");
        webSocket.request(1);

        // 通知 BotClient 连接已建立，重置重连计数器
        BotClient.getInstance().onConnected();

        // 1. 游戏内广播
        if (BottyMod.serverInstance != null) {
            BottyMod.serverInstance.execute(() ->
                    BottyMod.serverInstance.getPlayerList().broadcastSystemMessage(Component.literal("§a[Bot] 连接成功！"), false)
            );
        }

        // 2. 向 QQ 群发送启动问候
        sendStartMessage(webSocket);
    }
    //================================内存熔断防炸=========================================================================================================
    // 定义一个常量：最大允许 1MB 的消息 (足够存几十万字的作文了)
    private static final int MAX_BUFFER_SIZE = 1024 * 1024;

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        // 1. 必须请求下一部分数据（无论是下一片碎片，还是下一条新消息）
        webSocket.request(1);

        // --- 【新增】保险丝机制 ---
        // 如果当前缓冲区大小 + 新来的数据长度 > 1MB，说明不对劲
        if (buffer.length() + data.length() > MAX_BUFFER_SIZE) {
            System.out.println(">>> [Bot] ⚠️ 警告：检测到异常过大的消息 (>1MB)，已自动拦截并丢弃！保护服务器内存。");

            // 熔断：清空缓冲区
            buffer.setLength(0);

            // 直接返回，不再处理后续数据，直到下一条新消息覆盖
            return null;
        }
        // ------------------------

        // 2. 将收到的数据追加到缓冲区
        buffer.append(data);

        // 3. 如果 last 为 false，说明消息还没发完，直接返回，等待下一片
        if (!last) {
            return null;
        }

        // 4. last 为 true，说明接收完毕，取出完整字符串
        String fullMessage = buffer.toString();
        // 清空缓冲区，为下一条消息做准备
        if (buffer.capacity() > MAX_BUFFER_SIZE) {
            buffer.setLength(0);
            buffer.trimToSize();
        } else {
            buffer.setLength(0);
        }

        // 5. 开始解析完整 JSON
        try {
            JsonObject json = JsonParser.parseString(fullMessage).getAsJsonObject();

            // --- 下面是原本的逻辑，保持不变 ---

            // 过滤 Bot 自身消息
            long configBotId = BotConfig.SERVER.targetBotId.get();
            if (configBotId != 0 && json.has("self_id") && json.get("self_id").getAsLong() != configBotId) {
                return null;
            }

            // 处理群消息
            if (json.has("post_type") && "message".equals(json.get("post_type").getAsString()) &&
                    json.has("message_type") && "group".equals(json.get("message_type").getAsString())) {

                processGroupMessage(json);
            }

        } catch (Exception e) {
            // 现在的报错通常是真的 JSON 格式错，而不是因为没收完
            System.out.println(">>> [Bot] 消息处理报错: " + e.getMessage());
            // 如果解析失败，也确保清空缓冲区（虽然上面已经清了，但为了保险）
            buffer.setLength(0);
        }

        return null;
    }

    private void processGroupMessage(JsonObject json) {
        long fromGroup = json.get("group_id").getAsLong();
        List<? extends Number> allowedGroups = BotConfig.SERVER.groupIds.get();

        boolean isAllowed = false;
        for (Number n : allowedGroups) {
            if (n.longValue() == fromGroup) {
                isAllowed = true;
                break;
            }
        }

        if (!isAllowed) {
            return;
        }

        String rawMsg = json.get("raw_message").getAsString();
        String senderName = json.get("sender").getAsJsonObject().get("nickname").getAsString();

        System.out.println(">>> [Bot] 收到群消息 [" + fromGroup + "] " + senderName + ": " + rawMsg);

        String cleanMsg = rawMsg.trim();
        // 拦截指令
        if ("!status".equalsIgnoreCase(cleanMsg) || "!状态".equals(cleanMsg)) {
            System.out.println(">>> [Bot] 触发状态查询指令！");
            handleStatusCommmand(fromGroup);
            return;
        }

        // 聊天转发
        if (BotConfig.SERVER.enableChatSync.get() && BottyMod.serverInstance != null) {
            String formattedMsg = "§b[QQ] §f" + senderName + ": " + rawMsg;
            BottyMod.serverInstance.execute(() ->
                    BottyMod.serverInstance.getPlayerList().broadcastSystemMessage(Component.literal(formattedMsg), false)
            );
        }
    }
    //=====================================服务器状态查询逻辑============================================================================
    private void handleStatusCommmand(long groupId) {
        if (BottyMod.serverInstance == null) {
            return;
        }

        // 获取前缀
        String prefix = BotConfig.SERVER.mcPrefix.get();

        int online = BottyMod.serverInstance.getPlayerList().getPlayerCount();
        int max = BottyMod.serverInstance.getMaxPlayers();

        // 性能数据
        double mspt = BottyMod.serverInstance.getAverageTickTime();
        String tps = String.format("%.1f", Math.min(1000.0 / mspt, 20.0));
        String msptStr = String.format("%.1f", mspt);

        // 计算 Ping 和 玩家列表
        List<String> names = new ArrayList<>();
        int totalPing = 0;
        List<ServerPlayer> players = BottyMod.serverInstance.getPlayerList().getPlayers();

        for (ServerPlayer player : players) {
            names.add(player.getName().getString());
            totalPing += player.latency;
        }

        int avgPing = players.isEmpty() ? 0 : (totalPing / players.size());
        String playerStr = names.isEmpty() ? "无" : String.join(", ", names);

        // 【修改】在最前面加上了 [%s] 来显示前缀
        String msg = String.format("[%s] 📊 服务器状态\\n👥 在线: %d/%d\\n⚡ TPS: %s (MSPT: %sms)\\n📶 延迟: %dms\\n🎮 玩家: %s",
                prefix, online, max, tps, msptStr, avgPing, playerStr);

        System.out.println(">>> [Bot] 发送状态报告: " + msg);

        String replyJson = "{\"action\":\"send_group_msg\",\"params\":{\"group_id\":" + groupId + ",\"message\":\"" + msg + "\"}}";
        BotClient.getInstance().sendRawJson(replyJson);
    }

    // 之前新增的启动消息逻辑
    private void sendStartMessage(WebSocket webSocket) {
        try {
            String template = BotConfig.SERVER.startMsgFormat.get();
            String prefix = BotConfig.SERVER.mcPrefix.get();
            List<? extends Number> groups = BotConfig.SERVER.groupIds.get();

            String msg = template.replace("%prefix%", prefix);

            for (Number groupId : groups) {
                String json = "{\"action\":\"send_group_msg\",\"params\":{\"group_id\":" + groupId + ",\"message\":\"" + msg + "\"}}";
                webSocket.sendText(json, true);
            }
            System.out.println(">>> [Bot] 已发送启动问候: " + msg);

        } catch (Exception e) {
            System.out.println(">>> [Bot] 发送启动消息失败: " + e.getMessage());
        }
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println(">>> [Bot] 连接断开 (Code: " + statusCode + ", Reason: " + reason + ")");
        BotClient.getInstance().clearWebSocket();

        if (BottyMod.serverInstance != null) {
            BottyMod.serverInstance.execute(() ->
                    BottyMod.serverInstance.getPlayerList().broadcastSystemMessage(Component.literal("§c[Bot] 连接断开！"), false)
            );
        }

        BotClient.getInstance().scheduleReconnect();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.out.println(">>> [Bot] 连接错误: " + error.getMessage());
        BotClient.getInstance().clearWebSocket();
        BotClient.getInstance().scheduleReconnect();
    }
}