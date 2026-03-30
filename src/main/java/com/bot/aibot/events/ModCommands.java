package com.bot.aibot.events;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.BotClient;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CMusicCommandPacket;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ModCommands {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("bot") // 主指令 /bot

                        // 子指令: reload
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    context.getSource().sendSuccess(() -> Component.literal("§e[Bot] 正在读取硬盘配置..."), true);
                                    BotConfig.refresh();
                                    context.getSource().sendSuccess(() -> Component.literal("§e[Bot] 正在重启网络模块..."), true);
                                    BotClient.getInstance().reload();
                                    context.getSource().sendSuccess(() -> Component.literal("§a[Bot] 重载完成！"), true);
                                    return 1;
                                })
                        )
                        // 子指令: login
                        .then(Commands.literal("login")
                                .executes(context -> {
                                    context.getSource().sendSuccess(() -> Component.literal("§b[Bot] 请在弹出的窗口中扫码登录网易云..."), false);
                                    PacketHandler.sendToPlayer(
                                            new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.OPEN_GUI),
                                            context.getSource().getPlayerOrException()
                                    );
                                    return 1;
                                })
                        )
                        // 子指令: stop
                        .then(Commands.literal("stop")
                                .executes(context -> {
                                    // 发送 STOP 指令
                                    PacketHandler.sendToPlayer(
                                            new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.STOP),
                                            context.getSource().getPlayerOrException()
                                    );
                                    return 1;
                                })
                        )
                        // 子指令: gui
                        .then(Commands.literal("gui")
                                .executes(context -> {
                                    // OPEN_GUI 指令
                                    PacketHandler.sendToPlayer(
                                            new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.OPEN_GUI),
                                            context.getSource().getPlayerOrException()
                                    );
                                    return 1;
                                })
                        )
                        .then(Commands.literal("cd")
                                .requires(source -> source.hasPermission(2)) // 只有 OP (权限等级2) 能用
                                .executes(context -> {
                                    // 发送指令包给执行该命令的玩家
                                    PacketHandler.sendToPlayer(
                                            new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.RESET_COOLDOWN),
                                            context.getSource().getPlayerOrException()
                                    );
                                    // 服务端聊天栏反馈
                                    context.getSource().sendSuccess(() -> Component.literal("§e[Bot] 已发送重置冷却指令。"), true);
                                    return 1;
                                })
                        )
        );
    }
}