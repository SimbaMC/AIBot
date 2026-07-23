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
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;

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
            BottyMod.LOG.error("Cannot load QQ bindings", e);
        }
    }

    private synchronized void save() {
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            new Gson().toJson(records, writer);
            writer.close();
        } catch (Exception e) {
            BottyMod.LOG.error("Cannot save QQ bindings", e);
        }
    }

    public synchronized void requestBind(EntityPlayerMP player, long qq) {
        load();
        BindingRecord r = new BindingRecord();
        r.playerName = player.getCommandSenderName();
        r.uuid = player.getUniqueID()
            .toString();
        r.qq = qq;
        records.put(r.uuid, r);
        save();
        BotClient.getInstance()
            .sendMessageToQQ("[绑定请求] [CQ:at,qq=" + qq + "] reply !bind " + r.playerName);
    }

    public synchronized void confirmBind(String player, long qq, String nickname) {
        load();
        for (BindingRecord r : records.values())
            if (!r.confirmed && r.qq == qq && r.playerName.equalsIgnoreCase(player)) {
                r.confirmed = true;
                r.groupNickname = sanitize(nickname);
                save();
                BotClient.getInstance()
                    .sendMessageToQQ("[绑定成功] " + r.playerName + " -> " + r.groupNickname);
                return;
            }
        BotClient.getInstance()
            .sendMessageToQQ("[绑定失败] 请先在游戏内执行 /qqbind QQ号");
    }

    public synchronized String getChatDisplayName(EntityPlayerMP player) {
        load();
        BindingRecord r = records.get(
            player.getUniqueID()
                .toString());
        return r != null && r.confirmed ? "[" + r.groupNickname + "] " + player.getCommandSenderName()
            : player.getCommandSenderName();
    }

    public void applyTabPrefix(EntityPlayerMP player) {
        player.refreshDisplayName();
    }

    public void sendBindReminder(EntityPlayerMP player) {
        load();
        BindingRecord r = records.get(
            player.getUniqueID()
                .toString());
        if (r != null && r.confirmed) return;
        ChatComponentText msg = new ChatComponentText("§eQQ未绑定，点击 §a[绑定]");
        msg.setChatStyle(
            new ChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/qqbind ")));
        player.addChatMessage(msg);
    }

    private String sanitize(String s) {
        if (s == null) return "QQ";
        s = s.replaceAll("[§\\[\\]\\r\\n\\t]", "")
            .trim();
        return s.length() > 32 ? s.substring(0, 32) : s;
    }
}
