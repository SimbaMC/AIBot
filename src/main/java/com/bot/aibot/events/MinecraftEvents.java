package com.bot.aibot.events;

import com.bot.aibot.ai.LLMClient;
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.BotClient;
import com.bot.aibot.utils.ChineseUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.regex.Pattern;

public class MinecraftEvents {

    // 用来检测是否包含中文的正则
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");

    public static String formatMsg(String template, String playerName, String message) {
        String prefix = BotConfig.SERVER.mcPrefix.get();
        return template
                .replace("%prefix%", prefix)
                .replace("%player%", playerName)
                .replace("%msg%", message);
    }

    // ... onChat, onPlayerJoin, onPlayerLeave 保持不变 ...
    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        // ... (保持原样) ...
        String name = event.getPlayer().getName().getString();
        String msg = event.getMessage().getString();

        if (BotConfig.SERVER.enableAI.get()) {
            String trigger = BotConfig.SERVER.aiTriggerPrefix.get();
            if (msg.toLowerCase().startsWith(trigger.toLowerCase())) {
                String question = msg.substring(trigger.length()).trim();
                if (!question.isEmpty()) {
                    LLMClient.chat(event.getPlayer(), question);
                }
                return;
            }
        }
        if (BotConfig.SERVER.enableChatSync.get()) {
            String template = BotConfig.SERVER.chatMsgFormat.get();
            BotClient.getInstance().sendMessageToQQ(formatMsg(template, name, msg));
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (BotConfig.SERVER.enableJoinLeave.get()) {
            BotClient.getInstance().sendMessageToQQ(formatMsg(BotConfig.SERVER.joinMsgFormat.get(), event.getEntity().getName().getString(), ""));
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (BotConfig.SERVER.enableJoinLeave.get()) {
            BotClient.getInstance().sendMessageToQQ(formatMsg(BotConfig.SERVER.leaveMsgFormat.get(), event.getEntity().getName().getString(), ""));
        }
    }

    // 【核心修改】支持三档模式的死亡逻辑
    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!BotConfig.SERVER.enableDeath.get()) return;
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String playerName = player.getName().getString();

        // 1. 获取原始英文消息
        String rawEnglishMsg = event.getSource().getLocalizedDeathMessage(player).getString();

        // 【核心修复】将玩家名字替换为 %s，制作成通用的 Key
        // 例如: "Dev fell from a high place" -> "%s fell from a high place"
        // 注意：这里简单的 replace 可能会有误伤（比如玩家名叫 "fell"），但在死亡消息语境下概率极低，足够用
        String abstractKey = rawEnglishMsg.replace(playerName, "%s");

        // 2. 获取本地翻译
        String localTranslatedMsg = ChineseUtils.translate(event.getSource().getLocalizedDeathMessage(player));

        // 3. 模式判断
        String mode = BotConfig.SERVER.aiDeathMode.get();
        String finalMessage = localTranslatedMsg;
        boolean shouldUseAI = false;

        if ("AI_ONLY".equals(mode)) {
            shouldUseAI = true;
        } else if ("HYBRID".equals(mode)) {
            // 如果本地翻译不含中文，说明没汉化
            if (!CHINESE_PATTERN.matcher(localTranslatedMsg).find()) {
                shouldUseAI = true;
            }
        }

        // --- AI 逻辑 ---
        if (shouldUseAI) {
            // A. 查缓存 (用 abstractKey 查，例如 "%s fell from a high place")
            String cached = ChineseUtils.getCached(abstractKey);

            if (cached != null) {
                // 命中缓存 (cached 是 "%s 从高处摔了下来")
                // 我们需要把 %s 填回具体的玩家名
                try {
                    // 简单的替换，或者 String.format
                    finalMessage = cached.replace("%s", playerName);
                    System.out.println(">>> [Bot] 🎯 命中通用缓存");
                } catch (Exception e) {
                    finalMessage = cached; // 容错
                }
            } else {
                // 未命中，调用 AI (传入 abstractKey)
                // 这里的 abstractKey 是带 %s 的，AI 会懂的
                LLMClient.translateDeath(player, abstractKey);
                return;
            }
        }

        // --- 发送消息 ---
        String template = BotConfig.SERVER.deathMsgFormat.get();
        BotClient.getInstance().sendMessageToQQ(formatMsg(template, playerName, finalMessage));
    }
}