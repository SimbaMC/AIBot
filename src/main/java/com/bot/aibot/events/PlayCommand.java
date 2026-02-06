package com.bot.aibot.events;

import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CRequestSearchPacket;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class PlayCommand implements Command<CommandSourceStack> {

    private final boolean forceGlobal;

    public PlayCommand() {
        this(false);
    }

    public PlayCommand(boolean forceGlobal) {
        this.forceGlobal = forceGlobal;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        String keyword = StringArgumentType.getString(context, "keyword");
        CommandSourceStack source = context.getSource();

        // 1. 只有玩家才能发起（因为需要客户端去搜）
        if (source.getEntity() instanceof ServerPlayer player) {

            // 2. 发送指令给客户端：去搜这首歌！
            // 参数 forceGlobal 决定了客户端搜到后是自己听，还是发回服务器广播
            PacketHandler.sendToPlayer(
                    new S2CRequestSearchPacket(keyword, forceGlobal),
                    player
            );

            source.sendSystemMessage(Component.literal("§7[Bot] 指令已下达，正在通过您的客户端搜索: " + keyword));

        } else {
            source.sendSystemMessage(Component.literal("§c[Bot] 控制台无法点歌，因为没有网易云客户端！"));
        }

        return 1;
    }
}