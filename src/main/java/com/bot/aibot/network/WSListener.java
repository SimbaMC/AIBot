package com.bot.aibot.network;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import com.bot.aibot.BottyMod;
import com.bot.aibot.binding.QQBindingManager;
import com.bot.aibot.config.BotConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class WSListener {

    private static final Pattern CQ_PATTERN = Pattern.compile("\\[CQ:([^,\\]]+),([^\\]]*)\\]");
    private static final Queue<Runnable> SERVER_TASKS = new ConcurrentLinkedQueue<Runnable>();

    private WSListener() {}

    public static void process(String payload) {
        try {
            JsonObject json = new JsonParser().parse(payload)
                .getAsJsonObject();
            if (!json.has("post_type") || !"message".equals(
                json.get("post_type")
                    .getAsString()))
                return;
            if (!json.has("message_type") || !"group".equals(
                json.get("message_type")
                    .getAsString()))
                return;
            if (BotConfig.targetBotId != 0 && json.has("self_id")
                && json.get("self_id")
                    .getAsLong() != BotConfig.targetBotId)
                return;

            long group = json.get("group_id")
                .getAsLong();
            boolean allowed = false;
            for (long configured : BotConfig.groupIds) if (configured == group) allowed = true;
            if (!allowed) return;

            String raw = json.has("raw_message") ? json.get("raw_message")
                .getAsString()
                : json.get("message")
                    .getAsString();
            JsonObject sender = json.getAsJsonObject("sender");
            String name = getSenderName(sender);
            String clean = raw.trim();
            BottyMod.LOG.info(">>> [Bot] 群消息 [" + group + "] " + name + ": " + raw);

            if ("!status".equalsIgnoreCase(clean) || "!状态".equals(clean)) {
                handleStatusCommand();
                return;
            }
            if (clean.toLowerCase(Locale.ROOT)
                .startsWith("!bind ") && json.has("user_id")) {
                final QQBindingManager.BindingRecord record = QQBindingManager.getInstance()
                    .confirmBind(
                        clean.substring(6)
                            .trim(),
                        json.get("user_id")
                            .getAsLong(),
                        name);
                if (record == null) {
                    BotClient.getInstance()
                        .sendMessageToQQ("[绑定失败] 未找到匹配的待确认记录，请先在游戏内执行 /qqbind QQ号");
                } else {
                    BotClient.getInstance()
                        .sendMessageToQQ("[绑定成功] " + record.playerName + " 已绑定为 " + record.groupNickname);
                    SERVER_TASKS.add(new Runnable() {

                        public void run() {
                            EntityPlayerMP player = findPlayer(record.uuid);
                            if (player == null) return;
                            QQBindingManager.getInstance()
                                .applyTabPrefix(player);
                            player.addChatMessage(new ChatComponentText("§a[Bot] QQ绑定成功，已应用群昵称头衔。"));
                        }
                    });
                }
                return;
            }
            if (BotConfig.enableChatSync && BottyMod.serverInstance != null) broadcastQqMessage(name, raw);
        } catch (Exception e) {
            BottyMod.LOG.warn(">>> [Bot] OneBot 消息解析失败: " + e.getMessage());
        }
    }

    private static String getSenderName(JsonObject sender) {
        if (sender == null) return "未知";
        String card = sender.has("card") ? sender.get("card")
            .getAsString()
            .trim() : "";
        if (!card.isEmpty()) return card;
        return sender.has("nickname") ? sender.get("nickname")
            .getAsString() : "未知";
    }

    private static EntityPlayerMP findPlayer(String uuid) {
        if (BottyMod.serverInstance == null || uuid == null) return null;
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> players = BottyMod.serverInstance.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : players) if (uuid.equals(
            player.getUniqueID()
                .toString()))
            return player;
        return null;
    }

    private static void broadcastQqMessage(String senderName, String raw) {
        ChatComponentText message = new ChatComponentText("§b[QQ] §f" + senderName + ": ");
        Matcher matcher = CQ_PATTERN.matcher(raw);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) message.appendText(raw.substring(lastEnd, matcher.start()));
            String type = matcher.group(1);
            String params = matcher.group(2);
            String url = null;
            String text = matcher.group(0);
            if ("image".equals(type)) {
                url = extractValue(params, "url");
                text = "§b[📷图片]§r";
            } else if ("face".equals(type)) {
                String id = extractValue(params, "id");
                if (id != null) {
                    url = formatFaceUrl(id);
                    text = "§e[😀表情]§r";
                }
            }
            if (url == null || url.isEmpty()) message.appendText(matcher.group(0));
            else {
                ChatComponentText link = new ChatComponentText(text);
                link.setChatStyle(
                    new ChatStyle().setColor("face".equals(type) ? EnumChatFormatting.GOLD : EnumChatFormatting.AQUA)
                        .setUnderlined(Boolean.TRUE)
                        .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                        .setChatHoverEvent(
                            new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText("§7点击打开图片"))));
                message.appendSibling(link);
            }
            lastEnd = matcher.end();
        }
        if (lastEnd < raw.length()) message.appendText(raw.substring(lastEnd));
        BottyMod.serverInstance.getConfigurationManager()
            .sendChatMsg(message);
    }

    private static void handleStatusCommand() {
        final MinecraftServer server = BottyMod.serverInstance;
        if (server == null) return;
        SERVER_TASKS.add(new Runnable() {

            public void run() {
                try {
                    @SuppressWarnings("unchecked")
                    List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
                    Map<String, String> mappings = parseNodeMappings();
                    List<String> details = new ArrayList<String>();
                    int totalPing = 0;
                    for (EntityPlayerMP player : players) {
                        totalPing += player.ping;
                        boolean op = server.getConfigurationManager()
                            .func_152596_g(player.getGameProfile());
                        String node = resolveNode(player, mappings);
                        details.add(
                            (op ? "🛡️ " : "") + player
                                .getCommandSenderName() + " [" + node + "] (" + player.ping + "ms)");
                    }
                    double mspt = average(server.tickTimeArray) / 1000000.0D;
                    double tps = mspt <= 0.0D ? 20.0D : Math.min(1000.0D / mspt, 20.0D);
                    int avgPing = players.isEmpty() ? 0 : totalPing / players.size();
                    StringBuilder playerText = new StringBuilder();
                    if (details.isEmpty()) playerText.append("无");
                    else for (String detail : details) playerText.append("\n● ")
                        .append(detail);
                    String message = String.format(
                        Locale.ROOT,
                        "[%s] 📊 服务器状态\n👥 在线: %d/%d\n⚡ TPS: %.1f (MSPT: %.1fms)\n📶 平均延迟: %dms\n\n🎮 在线玩家 : %s",
                        BotConfig.mcPrefix,
                        players.size(),
                        server.getMaxPlayers(),
                        tps,
                        mspt,
                        avgPing,
                        playerText.toString());
                    BotClient.getInstance()
                        .sendMessageToQQ(message);
                } catch (Exception e) {
                    BottyMod.LOG.error(">>> [Bot] 状态指令执行异常", e);
                }
            }
        });
    }

    public static void processServerTasks() {
        Runnable task;
        while ((task = SERVER_TASKS.poll()) != null) task.run();
    }

    private static Map<String, String> parseNodeMappings() {
        Map<String, String> result = new HashMap<String, String>();
        for (String mapping : BotConfig.nodeMappings) {
            int separator = mapping.indexOf(':');
            if (separator > 0) result.put(
                mapping.substring(0, separator)
                    .trim(),
                mapping.substring(separator + 1)
                    .trim());
        }
        return result;
    }

    private static String resolveNode(EntityPlayerMP player, Map<String, String> mappings) {
        try {
            SocketAddress address = player.playerNetServerHandler.netManager.getSocketAddress();
            String value = address == null ? "" : address.toString();
            if (value.startsWith("/")) value = value.substring(1);
            int colon = value.lastIndexOf(':');
            String ip = colon > 0 ? value.substring(0, colon) : value;
            String node = mappings.get(ip);
            return node == null ? BotConfig.defaultNodeName : node;
        } catch (Exception ignored) {
            return BotConfig.defaultNodeName;
        }
    }

    private static double average(long[] values) {
        if (values == null || values.length == 0) return 0.0D;
        long total = 0L;
        for (long value : values) total += value;
        return (double) total / values.length;
    }

    public static void runServerTasks() {
        Runnable task;
        while ((task = SERVER_TASKS.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                BottyMod.LOG.error(">>> [Bot] 主线程任务执行异常", e);
            }
        }
    }

    private static String extractValue(String params, String key) {
        Matcher matcher = Pattern.compile(key + "=([^,\\]\\s]+)")
            .matcher(params);
        return matcher.find() ? matcher.group(1)
            .trim() : null;
    }

    private static String formatFaceUrl(String id) {
        try {
            return String.format(BotConfig.qqFaceApi, id, id);
        } catch (Exception ignored) {
            return String.format(BotConfig.qqFaceApi, id);
        }
    }
}
