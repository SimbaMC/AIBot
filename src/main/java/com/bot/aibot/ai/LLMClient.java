package com.bot.aibot.ai;

import com.bot.aibot.BottyMod;
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.events.MinecraftEvents;
import com.bot.aibot.network.BotClient;
import com.bot.aibot.utils.ChineseUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LLMClient {

    private static final HttpClient client = HttpClient.newHttpClient();

    /**
     * 普通聊天功能
     */
    public static void chat(ServerPlayer player, String userMessage) {
        if (!BotConfig.SERVER.enableAI.get()) return;

        String playerName = player.getName().getString();
        player.sendSystemMessage(Component.literal("§7[Bot] 正在思考..."));

        String systemPrompt = BotConfig.SERVER.aiPrompt.get();
        String userContent = "玩家[" + playerName + "]说: " + userMessage;

        // 调用统一的发送方法
        sendRequest(systemPrompt, userContent, 0.7, aiReply -> {
            // 回调：私聊发给玩家
            replyToPlayer(player, aiReply);
        }, errorMsg -> {
            // 错误回调
            sendErrorToPlayer(player, errorMsg);
        });
    }

    /**
     * 死亡播报翻译功能
     */
    public static void translateDeath(ServerPlayer player, String abstractKey) {
        String mode = BotConfig.SERVER.aiDeathMode.get();
        if (!BotConfig.SERVER.enableAI.get() || "OFF".equals(mode)) return;

        String systemPrompt = BotConfig.SERVER.aiDeathPrompt.get();
        String playerName = player.getName().getString();

        // 【核心修改】Prompt 升级
        // 明确告诉 AI 保持格式
        String promptWithContext = systemPrompt +
                "\n\n[重要指令]：\n" +
                "1. 原始消息中包含了 '%s'，这代表玩家的名字。\n" +
                "2. 请在你的翻译结果中**保留这个 '%s'**，放在合适的位置。\n" +
                "3. 你的任务是翻译死法并进行嘲讽，但不要把 '%s' 替换成具体名字，也不要弄丢它。\n" +
                "4. 只要输出翻译后的句子，不要其他废话。";

        // 调用统一发送逻辑 (传入的是 abstractKey, 如 "%s was slain by zombie")
        sendRequest(promptWithContext, abstractKey, 0.8, translatedTemplate -> {

            // 回调逻辑：

            // 1. 存入缓存 (存模板: "%s was slain..." -> "%s 被僵尸干碎了...")
            ChineseUtils.learn(abstractKey, translatedTemplate);

            // 2. 还原回具体消息用于发送
            // 将模板里的 %s 变回 Dev
            String realMsg = translatedTemplate.replace("%s", playerName);

            // 3. 格式化并广播
            String template = BotConfig.SERVER.deathMsgFormat.get();
            String finalMsg = MinecraftEvents.formatMsg(template, playerName, realMsg);
            BotClient.getInstance().sendMessageToQQ(finalMsg);

        }, errorMsg -> {
            System.out.println(">>> [Bot AI] 死亡翻译失败: " + errorMsg);
        });
    }

    /**
     * 【重构优化】统一的 HTTP 请求发送逻辑
     * @param sysPrompt 系统提示词
     * @param userMsg 用户输入
     * @param temperature 温度参数
     * @param onSuccess 成功时的回调 (Lambda)
     * @param onError 失败时的回调 (Lambda)
     */
    private static void sendRequest(String sysPrompt, String userMsg, double temperature, Consumer<String> onSuccess, Consumer<String> onError) {
        String apiUrl = BotConfig.SERVER.aiApiUrl.get();
        String apiKey = BotConfig.SERVER.aiApiKey.get();
        String model = BotConfig.SERVER.aiModelName.get();

        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("model", model);
        jsonBody.addProperty("temperature", temperature);

        JsonArray messages = new JsonArray();
        JsonObject sysObj = new JsonObject();
        sysObj.addProperty("role", "system");
        sysObj.addProperty("content", sysPrompt);
        messages.add(sysObj);

        JsonObject userObj = new JsonObject();
        userObj.addProperty("role", "user");
        userObj.addProperty("content", userMsg);
        messages.add(userObj);

        jsonBody.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                .build();

        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
                    String content = responseJson.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();

                    // 成功回调
                    onSuccess.accept(content);
                } else {
                    onError.accept("HTTP " + response.statusCode());
                }
            } catch (Exception e) {
                onError.accept(e.getMessage());
            }
        });
    }

    private static void replyToPlayer(ServerPlayer player, String message) {
        if (BottyMod.serverInstance != null) {
            BottyMod.serverInstance.execute(() -> player.sendSystemMessage(Component.literal("§a[Bot] " + message)));
        }
    }

    private static void sendErrorToPlayer(ServerPlayer player, String error) {
        if (BottyMod.serverInstance != null) {
            BottyMod.serverInstance.execute(() -> player.sendSystemMessage(Component.literal("§c[Bot] " + error)));
        }
    }
}