package com.bot.aibot.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class BotConfig {

    // --- 服务端/通用配置 (aibot-common.toml) ---
    public static final ServerConfig SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    // --- 【新增】客户端配置 (aibot-client.toml) ---
    public static final ClientConfig CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        // 初始化服务端配置
        final Pair<ServerConfig, ForgeConfigSpec> serverPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = serverPair.getRight();
        SERVER = serverPair.getLeft();

        // 【新增】初始化客户端配置
        final Pair<ClientConfig, ForgeConfigSpec> clientPair = new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = clientPair.getRight();
        CLIENT = clientPair.getLeft();
    }

    /**
     * 服务端配置类 (存全局设置)
     */
    public static class ServerConfig {
        public final ForgeConfigSpec.ConfigValue<String> wsUrl;
        public final ForgeConfigSpec.ConfigValue<List<? extends Number>> groupIds;
        public final ForgeConfigSpec.ConfigValue<Long> targetBotId;

        public final ForgeConfigSpec.BooleanValue enableChatSync;
        public final ForgeConfigSpec.BooleanValue enableJoinLeave;
        public final ForgeConfigSpec.BooleanValue enableDeath;
        public final ForgeConfigSpec.ConfigValue<String> mcPrefix;
        public final ForgeConfigSpec.BooleanValue enableAdvancement;

        public final ForgeConfigSpec.BooleanValue enableAI;
        public final ForgeConfigSpec.ConfigValue<String> aiApiUrl;
        public final ForgeConfigSpec.ConfigValue<String> aiApiKey;
        public final ForgeConfigSpec.ConfigValue<String> aiModelName;
        public final ForgeConfigSpec.ConfigValue<String> aiPrompt;
        public final ForgeConfigSpec.ConfigValue<String> aiTriggerPrefix;
        public final ForgeConfigSpec.ConfigValue<String> aiDeathMode;
        public final ForgeConfigSpec.ConfigValue<String> aiDeathPrompt;

        // 移除了 neteaseCookie！因为它不应该在服务端！

        public final ForgeConfigSpec.ConfigValue<String> joinMsgFormat;
        public final ForgeConfigSpec.ConfigValue<String> leaveMsgFormat;
        public final ForgeConfigSpec.ConfigValue<String> deathMsgFormat;
        public final ForgeConfigSpec.ConfigValue<String> chatMsgFormat;
        public final ForgeConfigSpec.ConfigValue<String> advancementMsgFormat;
        public final ForgeConfigSpec.ConfigValue<String> startMsgFormat;

        public ServerConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Bot 基础连接配置").push("general");
            wsUrl = builder.define("ws_url", "ws://127.0.0.1:3001");
            groupIds = builder.defineList("group_ids", Arrays.asList(0L), o -> o instanceof Number);
            targetBotId = builder.define("target_bot_id", 0L);
            builder.pop();

            builder.comment("功能开关").push("features");
            enableChatSync = builder.define("enable_chat_sync", true);
            enableJoinLeave = builder.define("enable_join_leave", true);
            enableDeath = builder.define("enable_death", true);
            enableAdvancement = builder.define("enable_advancement", true);
            mcPrefix = builder.comment("服务器前缀").define("mc_prefix", "Server");
            builder.pop();

            builder.comment("AI 配置").push("ai_features");
            enableAI = builder.define("enable_ai", false);
            aiApiUrl = builder.define("api_url", "https://api.deepseek.com/chat/completions");
            aiApiKey = builder.define("api_key", "sk-xxxxxxxx");
            aiModelName = builder.define("model_name", "deepseek-chat");
            aiPrompt = builder.define("system_prompt", "你是一个 Minecraft 助手...");
            aiTriggerPrefix = builder.define("trigger_prefix", "bot ");
            aiDeathMode = builder.defineInList("ai_death_mode", "HYBRID", Arrays.asList("OFF", "HYBRID", "AI_ONLY"));
            aiDeathPrompt = builder.define("ai_death_prompt", "无情嘲讽玩家...");
            builder.pop();

            builder.comment("消息格式").push("messages");
            advancementMsgFormat = builder.define("advancement_msg", "%player% 取得了进度 [%advancement%]");
            joinMsgFormat = builder.define("join_msg", "%player% 加入了服务器！");
            leaveMsgFormat = builder.define("leave_msg", "%player% 离开了服务器。");
            deathMsgFormat = builder.define("death_msg", "%msg%");
            chatMsgFormat = builder.define("chat_format", "[%prefix%] %player%: %msg%");
            startMsgFormat = builder.define("start_msg", "[%prefix%] Bot 已连接！");
            builder.pop();
        }
    }

    /**
     * 【新增】客户端配置类 (存个人隐私数据)
     */
    public static class ClientConfig {
        public final ForgeConfigSpec.ConfigValue<String> neteaseCookie;

        public ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("客户端设置 (个人数据)").push("client");

            neteaseCookie = builder.comment("网易云音乐 Cookie (自动保存，请勿泄露)")
                    .define("netease_cookie", "");

            builder.pop();
        }
    }

    /**
     * 重载配置 (主要用于服务端热重载)
     */
    public static void refresh() {
        try {
            // 只重载服务端配置，客户端配置通常不需要热重载
            Path path = FMLPaths.CONFIGDIR.get().resolve("aibot-common.toml");
            CommentedFileConfig configData = CommentedFileConfig.builder(path).sync().build();
            configData.load();
            SERVER_SPEC.setConfig(configData);
            System.out.println(">>> [Bot] 配置文件已刷新");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}