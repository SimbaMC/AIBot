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
                                    // 依然使用新包 Action.OPEN_GUI (因为登录通常也是打开GUI的一部分，或者你可以保持特殊的处理)
                                    // 但根据之前的逻辑，login 似乎主要是为了触发二维码。
                                    // 既然我们之前只有 OPEN_GUI，这里可以用 OPEN_GUI，或者暂时不动客户端的 LoginQrScreen 逻辑（那个是 ClientCommand 还是什么？）
                                    // 如果你在服务端没有专门发给 login 的包，这里可以用 OPEN_GUI 打开主界面，让玩家点登录。
                                    // 或者，这里可以发一个 OPEN_GUI，参数里带个标记？
                                    // 为了简单起见，这里先打开主 GUI
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
                                    // 【修复】发送 STOP 指令
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
                                    // 【修复】发送 OPEN_GUI 指令
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