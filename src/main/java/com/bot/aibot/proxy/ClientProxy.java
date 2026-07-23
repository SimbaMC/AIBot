package com.bot.aibot.proxy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.bot.aibot.BottyMod;
import com.bot.aibot.client.ClientPacketHandler;
import com.bot.aibot.client.MusicPlayerScreen;
import com.bot.aibot.config.ClientMusicConfig;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

public class ClientProxy extends CommonProxy {

    private final KeyBinding musicKey = new KeyBinding("key.aibot.music", Keyboard.KEY_M, "key.categories.aibot");

    public void preInit() {
        ClientMusicConfig.init(BottyMod.configDirectory);
        ClientRegistry.registerKeyBinding(musicKey);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (musicKey.isPressed()) Minecraft.getMinecraft()
            .displayGuiScreen(new MusicPlayerScreen());
    }

    public void handleMusicPacket(int action, String data, long extra) {
        ClientPacketHandler.handle(action, data, extra);
    }
}
