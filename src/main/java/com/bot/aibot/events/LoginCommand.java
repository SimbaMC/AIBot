package com.bot.aibot.events;

import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CRequestLoginPacket;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class LoginCommand implements Command<CommandSourceStack> {

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // 1. 只有玩家才能执行（因为需要客户端去扫码）
        if (source.getEntity() instanceof ServerPlayer player) {

            // 2. 发送指令包给客户端：请启动登录流程
            PacketHandler.sendToPlayer(new S2CRequestLoginPacket(), player);

            source.sendSystemMessage(Component.literal("§e[Bot] 正在启动客户端登录流程... 请留意聊天栏链接。"));
        } else {
            source.sendSystemMessage(Component.literal("§c[Bot] 控制台无法执行此命令！请在游戏内使用。"));
        }
        return 1;
    }
}