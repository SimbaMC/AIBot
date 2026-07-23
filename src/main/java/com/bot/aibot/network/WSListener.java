package com.bot.aibot.network;

import net.minecraft.util.ChatComponentText;

import com.bot.aibot.BottyMod;
import com.bot.aibot.binding.QQBindingManager;
import com.bot.aibot.config.BotConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class WSListener {

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
            String name = sender != null && sender.has("card")
                && !sender.get("card")
                    .getAsString()
                    .isEmpty() ? sender.get("card")
                        .getAsString()
                        : sender != null && sender.has("nickname") ? sender.get("nickname")
                            .getAsString() : "QQ";
            if (raw.toLowerCase()
                .startsWith("!bind ") && json.has("user_id")) {
                QQBindingManager.getInstance()
                    .confirmBind(
                        raw.substring(6)
                            .trim(),
                        json.get("user_id")
                            .getAsLong(),
                        name);
                return;
            }
            if ("!status".equalsIgnoreCase(raw) || "!状态".equals(raw)) {
                int online = BottyMod.serverInstance == null ? 0 : BottyMod.serverInstance.getCurrentPlayerCount();
                int max = BottyMod.serverInstance == null ? 0 : BottyMod.serverInstance.getMaxPlayers();
                BotClient.getInstance()
                    .sendMessageToQQ("[" + BotConfig.mcPrefix + "] online: " + online + "/" + max);
                return;
            }
            if (BotConfig.enableChatSync && BottyMod.serverInstance != null)
                BottyMod.serverInstance.getConfigurationManager()
                    .sendChatMsg(new ChatComponentText("§b[QQ] §f" + name + ": " + simplifyCq(raw)));
        } catch (Exception e) {
            BottyMod.LOG.warn("Invalid OneBot payload: " + e.getMessage());
        }
    }

    private static String simplifyCq(String text) {
        return text.replaceAll("\\[CQ:image,[^]]*]", "§b[image]§r")
            .replaceAll("\\[CQ:face,[^]]*]", "§e[face]§r");
    }
}
