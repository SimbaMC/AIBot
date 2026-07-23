package com.bot.aibot.events;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.bot.aibot.ai.LLMClient;
import com.bot.aibot.binding.QQBindingManager;
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.BotClient;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

public class MinecraftEvents {

    public static String formatMsg(String template, String player, String message) {
        return template.replace("%prefix%", BotConfig.mcPrefix)
            .replace("%player%", player)
            .replace("%msg%", message);
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        String name = QQBindingManager.getInstance()
            .getChatDisplayName(event.player);
        String message = event.message == null ? "" : event.message;
        String trigger = BotConfig.aiTriggerPrefix == null ? "bot " : BotConfig.aiTriggerPrefix;
        if (BotConfig.enableAI && message.toLowerCase()
            .startsWith(trigger.toLowerCase())) {
            String question = message.substring(trigger.length())
                .trim();
            if (!question.isEmpty()) LLMClient.chat(event.player, question);
            return;
        }
        if (BotConfig.enableChatSync) BotClient.getInstance()
            .sendMessageToQQ(formatMsg(BotConfig.chatMsgFormat, name, message));
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            QQBindingManager.getInstance()
                .applyTabPrefix(player);
            QQBindingManager.getInstance()
                .sendBindReminder(player);
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
        String raw = player.func_110142_aN()
            .func_151521_b()
            .getUnformattedText();
        if (BotConfig.enableAI && !"OFF".equalsIgnoreCase(BotConfig.aiDeathMode)) {
            LLMClient.translateDeath(player, raw.replace(player.getCommandSenderName(), "%s"));
        } else {
            BotClient.getInstance()
                .sendMessageToQQ(formatMsg(BotConfig.deathMsgFormat, player.getCommandSenderName(), raw));
        }
    }
}
