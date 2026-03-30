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
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("aibot")
public class BottyMod {

    public static MinecraftServer serverInstance;

    public BottyMod() {
        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BotConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BotConfig.CLIENT_SPEC);

        // 注册事件
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new MinecraftEvents());
        MinecraftForge.EVENT_BUS.register(new ModCommands());
        MinecraftForge.EVENT_BUS.register(new AdvancementEvents());

        // 注册网络包
        PacketHandler.register();

        // 注册客户端初始化事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            System.out.println(">>> [Bot] Client Setup...");
            NeteaseApi.loadCookies();
        });
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