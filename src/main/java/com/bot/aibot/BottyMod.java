package com.bot.aibot;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.events.AdvancementEvents;
import com.bot.aibot.events.MinecraftEvents;
import com.bot.aibot.events.ModCommands;
import com.bot.aibot.network.BotClient;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.utils.ChineseUtils;
import com.bot.aibot.utils.NeteaseApi;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod("aibot")
public class BottyMod {

    public static MinecraftServer serverInstance;

    public BottyMod() {
        // 注册配置

        // 注册事件
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new MinecraftEvents());
        NeoForge.EVENT_BUS.register(new ModCommands());
        NeoForge.EVENT_BUS.register(new AdvancementEvents());

        // 注册网络包
        PacketHandler.register();

        // 客户端侧初始化（兼容 NeoForge API 变更）
        NeteaseApi.loadCookies();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        serverInstance = event.getServer();
        ChineseUtils.load();
        System.out.println(">>> [Bot] Starting Network...");
        BotClient.getInstance().connect();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        BotClient.getInstance().close("Server Stopping");
    }
}