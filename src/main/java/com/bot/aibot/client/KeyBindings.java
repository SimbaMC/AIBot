package com.bot.aibot.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    // 按键的分类名（在控制设置里显示的分类）
    public static final String CATEGORY = "key.categories.aibot";

    // 按键的具体名称
    public static final String KEY_OPEN_GUI = "key.aibot.open_gui";

    // 定义按键实例：默认绑定到 M 键，只在游戏中有效
    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            KEY_OPEN_GUI,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M, // 默认 M 键
            CATEGORY
    );
}