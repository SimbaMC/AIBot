package com.bot.aibot.ai;

import com.bot.aibot.BottyMod;
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.events.MinecraftEvents;
import com.bot.aibot.network.BotClient;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CMusicCommandPacket;
import com.bot.aibot.utils.ChineseUtils;
import com.bot.aibot.utils.HttpUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LLMClient {

    /**
     * 智能聊天 + 指令解析
     */
    public static void chat(ServerPlayer player, String userMessage) {
        if (!BotConfig.SERVER.enableAI.get()) return;

        String playerName = player.getName().getString();
        player.sendSystemMessage(Component.literal("§7[Bot] 正在思考..."));

        String basePrompt = BotConfig.SERVER.aiPrompt.get();

        // --- 1. 构建全新的 System Prompt ---
        String systemPrompt = basePrompt +
                "\n\n[核心协议指令]：" +
                "\n请严格遵守以下指令格式，将指令代码放在回复的【最后一行】。指令行不要包含其他标点符号。" +
                "\n1. 搜歌（仅自己听）：回复 'ACTION: SEARCH: 歌名/关键词'" +
                "\n2. 搜歌（全服广播）：如果用户强调“大家”、“全服”、“所有人”，回复 'ACTION: SEARCH_ALL: 歌名/关键词'" +
                "\n3. 随机红心歌单：如果用户说“随便放点”、“放我的歌”、“来点我喜欢的”，回复 'ACTION: PLAY_MY_LIKE'" +
                "\n4. 停止播放：回复 'ACTION: STOP'" +
                "\n\n[示例]：" +
                "\n用户：帮我放首周杰伦的歌。" +
                "\n你的回复：根据你的个性回答。\nACTION: SEARCH: 周杰伦" +
                "\n\n用户：随便来点音乐。" +
                "\n你的回复：根据你的个性回答。\nACTION: PLAY_MY_LIKE" +
                "\n- 保持你的个性化语气。";

        String userContent = "玩家[" + playerName + "]说: " + userMessage;

        // 使用第一步封装的 HttpUtils (如果之前还没改 sendRequest，记得改一下)
        sendRequest(systemPrompt, userContent, 0.7, aiReply -> {
            // --- DEBUG ---
            System.out.println(">>> [LLM] 原始回复: " + aiReply);

            // --- 2. 解析逻辑 ---
            String chatContent = aiReply;
            String commandLine = null;

            // 从后往前找 ACTION: 标记
            int actionIndex = aiReply.lastIndexOf("ACTION:");
            if (actionIndex != -1) {
                // 提取指令行
                commandLine = aiReply.substring(actionIndex).trim();
                // 截取聊天内容（去掉指令部分）
                chatContent = aiReply.substring(0, actionIndex).trim();
            }

            // --- 3. 执行指令 ---
            if (commandLine != null) {
                executeCommand(player, commandLine);
            }

            // --- 4. 发送聊天回复 ---
            if (!chatContent.isEmpty()) {
                replyToPlayer(player, chatContent);
            }

        }, errorMsg -> {
            sendErrorToPlayer(player, errorMsg);
        });
    }
    /**
     * 解析并分发指令
     */
    private static void executeCommand(ServerPlayer player, String commandLine) {
        System.out.println(">>> [LLM] 识别到指令: " + commandLine);

        // 格式：ACTION: <TYPE>: <DATA>
        // 例如：ACTION: SEARCH: 稻香
        // 例如：ACTION: PLAY_MY_LIKE

        try {
            // 去掉前缀
            String raw = commandLine.replace("ACTION:", "").trim();

            if (raw.startsWith("STOP")) {
                PacketHandler.sendToPlayer(new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.STOP), player);
            }
            else if (raw.startsWith("PLAY_MY_LIKE")) {
                PacketHandler.sendToPlayer(new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.PLAY_MY_LIKE), player);
            }
            else if (raw.startsWith("SEARCH:")) {
                String keyword = raw.replace("SEARCH:", "").trim();
                // extra=0 代表私享
                PacketHandler.sendToPlayer(new S2CMusicCommandPacket(
                        S2CMusicCommandPacket.Action.SEARCH_AND_PLAY, keyword, 0), player);
            }
            else if (raw.startsWith("SEARCH_ALL:")) {
                String keyword = raw.replace("SEARCH_ALL:", "").trim();
                // extra=1 代表广播
                PacketHandler.sendToPlayer(new S2CMusicCommandPacket(
                        S2CMusicCommandPacket.Action.SEARCH_AND_PLAY, keyword, 1), player);
            }

        } catch (Exception e) {
            System.err.println(">>> [LLM] 指令解析失败: " + e.getMessage());
        }
    }

    /**
     * 【核心修改】异步点歌逻辑 -> 改为发送 S2CRequestSearchPacket
     */
    private static void handleAiMusic(ServerPlayer player, String keyword, boolean isGlobal) {
        System.out.println(">>> [Music Debug] AI 发起搜索: [" + keyword + "]");

        // 使用新包 Action.SEARCH_AND_PLAY
        // extra 参数我们约定：1代表全服，0代表私享 (目前客户端逻辑没用到这个区分，主要是汇报回去的时候用，先传1备用)
        S2CMusicCommandPacket packet = new S2CMusicCommandPacket(
                S2CMusicCommandPacket.Action.SEARCH_AND_PLAY,
                keyword,
                isGlobal ? 1 : 0
        );

        PacketHandler.sendToPlayer(packet, player);
    }

    private static void handleStopMusic(ServerPlayer player) {
        // 使用新包 Action.STOP
        PacketHandler.sendToPlayer(new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.STOP), player);
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
                HttpResponse<String> response = HttpUtils.getClient().send(request, HttpResponse.BodyHandlers.ofString());
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