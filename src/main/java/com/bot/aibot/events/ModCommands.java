package com.bot.aibot.events;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.BotClient;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CMusicCommandPacket;
import com.bot.aibot.security.MusicReportService;

public class ModCommands extends CommandBase {

    public String getCommandName() {
        return "bot";
    }

    public String getCommandUsage(ICommandSender sender) {
        return "/bot <reload|stop|cd>";
    }

    public int getRequiredPermissionLevel() {
        return 0;
    }

    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.canCommandSenderUseCommand(2, "bot")) {
                sender.addChatMessage(new ChatComponentText("§c[Bot] 你没有权限执行此命令。"));
                return;
            }
            sender.addChatMessage(new ChatComponentText("§e[Bot] 正在读取硬盘配置..."));
            BotConfig.load();
            sender.addChatMessage(new ChatComponentText("§e[Bot] 正在重启网络模块..."));
            BotClient.getInstance()
                .reload();
            sender.addChatMessage(new ChatComponentText("§a[Bot] 重载完成！"));
        } else if (sender instanceof EntityPlayerMP && "stop".equalsIgnoreCase(args[0])) {
            PacketHandler
                .sendToPlayer(new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.STOP), (EntityPlayerMP) sender);
        } else if (sender instanceof EntityPlayerMP && "cd".equalsIgnoreCase(args[0])
            && sender.canCommandSenderUseCommand(2, "bot")) {
                MusicReportService.resetBroadcastCooldown();
                PacketHandler.sendToPlayer(
                    new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.RESET_COOLDOWN),
                    (EntityPlayerMP) sender);
                sender.addChatMessage(new ChatComponentText("§a[Bot] 全服播放冷却已重置。"));
            } else sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
    }
}
