package com.bot.aibot.events;

import net.minecraftforge.event.entity.player.AchievementEvent;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.BotClient;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class AdvancementEvents {

    @SubscribeEvent
    public void onAchievement(AchievementEvent event) {
        if (!BotConfig.enableAdvancement || event.achievement == null || event.entityPlayer.worldObj.isRemote) return;
        String message = MinecraftEvents
            .formatMsg(BotConfig.advancementMsgFormat, event.entityPlayer.getCommandSenderName(), "")
            .replace(
                "%advancement%",
                event.achievement.func_150951_e()
                    .getUnformattedText())
            .replace("%desc%", event.achievement.getDescription());
        BotClient.getInstance()
            .sendMessageToQQ(message);
    }
}
