package com.bot.aibot.events;

import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CPlayMusicPacket;
import com.bot.aibot.utils.NeteaseApi;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public class PlayCommand implements Command<CommandSourceStack> {

    // 注册指令 /bot play <歌名>
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bot")
                .then(Commands.literal("play")
                        .then(Commands.argument("keyword", StringArgumentType.greedyString()) // greedyString 允许带空格
                                .executes(new PlayCommand()))));
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        String keyword = StringArgumentType.getString(context, "keyword");
        CommandSourceStack source = context.getSource();

        // 异步执行，防止卡顿
        new Thread(() -> {
            try {
                sendMsg(source, "🔍 正在云端搜索: §e" + keyword + "§r ...");

                // 1. 搜索
                String songId = NeteaseApi.search(keyword);
                if (songId == null) {
                    sendMsg(source, "❌ 未找到相关歌曲，或 API 响应超时。");
                    return;
                }

                // 2. 获取链接
                String url = NeteaseApi.getSongUrl(songId);
                if (url == null) {
                    sendMsg(source, "❌ 无法获取播放链接 (可能是 VIP 专属或无版权)。");
                    return;
                }

                // 3. 【核心修改】发送网络包，接管客户端 BGM
                S2CPlayMusicPacket packet = new S2CPlayMusicPacket(url, keyword);

                if (source.getEntity() instanceof ServerPlayer player) {
                    // 情况 A: 玩家自己在游戏里输入 -> 只放给该玩家听 (私享 BGM)
                    PacketHandler.INSTANCE.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            packet
                    );
                    sendMsg(source, "▶️ 正在为您播放: §a" + keyword + " §7(原版 BGM 已暂停)");
                } else {
                    // 情况 B: 控制台/命令方块输入 -> 全服广播
                    PacketHandler.INSTANCE.send(
                            PacketDistributor.ALL.noArg(),
                            packet
                    );
                    sendMsg(source, "▶️ [全服广播] 正在播放: §a" + keyword);
                }

                // 后台留底
                System.out.println(">>> [Music] 发送播放指令: " + keyword + " -> " + url);

            } catch (Exception e) {
                e.printStackTrace();
                sendMsg(source, "❌ 发生内部错误: " + e.getMessage());
            }
        }).start();

        return 1;
    }

    private void sendMsg(CommandSourceStack source, String msg) {
        source.sendSystemMessage(Component.literal(msg));
    }
}