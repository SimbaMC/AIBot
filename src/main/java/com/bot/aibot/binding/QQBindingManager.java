package com.bot.aibot.binding;

import com.bot.aibot.network.BotClient;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.NoSuchFileException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class QQBindingManager {
    public enum BindStatus {
        PENDING,
        CONFIRMED
    }

    public static class BindingRecord {
        public String playerName;
        public String uuid;
        public long qq;
        public String groupNickname;
        public BindStatus status;
    }

    private static final QQBindingManager INSTANCE = new QQBindingManager();
    private static final Type MAP_TYPE = new TypeToken<Map<String, BindingRecord>>() {}.getType();

    public static QQBindingManager getInstance() {
        return INSTANCE;
    }

    private final Gson gson = new Gson();
    private final Path filePath = FMLPaths.CONFIGDIR.get().resolve("aibot-qq-bindings.json");
    private final Map<String, BindingRecord> records = new LinkedHashMap<>();
    private boolean loaded = false;

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        try (Reader reader = Files.newBufferedReader(filePath)) {
            Map<String, BindingRecord> data = gson.fromJson(reader, MAP_TYPE);
            if (data != null) {
                records.clear();
                records.putAll(data);
            }
        } catch (NoSuchFileException ignored) {
        } catch (JsonSyntaxException e) {
            System.err.println(">>> [Bot] QQ 绑定缓存格式错误: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println(">>> [Bot] 读取 QQ 绑定缓存失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                gson.toJson(records, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            System.err.println(">>> [Bot] 保存 QQ 绑定缓存失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized void requestBind(ServerPlayer player, long qq) {
        ensureLoaded();

        String key = player.getUUID().toString();
        BindingRecord record = records.getOrDefault(key, new BindingRecord());
        record.playerName = player.getName().getString();
        record.uuid = key;
        record.qq = qq;
        record.status = BindStatus.PENDING;
        if (record.groupNickname == null) {
            record.groupNickname = "";
        }
        records.put(key, record);
        save();

        BotClient.getInstance().sendMessageToQQ(String.format(
                "[绑定请求] [CQ:at,qq=%d] 请在群内回复 !bind %s 确认绑定 Minecraft 角色。",
                qq, record.playerName));
    }

    public synchronized Optional<BindingRecord> confirmBind(String playerName, long qq, String groupNickname) {
        ensureLoaded();

        for (BindingRecord record : records.values()) {
            if (record.status == BindStatus.PENDING
                    && record.qq == qq
                    && record.playerName != null
                    && record.playerName.equalsIgnoreCase(playerName)) {
                record.status = BindStatus.CONFIRMED;
                record.groupNickname = sanitizeNickname(groupNickname);
                save();
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    public synchronized BindStatus getStatus(UUID uuid) {
        ensureLoaded();
        BindingRecord record = records.get(uuid.toString());
        if (record == null) return null;
        return record.status;
    }

    public synchronized String getChatDisplayName(ServerPlayer player) {
        ensureLoaded();
        String playerName = player.getName().getString();
        BindingRecord record = records.get(player.getUUID().toString());
        if (record == null || record.status != BindStatus.CONFIRMED || record.groupNickname == null || record.groupNickname.isEmpty()) {
            return playerName;
        }
        return "[" + record.groupNickname + "] " + playerName;
    }

    public synchronized void applyTabPrefix(ServerPlayer player) {
        ensureLoaded();
        BindingRecord record = records.get(player.getUUID().toString());
        if (record == null || record.status != BindStatus.CONFIRMED || record.groupNickname == null || record.groupNickname.isEmpty()) {
            player.setTabListDisplayName(null);
            return;
        }
        player.setTabListDisplayName(Component.literal("§b[" + record.groupNickname + "] §f" + player.getName().getString()));
    }

    public void sendBindReminder(ServerPlayer player) {
        MutableComponent tip = Component.literal("§e你还没有绑定QQ号, 点击");
        MutableComponent bindButton = Component.literal("[绑定]");
        bindButton.setStyle(Style.EMPTY
                .withColor(0x55FF55)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/qqbind "))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§7点击后输入QQ号，例如 /qqbind 123456789"))));
        tip.append(bindButton).append(Component.literal("§e后输入QQ号可获得头衔"));
        player.sendSystemMessage(tip);
    }

    private String sanitizeNickname(String nickname) {
        if (nickname == null) return "";
        String sanitized = nickname
                .replace("§", "")
                .replace("[", "")
                .replace("]", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace("\t", "")
                .trim();
        if (sanitized.length() > 32) {
            sanitized = sanitized.substring(0, 32);
        }
        return sanitized;
    }
}
