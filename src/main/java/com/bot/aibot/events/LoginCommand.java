package com.bot.aibot.events;

import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CRequestLoginPacket;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class LoginCommand implements Command<CommandSourceStack> {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bot")
                .then(Commands.literal("login")
                        .executes(new LoginCommand())));
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        // 1. 判断是否为玩家 (只有玩家客户端才有网易云环境)
        if (source.getEntity() instanceof ServerPlayer player) {

            // 2. 发送指令包给客户端：请在本地执行登录逻辑
            // (所有的扫码、轮询、保存Cookie到ClientConfig的操作，都已经在 S2CRequestLoginPacket 里写好了)
            PacketHandler.sendToPlayer(new S2CRequestLoginPacket(), player);

            source.sendSystemMessage(Component.literal("§e[Bot] 正在启动客户端登录流程... 请留意您的聊天栏链接或客户端控制台。"));

        } else {
            // 3. 控制台无法登录，因为控制台没有客户端配置文件来存 Cookie
            source.sendSystemMessage(Component.literal("§c[Bot] 控制台无法执行此命令！请进入游戏后使用，Cookie 将保存在您的客户端本地。"));
        }

        return 1;
    }
}