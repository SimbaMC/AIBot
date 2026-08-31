package com.bot.aibot;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.events.AdvancementEvents;
import com.bot.aibot.events.MinecraftEvents;
import com.bot.aibot.events.ModCommands;
import com.bot.aibot.events.QQBindCommand;
import com.bot.aibot.network.BotClient;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.proxy.CommonProxy;
import com.bot.aibot.security.MusicReportService;
import com.bot.aibot.utils.ChineseUtils;
import com.bot.aibot.utils.GeoIpResolver;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

@Mod(modid = BottyMod.MODID, name = "AiBot", version = Tags.VERSION, acceptableRemoteVersions = "*")
public class BottyMod {

    public static final String MODID = "aibot";
    public static final Logger LOG = LogManager.getLogger("AiBot");
    public static java.io.File configDirectory;
    public static MinecraftServer serverInstance;
    @SidedProxy(clientSide = "com.bot.aibot.proxy.ClientProxy", serverSide = "com.bot.aibot.proxy.CommonProxy")
    public static CommonProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configDirectory = event.getModConfigurationDirectory();
        BotConfig.init(event.getModConfigurationDirectory());
        PacketHandler.register();
        MinecraftEvents events = new MinecraftEvents();
        MinecraftForge.EVENT_BUS.register(events);
        MinecraftForge.EVENT_BUS.register(new AdvancementEvents());
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        proxy.preInit();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        serverInstance = event.getServer();
        event.registerServerCommand(new ModCommands());
        event.registerServerCommand(new QQBindCommand());
        ChineseUtils.load();
        BotClient.getInstance()
            .connect();
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        MusicReportService.shutdown();
        GeoIpResolver.shutdown();
        BotClient.getInstance()
            .close("Server stopping");
        serverInstance = null;
    }
}
