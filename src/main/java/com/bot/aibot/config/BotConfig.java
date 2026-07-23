package com.bot.aibot.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class BotConfig {

    public static String wsUrl, accessToken, mcPrefix, aiApiUrl, aiApiKey, aiModelName, aiPrompt, aiTriggerPrefix,
        aiDeathMode, aiDeathPrompt, joinMsgFormat, leaveMsgFormat, deathMsgFormat, chatMsgFormat, advancementMsgFormat,
        startMsgFormat, qqFaceApi, neteaseCookie;
    public static long[] groupIds;
    public static long targetBotId;
    public static boolean reconnectEnabled, enableChatSync, enableJoinLeave, enableDeath, enableAdvancement, enableAI;
    public static int reconnectInitialInterval, reconnectMaxInterval, broadcastCooldown;
    public static double reconnectMultiplier;
    private static Configuration server, client;

    private BotConfig() {}

    public static void init(File configDir) {
        server = new Configuration(new File(configDir, "aibot.cfg"));
        client = new Configuration(new File(configDir, "aibot-client.cfg"));
        load();
    }

    public static synchronized void load() {
        server.load();
        wsUrl = server.getString("ws_url", "general", "ws://127.0.0.1:3001", "OneBot 11 reverse WebSocket URL");
        accessToken = server.getString("access_token", "general", "", "Bearer token; leave empty when disabled");
        groupIds = parseLongs(
            server.getStringList("group_ids", "general", new String[] { "0" }, "Allowed QQ group IDs"));
        targetBotId = parseLong(
            server.getString("target_bot_id", "general", "0", "Expected OneBot self_id; 0 accepts all"));
        reconnectEnabled = server.getBoolean("reconnect_enabled", "general", true, "Reconnect automatically");
        reconnectInitialInterval = server
            .getInt("reconnect_initial_interval", "general", 5, 1, 3600, "Initial reconnect delay in seconds");
        reconnectMultiplier = server
            .getFloat("reconnect_multiplier", "general", 2.0f, 1.0f, 10.0f, "Reconnect backoff multiplier");
        reconnectMaxInterval = server
            .getInt("reconnect_max_interval", "general", 300, 1, 86400, "Maximum reconnect delay");
        qqFaceApi = server.getString(
            "qq_face_api",
            "general",
            "https://koishi.js.org/QFace/assets/qq_emoji/%s/png/%s.png",
            "QQ face URL template");
        enableChatSync = server.getBoolean("enable_chat_sync", "features", true, "Synchronize chat");
        enableJoinLeave = server.getBoolean("enable_join_leave", "features", true, "Synchronize joins and leaves");
        enableDeath = server.getBoolean("enable_death", "features", true, "Synchronize deaths");
        enableAdvancement = server.getBoolean("enable_achievement", "features", true, "Synchronize achievements");
        enableAI = server.getBoolean("enable_ai", "ai", false, "Enable OpenAI-compatible chat/death translation");
        broadcastCooldown = server
            .getInt("broadcast_cooldown", "features", 600, 0, 3600, "Global music cooldown in seconds");
        mcPrefix = server.getString("mc_prefix", "messages", "Server", "Server label");
        joinMsgFormat = server.getString("join_msg", "messages", "%player% joined the server!", "Join message");
        leaveMsgFormat = server.getString("leave_msg", "messages", "%player% left the server.", "Leave message");
        deathMsgFormat = server.getString("death_msg", "messages", "%msg%", "Death message");
        chatMsgFormat = server.getString("chat_format", "messages", "[%prefix%] %player%: %msg%", "Chat message");
        advancementMsgFormat = server
            .getString("achievement_msg", "messages", "%player% earned [%achievement%]", "Achievement message");
        startMsgFormat = server
            .getString("start_msg", "messages", "[%prefix%] QQ bridge connected!", "Connection message");
        aiApiUrl = server
            .getString("api_url", "ai", "https://api.deepseek.com/chat/completions", "OpenAI-compatible endpoint");
        aiApiKey = server.getString("api_key", "ai", "", "API key");
        aiModelName = server.getString("model_name", "ai", "deepseek-chat", "Model name");
        aiPrompt = server
            .getString("system_prompt", "ai", "You are a Minecraft server assistant.", "Chat system prompt");
        aiTriggerPrefix = server.getString("trigger_prefix", "ai", "bot ", "AI chat trigger");
        aiDeathMode = server.getString("ai_death_mode", "ai", "HYBRID", "OFF, HYBRID, or AI_ONLY");
        aiDeathPrompt = server.getString(
            "ai_death_prompt",
            "ai",
            "Translate this Minecraft death message into concise Chinese.",
            "Death translation prompt");
        if (server.hasChanged()) server.save();
        client.load();
        neteaseCookie = client.getString("netease_cookie", "client", "", "Reserved for NetEase API authentication");
        if (client.hasChanged()) client.save();
    }

    public static synchronized void saveClientCookie(String cookie) {
        neteaseCookie = cookie == null ? "" : cookie.trim();
        client.get("client", "netease_cookie", "")
            .set(neteaseCookie);
        client.save();
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static long[] parseLongs(String[] values) {
        long[] result = new long[values.length];
        for (int i = 0; i < values.length; i++) result[i] = parseLong(values[i]);
        return result;
    }
}
