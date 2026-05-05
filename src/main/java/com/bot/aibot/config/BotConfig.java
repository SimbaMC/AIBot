package com.bot.aibot.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class BotConfig {

    public static final ServerConfig SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    public static final ClientConfig CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        final Pair<ServerConfig, ModConfigSpec> serverPair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = serverPair.getRight();
        SERVER = serverPair.getLeft();

        final Pair<ClientConfig, ModConfigSpec> clientPair = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = clientPair.getRight();
        CLIENT = clientPair.getLeft();
    }

    public static class ServerConfig {
        public final ModConfigSpec.ConfigValue<String> wsUrl;
        public final ModConfigSpec.ConfigValue<List<? extends Number>> groupIds;
        public final ModConfigSpec.ConfigValue<Long> targetBotId;
        public final ModConfigSpec.ConfigValue<String> accessToken;
        public final ModConfigSpec.BooleanValue reconnectEnabled;
        public final ModConfigSpec.IntValue reconnectInitialInterval;
        public final ModConfigSpec.DoubleValue reconnectMultiplier;
        public final ModConfigSpec.IntValue reconnectMaxInterval;

        public final ModConfigSpec.BooleanValue enableChatSync;
        public final ModConfigSpec.BooleanValue enableJoinLeave;
        public final ModConfigSpec.BooleanValue enableDeath;
        public final ModConfigSpec.ConfigValue<String> mcPrefix;
        public final ModConfigSpec.BooleanValue enableAdvancement;

        public final ModConfigSpec.BooleanValue enableAI;
        public final ModConfigSpec.ConfigValue<String> aiApiUrl;
        public final ModConfigSpec.ConfigValue<String> aiApiKey;
        public final ModConfigSpec.ConfigValue<String> aiModelName;
        public final ModConfigSpec.ConfigValue<String> aiPrompt;
        public final ModConfigSpec.ConfigValue<String> aiTriggerPrefix;
        public final ModConfigSpec.ConfigValue<String> aiDeathMode;
        public final ModConfigSpec.ConfigValue<String> aiDeathPrompt;

        public final ModConfigSpec.IntValue broadcastCooldown;

        public final ModConfigSpec.ConfigValue<String> joinMsgFormat;
        public final ModConfigSpec.ConfigValue<String> leaveMsgFormat;
        public final ModConfigSpec.ConfigValue<String> deathMsgFormat;
        public final ModConfigSpec.ConfigValue<String> chatMsgFormat;
        public final ModConfigSpec.ConfigValue<String> advancementMsgFormat;
        public final ModConfigSpec.ConfigValue<String> startMsgFormat;
        public final ModConfigSpec.ConfigValue<List<? extends String>> nodeMappings;
        public final ModConfigSpec.ConfigValue<String> defaultNodeName;

        public final ModConfigSpec.ConfigValue<String> qqFaceApi;

        public ServerConfig(ModConfigSpec.Builder builder) {
            builder.push("Status_Command_Settings");

            nodeMappings = builder
                    .comment("节点 IP 映射配置。格式为 'IP:节点名'", "例如: ['127.0.0.1:本地节点', '1.2.3.4:上海中转']")
                    .defineList("nodeMappings", List.of("127.0.0.1:本地节点"), o -> o instanceof String);

            defaultNodeName = builder
                    .comment("当玩家 IP 不在映射列表中时显示的名称")
                    .define("defaultNodeName", "直连");

            builder.pop();
            builder.comment("bot链接配置").push("general");
            wsUrl = builder.comment("WebSocket URL")
                    .define("ws_url", "ws://127.0.0.1:3001");
            accessToken = builder.comment("NapCat/OneBot 鉴权 Token (如果未开启鉴权请留空)")
                    .define("access_token", "");
            groupIds = builder.comment("QQ群号列表")
                    .defineList("group_ids", Arrays.asList(0L), o -> o instanceof Number);
            targetBotId = builder.comment("目标机器人Q号")
                    .define("target_bot_id", 0L);
            reconnectEnabled = builder.comment("是否在断开连接后自动重连")
                    .define("reconnect_enabled", true);
            reconnectInitialInterval = builder.comment("初始重连间隔（秒）")
                    .defineInRange("reconnect_initial_interval", 5, 1, Integer.MAX_VALUE);
            reconnectMultiplier = builder.comment("重连间隔指数退避倍率（每次失败后乘以此值）")
                    .defineInRange("reconnect_multiplier", 2.0, 1.0, (double) Integer.MAX_VALUE);
            reconnectMaxInterval = builder.comment("最大重连间隔上限（秒）")
                    .defineInRange("reconnect_max_interval", 300, 1, Integer.MAX_VALUE);
            qqFaceApi = builder.comment("QQ表情源码地址 (必须包含 %s)")
                    .define("qq_face_api", "https://koishi.js.org/QFace/assets/qq_emoji/%s/png/%s.png");
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
                    .defineInRange("broadcast_cooldown", 600, 0, 3600);
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
                    .define("trigger_prefix", "bot ");
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
        public final ModConfigSpec.ConfigValue<String> neteaseCookie;

        public ClientConfig(ModConfigSpec.Builder builder) {
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

            Path clientPath = FMLPaths.CONFIGDIR.get().resolve("aibot-client.toml");
            if (clientPath.toFile().exists()) {
                System.out.println(">>> [Bot] 正在重新加载客户端配置: " + clientPath);
                CommentedFileConfig clientConfig = CommentedFileConfig.builder(clientPath)
                        .sync()
                        .writingMode(WritingMode.REPLACE)
                        .build();
                clientConfig.load();
            }

            System.out.println(">>> [Bot] 配置文件热重载成功！");
        } catch (Exception e) {
            System.err.println(">>> [Bot] 配置文件热重载失败！");
            e.printStackTrace();
        }
    }

    public static void saveClientCookie(String cookie) {
        String normalized = cookie == null ? "" : cookie.trim();
        try {
            CLIENT.neteaseCookie.set(normalized);
            CLIENT_SPEC.save();
            return;
        } catch (Exception ignored) {
            // 部分环境下 CLIENT_SPEC.save() 可能在配置尚未绑定时不可用，走文件兜底。
        }

        try {
            Path clientPath = FMLPaths.CONFIGDIR.get().resolve("aibot-client.toml");
            CommentedFileConfig clientConfig = CommentedFileConfig.builder(clientPath)
                    .sync()
                    .autosave()
                    .writingMode(WritingMode.REPLACE)
                    .build();
            if (clientPath.toFile().exists()) {
                clientConfig.load();
            }
            clientConfig.set("client.netease_cookie", normalized);
            clientConfig.save();
            clientConfig.close();
            CLIENT.neteaseCookie.set(normalized);
        } catch (Exception e) {
            System.err.println(">>> [Bot] 保存网易云 Cookie 失败: " + e.getMessage());
        }
    }
}
