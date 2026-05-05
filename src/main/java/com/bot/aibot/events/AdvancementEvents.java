package com.bot.aibot.events;


import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.BotClient;
import com.bot.aibot.utils.ChineseUtils;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AdvancementEvents {

    // 【新增】防刷屏缓存：记录 "玩家名:成就ID" -> 上次播报时间
    private static final Map<String, Long> COOLDOWN_MAP = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onAdvancement(AdvancementEarnEvent event) {
        // 1. 检查总开关
        if (!BotConfig.SERVER.enableAdvancement.get()) return;

        // 2. 仅服务端逻辑 & 类型检查
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 3. AdvancementEarnEvent fires only when the advancement is fully earned (isDone)
        AdvancementHolder adv = event.getAdvancement();
        DisplayInfo display = adv.value().display().orElse(null);
        if (display == null || !display.shouldAnnounceChat()) return;

        // --- 【修复 1】防重复播报逻辑 ---
        String playerName = player.getName().getString();
        // 组合一个唯一的 Key：玩家名 + 成就ID (例如: Dev:minecraft:story/mine_stone)
        String uniqueKey = playerName + ":" + adv.id().toString();
        long now = System.currentTimeMillis();

        // 如果 1 秒内已经播报过这个成就，直接跳过
        if (now - COOLDOWN_MAP.getOrDefault(uniqueKey, 0L) < 1000) {
            return;
        }
        // 更新最后播报时间
        COOLDOWN_MAP.put(uniqueKey, now);
        // -----------------------------

        // --- 【修复 2】汉化逻辑 ---
        // 使用 ChineseUtils.translate 替代直接 getString()
        String title = ChineseUtils.translate(display.getTitle());
        String desc = ChineseUtils.translate(display.getDescription());
        // ------------------------

        // 6. 格式化消息
        String template = BotConfig.SERVER.advancementMsgFormat.get();
        String tempMsg = MinecraftEvents.formatMsg(template, playerName, "");

        String finalMsg = tempMsg
                .replace("%advancement%", title)
                .replace("%desc%", desc);

        // 7. 发送
        BotClient.getInstance().sendMessageToQQ(finalMsg);
    }
}