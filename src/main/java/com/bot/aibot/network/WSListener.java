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

    private final StringBuilder buffer = new StringBuilder();
    private static final Pattern CQ_PATTERN = Pattern.compile("\\[CQ:(image|face),(.*?)\\]");
    private static final int MAX_BUFFER_SIZE = 1024 * 1024;

    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println(">>> [Bot] 连接成功！等待消息...");
        webSocket.request(1);
        if (BottyMod.serverInstance != null) {
            BottyMod.serverInstance.execute(() ->
                    BottyMod.serverInstance.getPlayerList().broadcastSystemMessage(Component.literal("§a[Bot] 连接成功！"), false)
            );
        }
        sendStartMessage(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        webSocket.request(1);

        if (buffer.length() + data.length() > MAX_BUFFER_SIZE) {
            buffer.setLength(0);
            return null;
        }
        buffer.append(data);
        if (!last) return null;

        String fullMessage = buffer.toString();
        if (buffer.capacity() > MAX_BUFFER_SIZE) {
            buffer.setLength(0);
            buffer.trimToSize();
        } else {
            buffer.setLength(0);
        }

        try {
            JsonObject json = JsonParser.parseString(fullMessage).getAsJsonObject();
            long configBotId = BotConfig.SERVER.targetBotId.get();
            if (configBotId != 0 && json.has("self_id") && json.get("self_id").getAsLong() != configBotId) {
                return null;
            }
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

        // 提取发送者信息
        String senderName = "未知";
        if (json.has("sender") && json.get("sender").isJsonObject()) {
            JsonObject sender = json.get("sender").getAsJsonObject();
            senderName = sender.get("nickname").getAsString();
        }

        System.out.println(">>> [Bot] 群消息 [" + fromGroup + "] " + senderName + ": " + rawMsg);
        String cleanMsg = rawMsg.trim();

        // ==================== 指令区 ====================

        // 1. 状态查询 (!status) - 【保留】
        if ("!status".equalsIgnoreCase(cleanMsg) || "!状态".equals(cleanMsg)) {
            handleStatusCommmand(fromGroup);
            return;
        }

        // (!load 和 !bind 已移除)

        // ==================== 转发区 ====================

        if (BotConfig.SERVER.enableChatSync.get() && BottyMod.serverInstance != null) {
            final String finalSenderName = senderName;
            final String finalRawMsg = rawMsg;

            BottyMod.serverInstance.execute(() -> {
                MutableComponent messageComponent = Component.literal("§b[QQ] §f" + finalSenderName + ": ");
                Matcher matcher = CQ_PATTERN.matcher(finalRawMsg);
                int lastEnd = 0;

                while (matcher.find()) {
                    String textBefore = finalRawMsg.substring(lastEnd, matcher.start());
                    if (!textBefore.isEmpty()) messageComponent.append(Component.literal(textBefore));

                    String type = matcher.group(1);
                    String params = matcher.group(2);

                    String targetUrl = null;
                    String displayText = "";
                    int color = 0x00AAAA;

                    if ("image".equals(type)) {
                        targetUrl = extractValue(params, "url");
                        displayText = "§b[📷图片]§r";
                    } else if ("face".equals(type)) {
                        String faceId = extractValue(params, "id");
                        if (faceId == null) faceId = extractValueSimple(params, "id");

                        if (faceId != null) {
                            String template = BotConfig.SERVER.qqFaceApi.get();
                            try {
                                // 兼容双 %s 格式
                                targetUrl = String.format(template, faceId, faceId);
                            } catch (Exception e) {
                                targetUrl = String.format(template, faceId);
                            }
                            displayText = "§e[😀表情]§r";
                            color = 0xFFAA00;
                        }
                    }

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
                        messageComponent.append(Component.literal(matcher.group(0)));
                    }
                    lastEnd = matcher.end();
                }

                String textAfter = finalRawMsg.substring(lastEnd);
                if (!textAfter.isEmpty()) messageComponent.append(Component.literal(textAfter));

                BottyMod.serverInstance.getPlayerList().broadcastSystemMessage(messageComponent, false);
            });
        }
    }

    // --- 指令处理方法 ---

    private void handleStatusCommmand(long groupId) {
        if (BottyMod.serverInstance == null) return;

        // 放入主线程执行，防止并发异常
        BottyMod.serverInstance.execute(() -> {
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

            // 构造消息内容
            String msg = String.format("[%s] 📊 服务器状态\n👥 在线: %d/%d\n⚡ TPS: %s (MSPT: %sms)\n📶 延迟: %dms\n🎮 玩家: %s",
                    prefix, online, max, tps, msptStr, avgPing, playerStr);

            // 使用 sendMessageToQQ 发送
            BotClient.getInstance().sendMessageToQQ(msg);
        });
    }

    private String extractValue(String params, String key) {
        try {
            Pattern p = Pattern.compile(key + "=([^,\\]\\s]+)");
            Matcher m = p.matcher(params);
            if (m.find()) return m.group(1).trim();
        } catch (Exception e) {}
        return null;
    }

    private String extractValueSimple(String params, String key) {
        String[] parts = params.split(",");
        for (String part : parts) {
            if (part.trim().startsWith(key + "=")) {
                return part.split("=")[1].replace("]", "").trim();
            }
        }
        return null;
    }

    private void sendStartMessage(WebSocket webSocket) {
        try {
            String template = BotConfig.SERVER.startMsgFormat.get();
            String prefix = BotConfig.SERVER.mcPrefix.get();
            List<? extends Number> groups = BotConfig.SERVER.groupIds.get();
            String msg = template.replace("%prefix%", prefix);
            for (Number groupId : groups) {
                JsonObject params = new JsonObject();
                params.addProperty("group_id", groupId);
                params.addProperty("message", msg);
                JsonObject root = new JsonObject();
                root.addProperty("action", "send_group_msg");
                root.add("params", params);
                webSocket.sendText(root.toString(), true);
            }
        } catch (Exception e) {
            System.out.println(">>> [Bot] 发送启动消息失败: " + e.getMessage());
        }
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println(">>> [Bot] WebSocket 关闭: " + reason);
        BotClient.getInstance().onDisconnect();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println(">>> [Bot] WebSocket 错误: " + error.getMessage());
        BotClient.getInstance().onDisconnect();
    }
}