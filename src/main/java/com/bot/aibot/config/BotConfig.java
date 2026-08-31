package com.bot.aibot.config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;

public final class BotConfig {

    public static String wsUrl, accessToken, mcPrefix, aiApiUrl, aiApiKey, aiModelName, aiPrompt, aiTriggerPrefix,
        aiDeathMode, aiDeathPrompt, joinMsgFormat, leaveMsgFormat, deathMsgFormat, chatMsgFormat, advancementMsgFormat,
        startMsgFormat, qqFaceApi, defaultNodeName, geoIpDatabase;
    public static String[] nodeMappings;
    public static long[] groupIds;
    public static long targetBotId;
    public static boolean reconnectEnabled, enableChatSync, enableJoinLeave, enableDeath, enableAdvancement, enableAI;
    public static int reconnectInitialInterval, reconnectMaxInterval, broadcastCooldown;
    public static double reconnectMultiplier;
    private static Configuration server;

    private BotConfig() {}

    public static void init(File configDir) {
        server = new Configuration(new File(configDir, "aibot.cfg"));
        load();
    }

    public static synchronized void load() {
        server.load();
        nodeMappings = server.getStringList(
            "nodeMappings",
            "Status_Command_Settings",
            new String[] { "127.0.0.1:本地节点" },
            "节点 IP 映射配置。格式为 'IP:节点名'");
        defaultNodeName = server.getString("defaultNodeName", "Status_Command_Settings", "直连", "当玩家 IP 不在映射列表中时显示的名称");
        geoIpDatabase = server.getString(
            "geoIpDatabase",
            "Status_Command_Settings",
            "aibot/GeoLite2-Country.mmdb",
            "GeoIP 国家数据库路径（相对于 config 目录，留空则禁用）");
        wsUrl = server.getString("ws_url", "general", "ws://127.0.0.1:3001", "WebSocket URL");
        accessToken = server.getString("access_token", "general", "", "NapCat/OneBot 鉴权 Token（未开启请留空）");
        groupIds = parseLongs(server.getStringList("group_ids", "general", new String[] { "0" }, "QQ群号列表"));
        targetBotId = parseLong(server.getString("target_bot_id", "general", "0", "目标机器人Q号"));
        reconnectEnabled = server.getBoolean("reconnect_enabled", "general", true, "是否在断开连接后自动重连");
        reconnectInitialInterval = server.getInt("reconnect_initial_interval", "general", 5, 1, 3600, "初始重连间隔（秒）");
        reconnectMultiplier = server.getFloat("reconnect_multiplier", "general", 2.0f, 1.0f, 10.0f, "重连间隔指数退避倍率");
        reconnectMaxInterval = server.getInt("reconnect_max_interval", "general", 300, 1, 86400, "最大重连间隔上限（秒）");
        qqFaceApi = server.getString(
            "qq_face_api",
            "general",
            "https://koishi.js.org/QFace/assets/qq_emoji/%s/png/%s.png",
            "QQ表情源码地址（必须包含 %s）");
        enableChatSync = server.getBoolean("enable_chat_sync", "features", true, "开启群聊同步");
        enableJoinLeave = server.getBoolean("enable_join_leave", "features", true, "开启加入/离开消息播报");
        enableDeath = server.getBoolean("enable_death", "features", true, "开启死亡消息播报");
        enableAdvancement = server.getBoolean("enable_advancement", "features", true, "开启成就消息播报");
        broadcastCooldown = server.getInt("broadcast_cooldown", "features", 600, 0, 3600, "全服广播音乐冷却时间");
        mcPrefix = server.getString("mc_prefix", "features", "Server", "服务器前缀");
        enableAI = server.getBoolean("enable_ai", "ai_features", false, "开启AI聊天功能");
        aiApiUrl = server
            .getString("api_url", "ai_features", "https://api.deepseek.com/chat/completions", "OpenAI兼容接口");
        aiApiKey = server.getString("api_key", "ai_features", "sk-xxxxxxxx", "API Key");
        aiModelName = server.getString("model_name", "ai_features", "deepseek-chat", "模型名称");
        aiPrompt = server.getString("system_prompt", "ai_features", "你是一个minecraft服务器助手...", "AI个性提示词");
        aiTriggerPrefix = server.getString("trigger_prefix", "ai_features", "bot ", "AI触发词");
        aiDeathMode = server.getString("ai_death_mode", "ai_features", "HYBRID", "OFF、HYBRID 或 AI_ONLY");
        aiDeathPrompt = server.getString("ai_death_prompt", "ai_features", "无情的嘲讽玩家...", "AI死亡播报风格提示词");
        advancementMsgFormat = server
            .getString("advancement_msg", "messages", "%player% 获得了成就 [%advancement%]", "成就播报消息格式");
        joinMsgFormat = server.getString("join_msg", "messages", "%player% 加入了服务器!", "加入消息格式");
        leaveMsgFormat = server.getString("leave_msg", "messages", "%player% 离开了服务器.", "离开消息格式");
        deathMsgFormat = server.getString("death_msg", "messages", "%msg%", "死亡消息播报格式");
        chatMsgFormat = server.getString("chat_format", "messages", "[%prefix%] %player%: %msg%", "聊天消息播报格式");
        startMsgFormat = server.getString("start_msg", "messages", "[%prefix%] 群服互联已连接!", "服务器连接通知");
        migrateBackportDefaults();
        if (server.hasChanged()) server.save();
    }

    private static void migrateBackportDefaults() {
        joinMsgFormat = migrate("join_msg", joinMsgFormat, "%player% joined the server!", "%player% 加入了服务器!");
        leaveMsgFormat = migrate("leave_msg", leaveMsgFormat, "%player% left the server.", "%player% 离开了服务器.");
        advancementMsgFormat = migrate(
            "achievement_msg",
            advancementMsgFormat,
            "%player% earned [%achievement%]",
            "%player% 获得了成就 [%advancement%]");
        startMsgFormat = migrate("start_msg", startMsgFormat, "[%prefix%] QQ bridge connected!", "[%prefix%] 群服互联已连接!");
        aiPrompt = migrate("system_prompt", aiPrompt, "You are a Minecraft server assistant.", "你是一个minecraft服务器助手...");
        aiDeathPrompt = migrate(
            "ai_death_prompt",
            aiDeathPrompt,
            "Translate this Minecraft death message into concise Chinese.",
            "无情的嘲讽玩家...");
    }

    private static String migrate(String key, String current, String oldDefault, String restoredDefault) {
        if (!oldDefault.equals(current)) return current;
        String category = key.startsWith("ai_") || "system_prompt".equals(key) ? "ai_features" : "messages";
        server.get(category, key, restoredDefault)
            .set(restoredDefault);
        return restoredDefault;
    }

    public static Map<String, String> getNodeMappings() {
        Map<String, String> result = new HashMap<String, String>();
        for (String mapping : nodeMappings) {
            String[] parts = mapping.split(":", 2);
            if (parts.length == 2) result.put(parts[0].trim(), parts[1].trim());
        }
        return result;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static long[] parseLongs(String[] values) {
        long[] result = new long[values.length];
        for (int i = 0; i < values.length; i++) result[i] = parseLong(values[i]);
        return result;
    }
}
