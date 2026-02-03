package com.bot.aibot.events;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.BotClient;
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
                        .then(Commands.literal("music_test")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            new Thread(() -> { // 必须异步，不能卡死主线程
                                                String id = NeteaseApi.search(name);
                                                if (id != null) {
                                                    String url = NeteaseApi.getSongUrl(id);
                                                    context.getSource().sendSuccess(() ->
                                                            Component.literal("🔍 搜索: " + name + "\n🆔 ID: " + id + "\n🔗 URL: " + url), false);
                                                } else {
                                                    context.getSource().sendFailure(Component.literal("❌ 未找到歌曲"));
                                                }
                                            }).start();
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("login")
                                .executes(new LoginCommand())
                        )
        );
    }
}