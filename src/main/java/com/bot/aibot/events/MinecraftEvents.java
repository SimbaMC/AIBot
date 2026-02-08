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

    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");

    public static String formatMsg(String template, String playerName, String message) {
        String prefix = BotConfig.SERVER.mcPrefix.get();
        return template
                .replace("%prefix%", prefix)
                .replace("%player%", playerName)
                .replace("%msg%", message);
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
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

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!BotConfig.SERVER.enableDeath.get()) return;
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String playerName = player.getName().getString();
        String rawEnglishMsg = event.getSource().getLocalizedDeathMessage(player).getString();
        String abstractKey = rawEnglishMsg.replace(playerName, "%s");
        String localTranslatedMsg = ChineseUtils.translate(event.getSource().getLocalizedDeathMessage(player));

        String mode = BotConfig.SERVER.aiDeathMode.get();
        String finalMessage = localTranslatedMsg;
        boolean shouldUseAI = false;

        if ("AI_ONLY".equals(mode)) {
            shouldUseAI = true;
        } else if ("HYBRID".equals(mode)) {
            if (!CHINESE_PATTERN.matcher(localTranslatedMsg).find()) {
                shouldUseAI = true;
            }
        }

        if (shouldUseAI) {
            String cached = ChineseUtils.getCached(abstractKey);
            if (cached != null) {
                try {
                    finalMessage = cached.replace("%s", playerName);
                    System.out.println(">>> [Bot] 缓存命中！");
                } catch (Exception e) {
                    finalMessage = cached;
                }
            } else {
                LLMClient.translateDeath(player, abstractKey);
                return;
            }
        }

        String template = BotConfig.SERVER.deathMsgFormat.get();
        BotClient.getInstance().sendMessageToQQ(formatMsg(template, playerName, finalMessage));
    }
}