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

    public static final ServerConfig SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    public static final ClientConfig CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        final Pair<ServerConfig, ForgeConfigSpec> serverPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = serverPair.getRight();
        SERVER = serverPair.getLeft();

        final Pair<ClientConfig, ForgeConfigSpec> clientPair = new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = clientPair.getRight();
        CLIENT = clientPair.getLeft();
    }

    public static class ServerConfig {
        public final ForgeConfigSpec.ConfigValue<String> wsUrl;
        public final ForgeConfigSpec.ConfigValue<List<? extends Number>> groupIds;
        public final ForgeConfigSpec.ConfigValue<Long> targetBotId;
        public final ForgeConfigSpec.ConfigValue<String> accessToken;

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

        public final ForgeConfigSpec.IntValue broadcastCooldown;

        public final ForgeConfigSpec.ConfigValue<String> joinMsgFormat;
        public final ForgeConfigSpec.ConfigValue<String> leaveMsgFormat;
        public final ForgeConfigSpec.ConfigValue<String> deathMsgFormat;
        public final ForgeConfigSpec.ConfigValue<String> chatMsgFormat;
        public final ForgeConfigSpec.ConfigValue<String> advancementMsgFormat;
        public final ForgeConfigSpec.ConfigValue<String> startMsgFormat;

        public final ForgeConfigSpec.ConfigValue<String> qqFaceApi;

        public ServerConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("bot链接配置").push("general");
            wsUrl = builder.comment("WebSocket URL")
                    .define("ws_url", "ws://127.0.0.1:3001");
            accessToken = builder.comment("NapCat/OneBot 鉴权 Token (如果未开启鉴权请留空)")
                    .define("access_token", "");
            groupIds = builder.comment("QQ群号列表")
                    .defineList("group_ids", Arrays.asList(0L), o -> o instanceof Number);
            targetBotId = builder.comment("目标机器人Q号")
                    .define("target_bot_id", 0L);
            qqFaceApi = builder.comment("QQ表情源码地址 (必须包含 %s)")
                    .define("qq_face_api", "https://github.com/koishijs/QFace/blob/master/public/gif/%s.gif");
            builder.pop();

            builder.comment("Features").push("features");
            enableChatSync = builder.comment("开启群聊同步")
                    .define("enable_chat_sync", true);
            enableJoinLeave = builder.comment("开启加入/离开消息播报")
                    .define("enable_join_leave", true);
            enableDeath = builder.comment("开启死亡消息播报")
                    .define("enable_death", true);
            enableAdvancement = builder.comment("开启成就消息播报")
                    .define("enable_advancement", true);
            mcPrefix = builder.comment("服务器前缀").define("mc_prefix", "Server");
            broadcastCooldown = builder
                    .comment("全服广播音乐冷却时间")
                    .defineInRange("broadcast_cooldown", 600, 0, 3600);//默认600秒
            builder.pop();

            builder.comment("AI 设置").push("ai_features");
            enableAI = builder.comment("开启AI聊天功能")
                    .define("enable_ai", false);
            aiApiUrl = builder.define("api_url", "https://api.deepseek.com/chat/completions");
            aiApiKey = builder.define("api_key", "sk-xxxxxxxx");
            aiModelName = builder.define("model_name", "deepseek-chat");
            aiPrompt = builder.comment("AI个性提示词")
                    .define("system_prompt", "你是一个minecraft服务器助手...");
            aiTriggerPrefix = builder.comment("AI触发词")
                    .define("trigger_prefix", "bot ");//例子：玩家在聊天框中输入 bot 我想导管子。 ---即可触发ai聊天
            aiDeathMode = builder.comment("AI死亡播报模式----OFF为关闭,HYBRID为混合（即优先加载已有汉化的死亡播报，如无汉化则使用ai翻译）,AI_ONLY为仅使用AI翻译")
                    .defineInList("ai_death_mode", "HYBRID", Arrays.asList("OFF", "HYBRID", "AI_ONLY"));
            aiDeathPrompt = builder.comment("AI死亡播报风格提示词")
                    .define("ai_death_prompt", "无情的嘲讽玩家...");
            builder.pop();

            builder.comment("消息设置").push("messages");
            advancementMsgFormat = builder.comment("成就播报消息格式")
                    .define("advancement_msg", "%player% 获得了成就 [%advancement%]");
            joinMsgFormat = builder.comment("加入消息格式")
                    .define("join_msg", "%player% 加入了服务器!");
            leaveMsgFormat = builder.comment("离开消息格式")
                    .define("leave_msg", "%player% 离开了服务器.");
            deathMsgFormat = builder.comment("死亡消息播报格式-------不加任何文字即为播放游戏中弹出的死亡消息")
                    .define("death_msg", "%msg%");
            chatMsgFormat = builder.comment("聊天消息播报格式---默认格式例子：[Server]玩家名:消息")
                    .define("chat_format", "[%prefix%] %player%: %msg%");
            startMsgFormat = builder.comment("服务器连接通知")
                    .define("start_msg", "[%prefix%] 群服互联已连接!");
            builder.pop();
        }
    }

    public static class ClientConfig {
        public final ForgeConfigSpec.ConfigValue<String> neteaseCookie;

        public ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Client Settings").push("client");
            neteaseCookie = builder.comment("网易云账号Cookie")
                    .define("netease_cookie", "");
            builder.pop();
        }
    }

    public static void refresh() {
        try {
            Path serverPath = FMLPaths.CONFIGDIR.get().resolve("aibot-common.toml");
            System.out.println(">>> [Bot] 正在重新加载服务器配置： " + serverPath);

            CommentedFileConfig serverConfig = CommentedFileConfig.builder(serverPath)
                    .sync()
                    .writingMode(WritingMode.REPLACE)
                    .build();

            serverConfig.load();
            SERVER_SPEC.setConfig(serverConfig);

            Path clientPath = FMLPaths.CONFIGDIR.get().resolve("aibot-client.toml");
            if (clientPath.toFile().exists()) {
                System.out.println(">>> [Bot] 正在重新加载客户端配置: " + clientPath);
                CommentedFileConfig clientConfig = CommentedFileConfig.builder(clientPath)
                        .sync()
                        .writingMode(WritingMode.REPLACE)
                        .build();
                clientConfig.load();
                CLIENT_SPEC.setConfig(clientConfig);
            }

            System.out.println(">>> [Bot] 配置文件热重载成功！");
        } catch (Exception e) {
            System.err.println(">>> [Bot] 配置文件热重载失败！");
            e.printStackTrace();
        }
    }
}