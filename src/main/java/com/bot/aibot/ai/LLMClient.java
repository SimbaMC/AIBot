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
     * 普通聊天功能（整合 AI 智能点歌）
     */
    public static void chat(ServerPlayer player, String userMessage) {
        if (!BotConfig.SERVER.enableAI.get()) return;

        String playerName = player.getName().getString();
        player.sendSystemMessage(Component.literal("§7[Bot] 正在思考..."));

        String basePrompt = BotConfig.SERVER.aiPrompt.get();

        // 动态拼接功能指令：强制 AI 补全歌手名以提高搜索精度
        String systemPrompt = basePrompt +
                "\n\n[核心功能指令]：" +
                "\n- 如果玩家想点歌，请在回复末尾添加 'PLAY: 歌曲关键词'（关键词可以是歌名，也可以是歌手+歌名，由你判断如何搜索最准）。" +
                "\n- 如果玩家想停止播放、太吵了、换一首或不想听了，请在回复末尾添加 'STOP: TRUE'。" +
                "\n- 严禁在 PLAY: 或 STOP: 后添加任何引号、括号或书名号。" +
                "\n- 保持你的个性化语气，指令行必须独立存在或位于回复最后。";

        String userContent = "玩家[" + playerName + "]说: " + userMessage;

        sendRequest(systemPrompt, userContent, 0.7, aiReply -> {
            // --- DEBUG: 查看原始回复 ---
            System.out.println(">>> [LLM DEBUG] 收到 AI 回复内容:\n" + aiReply);

            if (aiReply.contains("STOP: TRUE")) {
                System.out.println(">>> [LLM DEBUG] 触发停止逻辑");
                handleStopMusic(player);
            }

            // 解析逻辑升级：处理 AI 不换行或带干扰符的情况
            String[] lines = aiReply.split("\n");
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.contains("PLAY:")) {
                    // 1. 定位指令起始位置并截取关键词
                    int playIndex = trimmedLine.indexOf("PLAY:");
                    String rawKeyword = trimmedLine.substring(playIndex + 5).trim();

                    // 2. 暴力清洗搜索词：去掉所有干扰 API 搜索的符号
                    String keyword = rawKeyword.replace("\"", "").replace("'", "")
                            .replace("《", "").replace("》", "")
                            .replace("“", "").replace("”", "").trim();

                    System.out.println(">>> [LLM DEBUG] 命中点歌指令，提取搜索词: [" + keyword + "]");

                    if (!keyword.isEmpty()) {
                        handleAiMusic(player, keyword);
                    }
                }
            }

            // 过滤聊天显示：移除所有包含 PLAY: 的行，防止指令泄露给玩家
            // 1. 先把每一行中 PLAY: 或 STOP: 之后的内容（包括标志位本身）全部切掉
            String cleanReply = aiReply.replaceAll("(PLAY:|STOP:).*", "").trim();

            // 2. 如果切完之后还有内容，才发送给玩家
            if (!cleanReply.isEmpty()) {
                replyToPlayer(player, cleanReply);
            }

        }, errorMsg -> {
            sendErrorToPlayer(player, errorMsg);
        });
    }

    /**
     * 异步处理音乐搜索与发送
     */
    private static void handleAiMusic(ServerPlayer player, String keyword) {
        CompletableFuture.runAsync(() -> {
            try {
                System.out.println(">>> [Music Debug] 开始搜索关键词: [" + keyword + "]");

                String songId = com.bot.aibot.utils.NeteaseApi.search(keyword);
                if (songId != null) {
                    System.out.println(">>> [Music Debug] 搜索成功，歌曲 ID: " + songId);
                    String url = com.bot.aibot.utils.NeteaseApi.getSongUrl(songId);
                    if (url != null) {
                        System.out.println(">>> [Music Debug] 获取播放链接成功，发送数据包...");
                        com.bot.aibot.network.PacketHandler.sendToPlayer(
                                new com.bot.aibot.network.packet.S2CPlayMusicPacket(url, keyword), player);
                    } else {
                        System.out.println(">>> [Music Debug] 失败：无法获取播放链接 (可能受版权限制)");
                    }
                } else {
                    System.out.println(">>> [Music Debug] 失败：网易云搜索无结果");
                }
            } catch (Exception e) {
                System.err.println(">>> [Music Debug] 播放逻辑发生异常: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private static void handleStopMusic(ServerPlayer player) {
        // 复用你提供的 S2CMusicControlPacket，action 为 0 代表 Stop
        com.bot.aibot.network.PacketHandler.sendToPlayer(
                new com.bot.aibot.network.packet.S2CMusicControlPacket(0), player);
    }

    /**
     * 死亡播报翻译功能 (保持逻辑独立，不受点歌指令干扰)
     */
    public static void translateDeath(ServerPlayer player, String abstractKey) {
        String mode = BotConfig.SERVER.aiDeathMode.get();
        if (!BotConfig.SERVER.enableAI.get() || "OFF".equals(mode)) return;

        String systemPrompt = BotConfig.SERVER.aiDeathPrompt.get();
        String playerName = player.getName().getString();

        String promptWithContext = systemPrompt +
                "\n\n[重要指令]：\n" +
                "1. 原始消息中包含了 '%s'，这代表玩家的名字。\n" +
                "2. 请在你的翻译结果中**保留这个 '%s'**，放在合适的位置。\n" +
                "3. 只要输出翻译后的句子，不要其他废话。";

        sendRequest(promptWithContext, abstractKey, 0.8, translatedTemplate -> {
            ChineseUtils.learn(abstractKey, translatedTemplate);
            String realMsg = translatedTemplate.replace("%s", playerName);
            String template = BotConfig.SERVER.deathMsgFormat.get();
            String finalMsg = MinecraftEvents.formatMsg(template, playerName, realMsg);
            BotClient.getInstance().sendMessageToQQ(finalMsg);
        }, errorMsg -> {
            System.out.println(">>> [Bot AI] 死亡翻译失败: " + errorMsg);
        });
    }

    /**
     * 统一的 HTTP 请求逻辑
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