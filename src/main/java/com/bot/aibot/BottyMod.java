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
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("aibot")
public class BottyMod {

    private static final Logger LOGGER = LogManager.getLogger();

    public static MinecraftServer serverInstance;

    public BottyMod(IEventBus modBus, ModContainer modContainer) {
        // 注册配置
        modContainer.registerConfig(ModConfig.Type.COMMON, BotConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, BotConfig.CLIENT_SPEC);

        // 注册事件
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new MinecraftEvents());
        NeoForge.EVENT_BUS.register(new ModCommands());
        NeoForge.EVENT_BUS.register(new AdvancementEvents());

        // 注册网络包
        modBus.addListener(PacketHandler::register);

        // 注册客户端初始化事件
        modBus.addListener(this::doClientStuff);
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info(">>> [Bot] Client Setup...");
            NeteaseApi.loadCookies();
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        serverInstance = event.getServer();
        ChineseUtils.load();
        LOGGER.info(">>> [Bot] Starting Network...");
        BotClient.getInstance().connect();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        BotClient.getInstance().close("Server Stopping");
    }
}
