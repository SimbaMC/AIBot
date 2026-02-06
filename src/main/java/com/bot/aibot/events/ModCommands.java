package com.bot.aibot.events;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.BotClient;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CMusicControlPacket;
import com.bot.aibot.network.packet.S2CRequestActionPacket;
import com.bot.aibot.utils.ChineseUtils; // 导入这个
import com.bot.aibot.utils.NeteaseApi;
import com.mojang.brigadier.arguments.StringArgumentType; // 导入这个
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ModCommands {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("bot") // 主指令 /bot

                        // 子指令 1: 重载
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2)) // 需要 OP 权限
                                .executes(context -> {
                                    context.getSource().sendSuccess(() -> Component.literal("§e[Bot] 正在读取硬盘配置..."), true);

                                    // 1. 【核心修复】这里必须调用 refresh！
                                    // 这一步会将 toml 文件的新内容强行刷入内存
                                    BotConfig.refresh();

                                    context.getSource().sendSuccess(() -> Component.literal("§e[Bot] 正在重启网络模块..."), true);

                                    // 2. 然后再执行 Bot 重连 (此时 BotClient 内部读到的就是新配置了)
                                    BotClient.getInstance().reload();

                                    context.getSource().sendSuccess(() -> Component.literal("§a[Bot] ✅ 重载全部完成！"), true);
                                    return 1;
                                })
                        )

                        // 子指令 2: 查字典 (新增的)
                        // 用法: /bot check item.twilightforest.naga_scale
                        .then(Commands.literal("check")
                                .then(Commands.argument("key", StringArgumentType.string())
                                        .executes(context -> {
                                            String key = StringArgumentType.getString(context, "key");
                                            // 伪造一个 Component 来测试翻译
                                            Component testComp = Component.translatable(key);
                                            String result = ChineseUtils.translate(testComp);

                                            context.getSource().sendSuccess(() ->
                                                    Component.literal("§b[Bot翻译测试] §f" + key + " -> §a" + result), false);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("play")
                                // 分支 A: /bot play all <keyword> -> 强制广播
                                .then(Commands.literal("all")
                                        .then(Commands.argument("keyword", StringArgumentType.greedyString())
                                                .executes(new PlayCommand(true)))) // 需要给 PlayCommand 加个构造函数
                                // 分支 B: /bot play <keyword> -> 走原有的自动判断逻辑
                                .then(Commands.argument("keyword", StringArgumentType.greedyString())
                                        .executes(new PlayCommand(false))))

                        .then(Commands.literal("stop")
                                .executes(context -> {
                                    PacketHandler.sendToPlayer(new S2CMusicControlPacket(0), context.getSource().getPlayerOrException());
                                    return 1;
                                })
                        )
                        .then(Commands.literal("pause")
                                .executes(context -> {
                                    PacketHandler.sendToPlayer(new S2CMusicControlPacket(1), context.getSource().getPlayerOrException());
                                    return 1;
                                })
                        )


                        .then(Commands.literal("login")
                                .executes(new LoginCommand())
                        )
                        .then(Commands.literal("stoplogin")
                                .executes(context -> {
                                    // 获取执行指令的玩家
                                    if (context.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                                        // 发送停止包给该玩家的客户端
                                        PacketHandler.sendToPlayer(new com.bot.aibot.network.packet.S2CStopLoginPacket(), player);
                                        context.getSource().sendSystemMessage(Component.literal("§7[Bot] 已向您的客户端发送终止信号。"));
                                    }
                                    return 1;
                                })
                        )

                        // 指令: /bot gui (打开界面)
                        .then(Commands.literal("gui")
                                .executes(context -> {
                                    // 发送 ACTION="OPEN_GUI" 给玩家
                                    PacketHandler.sendToPlayer(new S2CRequestActionPacket("OPEN_GUI"), context.getSource().getPlayerOrException());
                                    return 1;
                                })
                        )

                        // 指令: /bot mine (查看歌单)
                        .then(Commands.literal("mine")
                                .executes(context -> {
                                    PacketHandler.sendToPlayer(new S2CRequestActionPacket("MINE"), context.getSource().getPlayerOrException());
                                    return 1;
                                })
                        )

                        // 指令: /bot mylike (随机红心)
                        .then(Commands.literal("mylike")
                                .executes(context -> {
                                    context.getSource().sendSuccess(() -> Component.literal("§b[Bot] 正在前往您的客户端抽取幸运歌曲..."), false);
                                    PacketHandler.sendToPlayer(new S2CRequestActionPacket("MYLIKE"), context.getSource().getPlayerOrException());
                                    return 1;
                                })
                        )
        );
    }
}