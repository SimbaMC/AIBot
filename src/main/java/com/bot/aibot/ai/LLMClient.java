package com.bot.aibot.ai;

import com.bot.aibot.BottyMod;
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.events.MinecraftEvents;
import com.bot.aibot.network.BotClient;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CPlayMusicPacket;
import com.bot.aibot.network.packet.S2CRequestSearchPacket;
import com.bot.aibot.utils.ChineseUtils;
import com.bot.aibot.utils.NeteaseApi;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

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
                "\n. 【核心】请优化搜索词！如果玩家说中文译名，请优先转换为原语种名称，玩家也可能说的是一个网络热梗，请先检索后再返回歌曲名。" +
                "\n. 如果玩家只提供了歌词或模糊信息，请根据你的知识库将其转换为 '歌手 - 歌名' 格式，以提高网易云搜索精度。" +
                "\n- 如果玩家想点歌，请在回复末尾添加 'PLAY: 歌曲名'。" +
                "\n- 如果玩家想点歌且明确提到“所有人”、“全服”、“广播”等，请在末尾加上 'BROADCAST: 歌曲名'。" +
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

            // 解析逻辑：同时处理 PLAY: 和 BROADCAST:
            String[] lines = aiReply.split("\n");
            for (String line : lines) {
                String trimmedLine = line.trim();

                // 识别广播点歌
                if (trimmedLine.contains("BROADCAST:")) {
                    String keyword = extractAndClean(trimmedLine, "BROADCAST:");
                    System.out.println(">>> [LLM DEBUG] 触发广播点歌: [" + keyword + "]");
                    if (!keyword.isEmpty()) {
                        handleAiMusic(player, keyword, true); // true 代表全服广播
                    }
                }
                // 识别普通点歌
                else if (trimmedLine.contains("PLAY:")) {
                    String keyword = extractAndClean(trimmedLine, "PLAY:");
                    System.out.println(">>> [LLM DEBUG] 触发私享点歌: [" + keyword + "]");
                    if (!keyword.isEmpty()) {
                        handleAiMusic(player, keyword, false); // false 代表仅个人听
                    }
                }
            }

            // 2. 过滤聊天显示：将所有指令关键字及其后面的内容全部切掉
            String cleanReply = aiReply.replaceAll("(PLAY:|STOP:|BROADCAST:).*", "").trim();

            if (!cleanReply.isEmpty()) {
                replyToPlayer(player, cleanReply);
            }

        }, errorMsg -> {
            sendErrorToPlayer(player, errorMsg);
        });
    }
    /**
     * 提取并清洗关键词的工具方法
     */
    private static String extractAndClean(String line, String prefix) {
        int index = line.indexOf(prefix);
        String raw = line.substring(index + prefix.length()).trim();
        return raw.replace("\"", "").replace("'", "")
                .replace("《", "").replace("》", "")
                .replace("“", "").replace("”", "").trim();
    }

    /**
     * 【核心修改】异步点歌逻辑 -> 改为发送 S2CRequestSearchPacket
     */
    private static void handleAiMusic(ServerPlayer player, String keyword, boolean isGlobal) {
        // 不需要 CompletableFuture 了，因为发包是非阻塞的
        System.out.println(">>> [Music Debug] AI 发起搜索: [" + keyword + "], 模式: " + (isGlobal ? "全服" : "私享"));

        // 1. 构造“帮我搜歌”的请求包
        // 参数：关键词, 是否广播
        S2CRequestSearchPacket packet = new S2CRequestSearchPacket(keyword, isGlobal);

        // 2. 发送给玩家客户端
        PacketHandler.sendToPlayer(packet, player);

        // 3. 给个反馈
        if (isGlobal) {
            player.sendSystemMessage(Component.literal("§a[Bot] AI 已为您发起全服点歌: §e" + keyword));
        } else {
            player.sendSystemMessage(Component.literal("§b[Bot] AI 已为您发起私享点歌: §e" + keyword));
        }
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