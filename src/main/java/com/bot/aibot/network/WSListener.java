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
    // 匹配 [CQ:image...] 或 [CQ:face...]
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

        String senderName = "未知";
        if (json.has("sender") && json.get("sender").isJsonObject()) {
            senderName = json.get("sender").getAsJsonObject().get("nickname").getAsString();
        }

        System.out.println(">>> [Bot] 群消息 [" + fromGroup + "] " + senderName + ": " + rawMsg);

        String cleanMsg = rawMsg.trim();
        if ("!status".equalsIgnoreCase(cleanMsg) || "!状态".equals(cleanMsg)) {
            handleStatusCommmand(fromGroup);
            return;
        }

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
                        // 如果没提取到 id，可能是参数太乱，尝试兜底解析
                        if (faceId == null) faceId = extractValueSimple(params, "id");

                        if (faceId != null) {
                            String template = BotConfig.SERVER.qqFaceApi.get();
                            try {
                                // 【关键修复】传入两次 faceId，兼容 "%s/png/%s.png" 这种需要填两个坑的格式
                                // 如果模板里只有一个 %s，Java 会自动忽略多余的参数，不会报错
                                targetUrl = String.format(template, faceId, faceId);

                                // 打印调试日志，让你知道最终请求的是啥
                                System.out.println(">>> [Bot] 生成表情链接: " + targetUrl);
                            } catch (Exception e) {
                                System.err.println(">>> [Bot] URL 格式化失败: " + e.getMessage());
                            }
                            displayText = "§e[😀表情]§r";
                            color = 0xFFAA00;
                        } else {
                            System.err.println(">>> [Bot] 表情 ID 提取失败，参数: " + params);
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

    // 增强版参数提取
    private String extractValue(String params, String key) {
        try {
            // 匹配 key=value
            // 排除 , ] 和空白字符，确保提取干净的 ID
            Pattern p = Pattern.compile(key + "=([^,\\]\\s]+)");
            Matcher m = p.matcher(params);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception e) {}
        return null;
    }

    // 简单粗暴提取 (兜底)
    private String extractValueSimple(String params, String key) {
        // 如果正则挂了，手动切字符串
        String[] parts = params.split(",");
        for (String part : parts) {
            if (part.trim().startsWith(key + "=")) {
                return part.split("=")[1].replace("]", "").trim();
            }
        }
        return null;
    }

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
        BotClient.getInstance().sendRawJson("{\"action\":\"send_group_msg\",\"params\":{\"group_id\":" + groupId + ",\"message\":\"" + msg + "\"}}");
    }

    private void sendStartMessage(WebSocket webSocket) {
        try {
            String template = BotConfig.SERVER.startMsgFormat.get();
            String prefix = BotConfig.SERVER.mcPrefix.get();
            List<? extends Number> groups = BotConfig.SERVER.groupIds.get();
            String msg = template.replace("%prefix%", prefix);
            for (Number groupId : groups) {
                webSocket.sendText("{\"action\":\"send_group_msg\",\"params\":{\"group_id\":" + groupId + ",\"message\":\"" + msg + "\"}}", true);
            }
        } catch (Exception e) {
            System.out.println(">>> [Bot] 发送启动消息失败: " + e.getMessage());
        }
    }
}