package com.bot.aibot.events;

import java.util.Locale;
import java.util.regex.Pattern;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.bot.aibot.BottyMod;
import com.bot.aibot.ai.LLMClient;
import com.bot.aibot.binding.QQBindingManager;
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.BotClient;
import com.bot.aibot.network.ServerTaskQueue;
import com.bot.aibot.utils.ChineseUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class MinecraftEvents {

    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) ServerTaskQueue.runPending();
    }

    public static String formatMsg(String template, String playerName, String message) {
        return template.replace("%prefix%", BotConfig.mcPrefix)
            .replace("%player%", playerName)
            .replace("%msg%", message);
    }

    private static String avoidDuplicatedPlayerPrefix(String template, String playerName, String message) {
        if (template.contains("%player%") && message != null && message.startsWith(playerName))
            return message.substring(playerName.length());
        return message;
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        String name = QQBindingManager.getInstance()
            .getChatDisplayName(event.player);
        String message = event.message == null ? "" : event.message;
        String normalized = message.trim();
        BottyMod.LOG
            .info(">>> [Bot AI] 聊天事件: player=" + name + ", aiEnabled=" + BotConfig.enableAI + ", rawMsg=" + normalized);
        if (BotConfig.enableAI) {
            String trigger = BotConfig.aiTriggerPrefix == null ? "" : BotConfig.aiTriggerPrefix.trim();
            if (trigger.isEmpty()) trigger = "bot";
            String lower = normalized.toLowerCase(Locale.ROOT);
            String lowerTrigger = trigger.toLowerCase(Locale.ROOT);
            boolean matched = lower.equals(lowerTrigger) || lower.startsWith(lowerTrigger + " ")
                || lower.startsWith(lowerTrigger + "，")
                || lower.startsWith(lowerTrigger + ",")
                || lower.startsWith(lowerTrigger);
            if (matched) {
                String question = normalized.substring(Math.min(trigger.length(), normalized.length()))
                    .trim();
                if (!question.isEmpty()) LLMClient.chat(event.player, question);
                return;
            }
        }
        if (BotConfig.enableChatSync) BotClient.getInstance()
            .sendMessageToQQ(formatMsg(BotConfig.chatMsgFormat, name, message));
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            QQBindingManager manager = QQBindingManager.getInstance();
            manager.applyTabPrefix(player);
            if (!manager.isConfirmed(player)) manager.sendBindReminder(player);
        }
        if (BotConfig.enableJoinLeave) BotClient.getInstance()
            .sendMessageToQQ(formatMsg(BotConfig.joinMsgFormat, event.player.getCommandSenderName(), ""));
    }

    @SubscribeEvent
    public void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (BotConfig.enableJoinLeave) BotClient.getInstance()
            .sendMessageToQQ(formatMsg(BotConfig.leaveMsgFormat, event.player.getCommandSenderName(), ""));
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!BotConfig.enableDeath || event.entity.worldObj.isRemote || !(event.entity instanceof EntityPlayerMP))
            return;
        EntityPlayerMP player = (EntityPlayerMP) event.entity;
        String playerName = player.getCommandSenderName();
        IChatComponent component = player.func_110142_aN()
            .func_151521_b();
        String raw = component.getUnformattedText();
        String abstractKey = raw.replace(playerName, "%s");
        String translated = ChineseUtils.translate(component);
        boolean shouldUseAI = "AI_ONLY".equalsIgnoreCase(BotConfig.aiDeathMode)
            || ("HYBRID".equalsIgnoreCase(BotConfig.aiDeathMode) && !CHINESE_PATTERN.matcher(translated)
                .find());
        if (BotConfig.enableAI && shouldUseAI) {
            String cached = ChineseUtils.getCached(abstractKey);
            if (cached == null) {
                LLMClient.translateDeath(player, abstractKey);
                return;
            }
            translated = cached.replace("%s", playerName);
        }
        String normalized = avoidDuplicatedPlayerPrefix(BotConfig.deathMsgFormat, playerName, translated);
        BotClient.getInstance()
            .sendMessageToQQ(formatMsg(BotConfig.deathMsgFormat, playerName, normalized));
    }
}
