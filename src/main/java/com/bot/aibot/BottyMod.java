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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod("aibot")
public class BottyMod {

    // 全局静态实例，方便别的地方获取 Server
    public static MinecraftServer serverInstance;

    public BottyMod() {
        // 1. 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BotConfig.SERVER_SPEC);

        // 2. 注册自己的事件
        MinecraftForge.EVENT_BUS.register(this);

        // 3. 注册其他模块的事件监听器
        MinecraftForge.EVENT_BUS.register(new MinecraftEvents());
        MinecraftForge.EVENT_BUS.register(new ModCommands());

        // 【新增】注册成就事件监听器
        MinecraftForge.EVENT_BUS.register(new AdvancementEvents());
        // 注册网络包
        PacketHandler.register();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        serverInstance = event.getServer();
        ChineseUtils.load(); // 加载汉化

        // 【新增】服务器启动时，尝试恢复登录状态
        NeteaseApi.loadCookies();

        System.out.println(">>> [Bot] 正在启动网络模块...");
        BotClient.getInstance().connect(); // 启动连接
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        BotClient.getInstance().close("Server Stopping");
    }
}