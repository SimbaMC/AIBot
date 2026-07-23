package com.bot.aibot.events;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.bot.aibot.binding.QQBindingManager;
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.BotClient;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.S2CMusicCommandPacket;

public class ModCommands extends CommandBase {

    public String getCommandName() {
        return "bot";
    }

    public String getCommandUsage(ICommandSender sender) {
        return "/bot <reload|stop|qqbind QQ>";
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
            if (!sender.canCommandSenderUseCommand(2, "bot")) return;
            BotConfig.load();
            BotClient.getInstance()
                .reload();
            sender.addChatMessage(new ChatComponentText("§a[Bot] Reloaded."));
        } else if (sender instanceof EntityPlayerMP && "stop".equalsIgnoreCase(args[0])) {
            PacketHandler
                .sendToPlayer(new S2CMusicCommandPacket(S2CMusicCommandPacket.Action.STOP), (EntityPlayerMP) sender);
        } else if (sender instanceof EntityPlayerMP && "qqbind".equalsIgnoreCase(args[0]) && args.length > 1) {
            QQBindingManager.getInstance()
                .requestBind((EntityPlayerMP) sender, Long.parseLong(args[1]));
        } else sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
    }
}
