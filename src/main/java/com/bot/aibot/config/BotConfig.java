package com.bot.aibot.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class BotConfig {

    // Forge 配置的标准样板代码
    public static final ServerConfig SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    public static class ServerConfig {
        // --- 基础连接设置 ---
        public final ForgeConfigSpec.ConfigValue<String> wsUrl;
        public final ForgeConfigSpec.ConfigValue<List<? extends Number>> groupIds;
        public final ForgeConfigSpec.ConfigValue<Long> targetBotId;

        // --- 功能开关 ---
        public final ForgeConfigSpec.BooleanValue enableChatSync;
        public final ForgeConfigSpec.BooleanValue enableJoinLeave;
        public final ForgeConfigSpec.BooleanValue enableDeath;
        public final ForgeConfigSpec.ConfigValue<String> mcPrefix;

        // 【新增】成就开关
        public final ForgeConfigSpec.BooleanValue enableAdvancement;

        // --- AI 功能设置 ---
        public final ForgeConfigSpec.BooleanValue enableAI;
        public final ForgeConfigSpec.ConfigValue<String> aiApiUrl;
        public final ForgeConfigSpec.ConfigValue<String> aiApiKey;
        public final ForgeConfigSpec.ConfigValue<String> aiModelName;
        public final ForgeConfigSpec.ConfigValue<String> aiPrompt;
        public final ForgeConfigSpec.ConfigValue<String> aiTriggerPrefix;

        // --- 自定义消息模板 ---
        public final ForgeConfigSpec.ConfigValue<String> joinMsgFormat;
        public final ForgeConfigSpec.ConfigValue<String> leaveMsgFormat;
        public final ForgeConfigSpec.ConfigValue<String> deathMsgFormat;
        public final ForgeConfigSpec.ConfigValue<String> chatMsgFormat;
        // 【新增】成就消息模板
        public final ForgeConfigSpec.ConfigValue<String> advancementMsgFormat;

        // 【新增】启动消息格式
        public final ForgeConfigSpec.ConfigValue<String> startMsgFormat;

        // 【新增】AI 死亡播报配置
        // 【修改】从 Boolean 改为 String 配置，提供三个选项
        public final ForgeConfigSpec.ConfigValue<String> aiDeathMode;
        public final ForgeConfigSpec.ConfigValue<String> aiDeathPrompt;


        public ServerConfig(ForgeConfigSpec.Builder builder) {
            // 1. General (基础设置)
            builder.comment("Bot 基础连接配置").push("general");
            wsUrl = builder.define("ws_url", "ws://127.0.0.1:3001");
            groupIds = builder.defineList("group_ids", Arrays.asList(0L), o -> o instanceof Number);
            targetBotId = builder.define("target_bot_id", 0L);
            builder.pop();

            // 2. Features (功能开关)
            builder.comment("功能开关").push("features");
            enableChatSync = builder.define("enable_chat_sync", true);
            enableJoinLeave = builder.define("enable_join_leave", true);
            enableDeath = builder.define("enable_death", true);
            // 成就开关在这里
            enableAdvancement = builder.define("enable_advancement", true);
            mcPrefix = builder.comment("服务器前缀 (对应变量 %prefix%)").define("mc_prefix", "Server");
            builder.pop();

            // 3. AI (AI 设置)
            builder.comment("AI 智能对话配置").push("ai_features");
            enableAI = builder.define("enable_ai", false);
            aiApiUrl = builder.define("api_url", "https://api.deepseek.com/chat/completions");
            aiApiKey = builder.define("api_key", "sk-xxxxxxxxxxxxxxxxxxxx");
            aiModelName = builder.define("model_name", "deepseek-chat");
            aiPrompt = builder.define("system_prompt", "你是一个 Minecraft 服务器的助手...");
            aiTriggerPrefix = builder.define("trigger_prefix", "bot ");
            // 【修改】AI 死亡播报模式
            // OFF: 关闭 (仅使用本地)
            // HYBRID: 混合 (优先本地，未翻译的才用 AI)
            // AI_ONLY: AI 独占 (完全接管，忽略本地)
            aiDeathMode = builder.comment("AI 死亡翻译模式 [OFF, HYBRID, AI_ONLY]")
                    .defineInList("ai_death_mode", "HYBRID", Arrays.asList("OFF", "HYBRID", "AI_ONLY"));
            aiDeathPrompt = builder.comment("AI 翻译死亡消息的提示词 (System Prompt)")
                    .define("ai_death_prompt", "你是一个Minecraft死亡播报员。请把用户的死亡消息翻译成幽默风趣的中文，并且无情的嘲讽玩家，不要解释，直接输出翻译结果。");
            builder.pop();

            // 4. Messages (消息文案 - 已合并)
            builder.comment("自定义消息格式",
                            "可用变量:",
                            "%player% - 玩家名",
                            "%msg% - 聊天内容/死亡信息",
                            "%prefix% - 服务器前缀",
                            "%advancement% - 成就标题",
                            "%desc% - 成就描述")
                    .push("messages");

            // --- 在这里统一初始化所有消息模板 ---

            advancementMsgFormat = builder.comment("玩家达成成就提示")
                    .define("advancement_msg", "%player% 取得了进度 [%advancement%]");

            joinMsgFormat = builder.comment("玩家加入提示")
                    .define("join_msg", "%player% 加入了服务器！");

            leaveMsgFormat = builder.comment("玩家离开提示")
                    .define("leave_msg", "%player% 离开了服务器。");

            deathMsgFormat = builder.comment("玩家死亡提示 (%msg% 是具体的死法，如'Dev被僵尸杀死了')")
                    .define("death_msg", "%msg%");

            chatMsgFormat = builder.comment("聊天转发格式")
                    .define("chat_format", "[%prefix%] %player%: %msg%");

            startMsgFormat = builder.comment("Bot 连接成功/服务器启动提示")
                    .define("start_msg", "[%prefix%] 服务器已连接，Bot 正在运行！");

            builder.pop(); // 只需要这一个 pop
        }
    }

    // 强制重载配置文件的逻辑
    public static void refresh() {
        try {
            // 1. 获取配置文件路径 (run/config/bottymod-common.toml)
            Path path = FMLPaths.CONFIGDIR.get().resolve("bottymod-common.toml");
            System.out.println(">>> [Bot] 正在重载配置文件: " + path.toString());

            // 2. 使用 NightConfig 强制读取硬盘文件
            CommentedFileConfig configData = CommentedFileConfig.builder(path)
                    .sync() // 同步
                    .build();

            // 3. 加载并设置给 Spec (这就把硬盘的值刷进内存了)
            configData.load();
            SERVER_SPEC.setConfig(configData);

            System.out.println(">>> [Bot] 配置文件重载成功！");

        } catch (Exception e) {
            System.out.println(">>> [Bot] 配置文件重载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}