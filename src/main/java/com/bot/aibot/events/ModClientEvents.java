package com.bot.aibot.events;

import com.bot.aibot.client.KeyBindings;
import com.bot.aibot.client.MusicPlayerScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// 标记为客户端专用事件处理类
@Mod.EventBusSubscriber(modid = "aibot", value = Dist.CLIENT)
public class ModClientEvents {

    // 1. 在 Mod 总线注册按键 (游戏启动时执行)
    @Mod.EventBusSubscriber(modid = "aibot", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(KeyBindings.OPEN_GUI_KEY);
        }
    }

    // 2. 在 Forge 总线监听按键输入 (游戏中实时执行)
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        // consumeClick() 会检查按键是否被按下，并且会“消耗”掉这次点击，防止重复触发
        if (KeyBindings.OPEN_GUI_KEY.consumeClick()) {
            // 直接打开 GUI，不需要发包给服务端，因为 GUI 是纯客户端逻辑
            Minecraft.getInstance().setScreen(new MusicPlayerScreen());
        }
    }
}