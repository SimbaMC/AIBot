package com.bot.aibot.network;

import com.bot.aibot.BottyMod;
import com.bot.aibot.binding.QQBindingManager;
import com.bot.aibot.config.BotConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

        BotClient.getInstance().onConnected();

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
            JsonObject sender = json.get("sender").getAsJsonObject();
            String card = sender.has("card") ? sender.get("card").getAsString().trim() : "";
            if (!card.isEmpty()) {
                senderName = card;
            } else if (sender.has("nickname")) {
                senderName = sender.get("nickname").getAsString();
            }
        }

        System.out.println(">>> [Bot] 群消息 [" + fromGroup + "] " + senderName + ": " + rawMsg);
        String cleanMsg = rawMsg.trim();

        if ("!status".equalsIgnoreCase(cleanMsg) || "!状态".equals(cleanMsg)) {
            handleStatusCommand(fromGroup);
            return;
        }
        if (cleanMsg.toLowerCase().startsWith("!bind ")) {
            long senderQq = json.has("user_id") ? json.get("user_id").getAsLong() : 0L;
            if (senderQq != 0L) {
                String playerName = cleanMsg.substring("!bind ".length()).trim();
                QQBindingManager.getInstance().confirmBind(playerName, senderQq, senderName).ifPresentOrElse(record -> {
                    BotClient.getInstance().sendMessageToQQ(String.format(
                            "[绑定成功] %s 已绑定为 %s", record.playerName, record.groupNickname));
                    if (BottyMod.serverInstance != null) {
                        BottyMod.serverInstance.execute(() -> {
                            try {
                                var target = BottyMod.serverInstance.getPlayerList().getPlayer(java.util.UUID.fromString(record.uuid));
                                if (target != null) {
                                    QQBindingManager.getInstance().applyTabPrefix(target);
                                    target.sendSystemMessage(Component.literal("§a[Bot] QQ绑定成功，已应用群昵称头衔。"));
                                }
                            } catch (IllegalArgumentException e) {
                                System.err.println(">>> [Bot] 绑定记录 UUID 异常: " + record.uuid);
                            }
                        });
                    }
                }, () -> BotClient.getInstance().sendMessageToQQ("[绑定失败] 未找到匹配的待确认记录，请先在游戏内执行 /qqbind QQ号"));
            }
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
                        if (faceId == null) faceId = extractValueSimple(params, "id");

                        if (faceId != null) {
                            String template = BotConfig.SERVER.qqFaceApi.get();
                            try {
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

    private void handleStatusCommand(long groupId) {
        if (com.bot.aibot.BottyMod.serverInstance == null) return;

        com.bot.aibot.BottyMod.serverInstance.execute(() -> {
            try {
                String prefix = com.bot.aibot.config.BotConfig.SERVER.mcPrefix.get();
                int online = com.bot.aibot.BottyMod.serverInstance.getPlayerList().getPlayerCount();
                int max = com.bot.aibot.BottyMod.serverInstance.getMaxPlayers();

                java.util.Map<String, String> ipMap = new java.util.HashMap<>();
                java.util.List<? extends String> configMappings = com.bot.aibot.config.BotConfig.SERVER.nodeMappings.get();
                for (String s : configMappings) {
                    String[] parts = s.split(":", 2);
                    if (parts.length == 2) {
                        ipMap.put(parts[0].trim(), parts[1].trim());
                    }
                }
                String defaultNode = com.bot.aibot.config.BotConfig.SERVER.defaultNodeName.get();

                double mspt = com.bot.aibot.BottyMod.serverInstance.getAverageTickTime();
                String tpsStr = String.format("%.1f", Math.min(1000.0 / mspt, 20.0));
                String msptStr = String.format("%.1f", mspt);

                java.util.List<net.minecraft.server.level.ServerPlayer> players = com.bot.aibot.BottyMod.serverInstance.getPlayerList().getPlayers();
                java.util.List<String> playerDetails = new java.util.ArrayList<>();
                int totalPing = 0;

                for (net.minecraft.server.level.ServerPlayer player : players) {
                    String name = player.getName().getString();
                    int ping = player.latency;
                    totalPing += ping;

                    boolean isOp = player.hasPermissions(4);
                    String opSymbol = isOp ? "🛡️ " : "";

                    String nodeName = defaultNode;
                    try {
                        String fullAddress = player.connection.connection.getRemoteAddress().toString();
                        if (fullAddress.startsWith("/")) {
                            fullAddress = fullAddress.substring(1);
                        }
                        String ipOnly = fullAddress.split(":")[0];

                        nodeName = ipMap.getOrDefault(ipOnly, defaultNode);
                    } catch (Exception e) {
                    }

                    playerDetails.add(opSymbol + name + " [" + nodeName + "] (" + ping + "ms)");
                }

                int avgPing = players.isEmpty() ? 0 : (totalPing / players.size());

                StringBuilder playerListBuilder = new StringBuilder();
                if (playerDetails.isEmpty()) {
                    playerListBuilder.append("无");
                } else {
                    for (String detail : playerDetails) {
                        playerListBuilder.append("\n● ").append(detail);
                    }
                }
                String playerStr = playerListBuilder.toString();

                String msg = String.format("[%s] 📊 服务器状态\n👥 在线: %d/%d\n⚡ TPS: %s (MSPT: %sms)\n📶 平均延迟: %dms\n\n🎮 在线玩家 : %s",
                        prefix, online, max, tpsStr, msptStr, avgPing, playerStr);

                com.bot.aibot.network.BotClient.getInstance().sendMessageToQQ(msg);

            } catch (Exception e) {
                System.err.println(">>> [Bot] 状态指令执行异常: " + e.getMessage());
                e.printStackTrace();
            }
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
        BotClient client = BotClient.getInstance();
        System.out.println(">>> [Bot] onClose 回调 (Code: " + statusCode + ", Reason: " + reason + ")");
        boolean activeDisconnected = client.onDisconnect(webSocket, "Code=" + statusCode + ", Reason=" + reason);
        if (!activeDisconnected) {
            return CompletableFuture.completedFuture(null);
        }
        if (BottyMod.serverInstance != null && !client.isIntentionalClose()) {
            BottyMod.serverInstance.execute(() ->
                    BottyMod.serverInstance.getPlayerList().broadcastSystemMessage(Component.literal("§c[Bot] 连接断开！"), false)
            );
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        BotClient client = BotClient.getInstance();
        String err = error == null ? "unknown" : error.getMessage();
        System.out.println(">>> [Bot] onError 回调: " + err);
        boolean activeDisconnected = client.onDisconnect(webSocket, "onError: " + err);
        if (activeDisconnected && BottyMod.serverInstance != null && !client.isIntentionalClose()) {
            BottyMod.serverInstance.execute(() ->
                    BottyMod.serverInstance.getPlayerList().broadcastSystemMessage(Component.literal("§c[Bot] 连接异常，已触发重连。"), false)
            );
        }
    }
}
