package com.bot.aibot.events;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.bot.aibot.binding.QQBindingManager;

public class QQBindCommand extends CommandBase {

    public String getCommandName() {
        return "qqbind";
    }

    public String getCommandUsage(ICommandSender sender) {
        return "/qqbind <QQ号>";
    }

    public int getRequiredPermissionLevel() {
        return 0;
    }

    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP) || args.length != 1) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        try {
            long qq = Long.parseLong(args[0]);
            if (qq < 10000L || qq > 999999999999L) throw new NumberFormatException();
            EntityPlayerMP player = (EntityPlayerMP) sender;
            QQBindingManager.getInstance()
                .requestBind(player, qq);
        } catch (NumberFormatException e) {
            sender.addChatMessage(new ChatComponentText("§c[Bot] QQ号格式无效。"));
        }
    }
}
