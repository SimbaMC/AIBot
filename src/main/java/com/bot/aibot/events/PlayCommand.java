package com.bot.aibot.events;

import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CPlayMusicPacket;
import com.bot.aibot.utils.NeteaseApi;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public class PlayCommand implements Command<CommandSourceStack> {

    private final boolean forceGlobal;

    // 默认构造函数（用于 /bot play <keyword>）
    public PlayCommand() {
        this(false);
    }

    // 带参数构造函数（用于 /bot play all <keyword>）
    public PlayCommand(boolean forceGlobal) {
        this.forceGlobal = forceGlobal;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        String keyword = StringArgumentType.getString(context, "keyword");
        CommandSourceStack source = context.getSource();

        // 异步执行，防止搜索 API 时卡住服务器主线程
        new Thread(() -> {
            try {
                sendMsg(source, "🔍 正在云端搜索: §e" + keyword + "§r ...");

                // 1. 搜索歌曲 ID
                String songId = NeteaseApi.search(keyword);
                if (songId == null) {
                    sendMsg(source, "❌ 未找到相关歌曲，或 API 响应超时。");
                    return;
                }

                // 2. 获取播放链接
                String url = NeteaseApi.getSongUrl(songId);
                if (url == null) {
                    sendMsg(source, "❌ 无法获取播放链接 (可能是 VIP 专属或无版权)。");
                    return;
                }

                // 3. 构造播放数据包
                S2CPlayMusicPacket packet = new S2CPlayMusicPacket(url, keyword);

                // 判断发送逻辑：强制全局 OR 控制台发送 -> 全服广播；否则 -> 个人私享
                if (forceGlobal || !(source.getEntity() instanceof ServerPlayer)) {
                    // 全服广播
                    PacketHandler.INSTANCE.send(
                            PacketDistributor.ALL.noArg(),
                            packet
                    );
                    sendMsg(source, "▶️ §6[全服广播] §f正在播放: §a" + keyword);
                } else {
                    // 仅发送给指令执行者
                    ServerPlayer player = (ServerPlayer) source.getEntity();
                    PacketHandler.INSTANCE.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            packet
                    );
                    sendMsg(source, "▶️ §b[私享] §f正在为您播放: §a" + keyword + " §7(原版 BGM 已暂停)");
                }

                // 后台日志留底
                System.out.println(">>> [Music] 发送播放指令: " + keyword + " (Global: " + forceGlobal + ") -> " + url);

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