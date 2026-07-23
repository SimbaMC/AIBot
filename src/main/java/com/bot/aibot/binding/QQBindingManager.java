package com.bot.aibot.binding;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

import com.bot.aibot.BottyMod;
import com.bot.aibot.network.BotClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public final class QQBindingManager {

    public static class BindingRecord {

        public String playerName, uuid, groupNickname;
        public long qq;
        public boolean confirmed;
    }

    private static final QQBindingManager INSTANCE = new QQBindingManager();
    private final Map<String, BindingRecord> records = new LinkedHashMap<String, BindingRecord>();
    private final File file = new File("config", "aibot-qq-bindings.json");
    private boolean loaded;

    public static QQBindingManager getInstance() {
        return INSTANCE;
    }

    private synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (!file.isFile()) return;
        try {
            Type type = new TypeToken<Map<String, BindingRecord>>() {}.getType();
            Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            Map<String, BindingRecord> value = new Gson().fromJson(reader, type);
            reader.close();
            if (value != null) records.putAll(value);
        } catch (Exception e) {
            BottyMod.LOG.error(">>> [Bot] 读取 QQ 绑定缓存失败", e);
        }
    }

    private synchronized void save() {
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            new Gson().toJson(records, writer);
            writer.close();
        } catch (Exception e) {
            BottyMod.LOG.error(">>> [Bot] 保存 QQ 绑定缓存失败", e);
        }
    }

    public synchronized void requestBind(EntityPlayerMP player, long qq) {
        load();
        BindingRecord record = records.get(
            player.getUniqueID()
                .toString());
        if (record == null) record = new BindingRecord();
        record.playerName = player.getCommandSenderName();
        record.uuid = player.getUniqueID()
            .toString();
        record.qq = qq;
        record.confirmed = false;
        if (record.groupNickname == null) record.groupNickname = "";
        records.put(record.uuid, record);
        save();
        BotClient.getInstance()
            .sendMessageToQQ("[绑定请求] [CQ:at,qq=" + qq + "] 请在群内回复 !bind " + record.playerName + " 确认绑定 Minecraft 角色。");
        player.addChatMessage(new ChatComponentText("§a[Bot] 已发起绑定请求，请到QQ群内回复 !bind " + record.playerName + " 完成确认。"));
    }

    public synchronized BindingRecord confirmBind(String player, long qq, String nickname) {
        load();
        for (BindingRecord record : records.values()) if (!record.confirmed && record.qq == qq
            && record.playerName != null
            && record.playerName.equalsIgnoreCase(player)) {
                record.confirmed = true;
                record.groupNickname = sanitize(nickname);
                save();
                return record;
            }
        return null;
    }

    public synchronized boolean isConfirmed(EntityPlayerMP player) {
        load();
        BindingRecord record = records.get(
            player.getUniqueID()
                .toString());
        return record != null && record.confirmed;
    }

    public synchronized String getChatDisplayName(EntityPlayerMP player) {
        load();
        BindingRecord record = records.get(
            player.getUniqueID()
                .toString());
        return hasNickname(record) ? "[" + record.groupNickname + "] " + player.getCommandSenderName()
            : player.getCommandSenderName();
    }

    public synchronized IChatComponent getTabDisplayName(EntityPlayerMP player) {
        load();
        BindingRecord record = records.get(
            player.getUniqueID()
                .toString());
        return hasNickname(record)
            ? new ChatComponentText("§b[" + record.groupNickname + "] §f" + player.getCommandSenderName())
            : null;
    }

    public void applyTabPrefix(EntityPlayerMP player) {
        player.refreshDisplayName();
    }

    public void sendBindReminder(EntityPlayerMP player) {
        if (isConfirmed(player)) return;
        ChatComponentText tip = new ChatComponentText("§e你还没有绑定QQ号, 点击");
        ChatComponentText button = new ChatComponentText("[绑定]");
        button.setChatStyle(
            new ChatStyle().setColor(net.minecraft.util.EnumChatFormatting.GREEN)
                .setUnderlined(true)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/qqbind "))
                .setChatHoverEvent(
                    new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText("§7点击后输入QQ号，例如 /qqbind 123456789"))));
        tip.appendSibling(button)
            .appendSibling(new ChatComponentText("§e后输入QQ号可获得头衔"));
        player.addChatMessage(tip);
    }

    private static boolean hasNickname(BindingRecord record) {
        return record != null && record.confirmed && record.groupNickname != null && !record.groupNickname.isEmpty();
    }

    private String sanitize(String value) {
        if (value == null) return "";
        value = value.replaceAll("[§\\[\\]\\r\\n\\t]", "")
            .trim();
        return value.length() > 32 ? value.substring(0, 32) : value;
    }
}
