package com.bot.aibot.network;

import com.bot.aibot.BottyMod;
import com.bot.aibot.config.BotConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WSListener implements WebSocket.Listener {

    // 用来暂存分片消息的缓冲区
    private final StringBuilder buffer = new StringBuilder();

    // 【升级】同时匹配 image 和 face，且能兼容乱七八糟的参数
    // Group 1: 类型 (image/face)
    // Group 2: 参数串 (file=xxx,url=xxx,id=xxx)
    private static final Pattern CQ_PATTERN = Pattern.compile("\\[CQ:(image|face),(.*?)\\]");

    // 内存熔断阈值
    private static final int MAX_BUFFER_SIZE = 1024 * 1024;

    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println(">>> [Bot] 连接成功！等待消息...");
        webSocket.request(1);

        // 1. 游戏内广播
        if (BottyMod.serverInstance != null) {
            BottyMod.serverInstance.execute(() ->
                    BottyMod.serverInstance.getPlayerList().broadcastSystemMessage(Component.literal("§a[Bot] 连接成功！"), false)
            );
        }

        // 2. 向 QQ 群发送启动问候
        sendStartMessage(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        webSocket.request(1);

        // --- 保险丝机制 ---
        if (buffer.length() + data.length() > MAX_BUFFER_SIZE) {
            System.out.println(">>> [Bot] ⚠️ 警告：检测到异常过大的消息 (>1MB)，已自动拦截并丢弃！");
            buffer.setLength(0);
            return null;
        }

        buffer.append(data);

        if (!last) {
            return null;
        }

        String fullMessage = buffer.toString();
        // 清空缓冲区
        if (buffer.capacity() > MAX_BUFFER_SIZE) {
            buffer.setLength(0);
            buffer.trimToSize();
        } else {
            buffer.setLength(0);
        }

        try {
            JsonObject json = JsonParser.parseString(fullMessage).getAsJsonObject();

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
            System.out.println(">>> [Bot] 消息处理报错: " + e.getMessage());
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

        if (!isAllowed) return;

        String rawMsg = "";
        if (json.has("raw_message")) rawMsg = json.get("raw_message").getAsString();
        else if (json.has("message")) rawMsg = json.get("message").getAsString();

        String senderName = "未知";
        if (json.has("sender") && json.get("sender").isJsonObject()) {
            senderName = json.get("sender").getAsJsonObject().get("nickname").getAsString();
        }

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
            final String finalSenderName = senderName;
            final String finalRawMsg = rawMsg;

            BottyMod.serverInstance.execute(() -> {
                MutableComponent messageComponent = Component.literal("§b[QQ] §f" + finalSenderName + ": ");

                // --- 使用升级后的正则进行匹配 ---
                Matcher matcher = CQ_PATTERN.matcher(finalRawMsg);
                int lastEnd = 0;

                while (matcher.find()) {
                    // 1. 添加前面的文本
                    String textBefore = finalRawMsg.substring(lastEnd, matcher.start());
                    if (!textBefore.isEmpty()) {
                        messageComponent.append(Component.literal(textBefore));
                    }

                    // 2. 判断类型
                    String type = matcher.group(1);   // image 或 face
                    String params = matcher.group(2); // url=...,id=...

                    String targetUrl = null;
                    String displayText = "";
                    int color = 0x00AAAA; // 默认青色

                    if ("image".equals(type)) {
                        targetUrl = extractValue(params, "url");
                        displayText = "§b[📷图片]§r";
                    } else if ("face".equals(type)) {
                        String faceId = extractValue(params, "id");
                        if (faceId != null) {
                            // 从配置文件获取表情包下载源
                            String template = BotConfig.SERVER.qqFaceApi.get();
                            targetUrl = String.format(template, faceId);
                            displayText = "§e[😀表情]§r"; // 黄色
                            color = 0xFFAA00;
                        }
                    }

                    // 3. 生成组件
                    if (targetUrl != null && !targetUrl.isEmpty()) {
                        MutableComponent linkBtn = Component.literal(displayText);
                        linkBtn.setStyle(Style.EMPTY
                                .withColor(color)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, targetUrl))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§7点击放大 / 悬停预览")))
                        );
                        messageComponent.append(linkBtn);
                    } else {
                        // 解析失败或无URL，显示原始 CQ 码
                        messageComponent.append(Component.literal(matcher.group(0)));
                    }

                    lastEnd = matcher.end();
                }

                // 4. 添加剩余文本
                String textAfter = finalRawMsg.substring(lastEnd);
                if (!textAfter.isEmpty()) {
                    messageComponent.append(Component.literal(textAfter));
                }

                BottyMod.serverInstance.getPlayerList().broadcastSystemMessage(messageComponent, false);
            });
        }
    }

    // --- 辅助方法：从参数串中提取值 ---
    private String extractValue(String params, String key) {
        try {
            // 匹配 key=value，值到逗号或结尾结束
            Pattern p = Pattern.compile(key + "=([^,\\]]+)");
            Matcher m = p.matcher(params);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {}
        return null;
    }

    // --- 服务器状态查询逻辑 (保持不变) ---
    private void handleStatusCommmand(long groupId) {
        if (BottyMod.serverInstance == null) return;

        String prefix = BotConfig.SERVER.mcPrefix.get();
        int online = BottyMod.serverInstance.getPlayerList().getPlayerCount();
        int max = BottyMod.serverInstance.getMaxPlayers();
        double mspt = BottyMod.serverInstance.getAverageTickTime();
        String tps = String.format("%.1f", Math.min(1000.0 / mspt, 20.0));
        String msptStr = String.format("%.1f", mspt);

        List<String> names = new ArrayList<>();
        int totalPing = 0;
        List<ServerPlayer> players = BottyMod.serverInstance.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            names.add(player.getName().getString());
            totalPing += player.latency;
        }
        int avgPing = players.isEmpty() ? 0 : (totalPing / players.size());
        String playerStr = names.isEmpty() ? "无" : String.join(", ", names);

        String msg = String.format("[%s] 📊 服务器状态\\n👥 在线: %d/%d\\n⚡ TPS: %s (MSPT: %sms)\\n📶 延迟: %dms\\n🎮 玩家: %s",
                prefix, online, max, tps, msptStr, avgPing, playerStr);

        System.out.println(">>> [Bot] 发送状态报告: " + msg);
        String replyJson = "{\"action\":\"send_group_msg\",\"params\":{\"group_id\":" + groupId + ",\"message\":\"" + msg + "\"}}";
        BotClient.getInstance().sendRawJson(replyJson);
    }

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
}