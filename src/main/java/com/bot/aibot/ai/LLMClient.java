package com.bot.aibot.ai;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.events.MinecraftEvents;
import com.bot.aibot.network.BotClient;
import com.bot.aibot.utils.ChineseUtils;
import com.bot.aibot.utils.HttpUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class LLMClient {

    private LLMClient() {}

    public static void chat(final EntityPlayerMP player, final String question) {
        player.addChatMessage(new ChatComponentText("§7[Bot] Thinking..."));
        request(BotConfig.aiPrompt, question, new Callback() {

            public void done(final String reply) {
                player.addChatMessage(new ChatComponentText("[Bot] " + reply));
            }
        });
    }

    public static void translateDeath(final EntityPlayerMP player, final String abstractMessage) {
        request(BotConfig.aiDeathPrompt, abstractMessage, new Callback() {

            public void done(String translated) {
                ChineseUtils.learn(abstractMessage, translated);
                BotClient.getInstance()
                    .sendMessageToQQ(
                        MinecraftEvents.formatMsg(
                            BotConfig.deathMsgFormat,
                            player.getCommandSenderName(),
                            translated.replace("%s", player.getCommandSenderName())));
            }
        });
    }

    private static void request(final String system, final String user, final Callback callback) {
        new Thread(new Runnable() {

            public void run() {
                try {
                    JsonObject body = new JsonObject();
                    body.addProperty("model", BotConfig.aiModelName);
                    JsonArray messages = new JsonArray();
                    JsonObject s = new JsonObject();
                    s.addProperty("role", "system");
                    s.addProperty("content", system);
                    messages.add(s);
                    JsonObject u = new JsonObject();
                    u.addProperty("role", "user");
                    u.addProperty("content", user);
                    messages.add(u);
                    body.add("messages", messages);
                    Map<String, String> headers = new HashMap<String, String>();
                    headers.put("Authorization", "Bearer " + BotConfig.aiApiKey);
                    HttpUtils.Response response = HttpUtils
                        .request(BotConfig.aiApiUrl, "POST", body.toString(), headers);
                    if (response.status == 200) callback.done(
                        new JsonParser().parse(response.body)
                            .getAsJsonObject()
                            .getAsJsonArray("choices")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content")
                            .getAsString());
                } catch (Exception e) {
                    playerError(e);
                }
            }
        }, "AiBot-LLM").start();
    }

    private static void playerError(Exception e) {
        System.err.println("[AiBot] AI request failed: " + e.getMessage());
    }

    private interface Callback {

        void done(String value);
    }
}
