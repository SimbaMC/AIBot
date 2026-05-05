package com.bot.aibot.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChineseUtils {

    private static final Logger LOGGER = LogManager.getLogger();
    // 静态字典，存所有的翻译
    private static final Map<String, String> TRANSLATIONS = new ConcurrentHashMap<>();
    // 【新增】专门存储 AI 学习到的死亡消息缓存
    private static final Map<String, String> AI_CACHE = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // 自定义缓存文件的路径: run/config/aibot/custom_death.json
    private static final Path CACHE_FILE_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("aibot").resolve("custom_death.json");

    public static void load() {
        TRANSLATIONS.clear(); // 重载前清空，防止重复
        AI_CACHE.clear(); // 清空缓存
        LOGGER.info(">>> [Bot] 开始全自动加载语言文件...");
        int count = 0;

        // 获取所有已加载的“模组”（包括 Minecraft 原版！）
        List<IModInfo> mods = ModList.get().getMods();

        LOGGER.info(">>> [Bot] 扫描目标列表长度: " + mods.size());

        for (IModInfo mod : mods) {
            String modId = mod.getModId();

            // ⚠️ 关键修改：只跳过 Bot 自己（防止死循环或读取混乱），不再跳过 "minecraft"！
            if (!"aibot".equals(modId)) {
                count += loadModLangForgeWay(mod);
            }
        }

        // 最后加载我们自己的“补丁文件”（如果有的话），用于纠正某些翻译
        // 如果你不需要手动补丁，这一步其实也可以删掉
        count += loadLocalPatch();
        // 【新增】加载 AI 学习到的缓存文件
        loadAICache();

        LOGGER.info(">>> [Bot] 汉化加载完成！当前字典总条目数: " + TRANSLATIONS.size() + " (本次加载: " + count + ")");

        // 打印几个关键 Key 验证一下原版是否进来了
        if (TRANSLATIONS.containsKey("death.attack.anvil")) {
            LOGGER.info(">>> [Bot] ✅ 原版汉化验证通过: death.attack.anvil -> " + TRANSLATIONS.get("death.attack.anvil"));
        } else {
            LOGGER.info(">>> [Bot] ❌ 警告: 未检测到原版汉化，可能是服务器核心 Jar 包里不包含中文文件。");
        }
    }
    // --- 新增：AI 缓存读写逻辑 ---

    private static void loadAICache() {
        try {
            if (!Files.exists(CACHE_FILE_PATH)) return;

            try (Reader reader = Files.newBufferedReader(CACHE_FILE_PATH, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                        AI_CACHE.put(entry.getKey(), entry.getValue().getAsString());
                    }
                    LOGGER.info(">>> [Bot] 🧠 已载入 " + AI_CACHE.size() + " 条 AI 学习记录");
                }
            }
        } catch (Exception e) {
            LOGGER.info(">>> [Bot] 读取 AI 缓存失败: " + e.getMessage());
        }
    }
    /**
     * 【核心方法】AI 翻译完后调用这个方法，把结果存下来
     * @param originalKey 原始英文消息 (例如: "Dev fell from a high place")
     * @param translatedValue 中文翻译 (例如: "Dev 从高处摔了下来")
     */
    public static synchronized void learn(String originalKey, String translatedValue) {
        // 1. 更新内存
        AI_CACHE.put(originalKey, translatedValue);

        // 2. 异步写入硬盘 (防止卡顿)
        new Thread(() -> {
            try {
                // 确保目录存在
                if (!Files.exists(CACHE_FILE_PATH.getParent())) {
                    Files.createDirectories(CACHE_FILE_PATH.getParent());
                }

                // 将 Map 转为 JsonObject
                JsonObject json = new JsonObject();
                // 排序写入，方便人工查看
                AI_CACHE.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> json.addProperty(entry.getKey(), entry.getValue()));

                // 写入文件
                try (Writer writer = Files.newBufferedWriter(CACHE_FILE_PATH, StandardCharsets.UTF_8)) {
                    GSON.toJson(json, writer);
                }
                LOGGER.info(">>> [Bot] 🧠 新知识已归档: " + originalKey);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 查询是否有缓存
     */
    public static String getCached(String key) {
        return AI_CACHE.get(key);
    }

    // 通用扫描逻辑：自动钻进 Jar 包找 assets/<modid>/lang/zh_cn.json
    private static int loadModLangForgeWay(IModInfo mod) {
        int loaded = 0;
        String modId = mod.getModId();

        try {
            IModFile modFile = mod.getOwningFile().getFile();

            // 自动寻找路径：assets/minecraft/lang/zh_cn.json 或 assets/create/lang/zh_cn.json
            Path langPath = modFile.findResource("assets", modId, "lang", "zh_cn.json");

            if (!Files.exists(langPath)) {
                langPath = modFile.findResource("assets", modId, "lang", "zh_CN.json");
            }

            if (Files.exists(langPath)) {
                try (InputStream is = Files.newInputStream(langPath);
                     InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                        String key = entry.getKey();
                        // 过滤逻辑：只把需要的吸进来，防止内存爆炸
                        // 原版有很多 GUI 文本我们不需要，只留实体名、物品名、死亡信息
                        boolean isUseful = key.startsWith("death.") ||
                                key.startsWith("item.") ||
                                key.startsWith("block.") ||
                                key.startsWith("entity.")||
                                key.startsWith("advancements.");

                        if (isUseful) {
                            TRANSLATIONS.put(key, entry.getValue().getAsString());
                            loaded++;
                        }
                    }
                }
                // 只打印原版和大量数据的模组，避免刷屏
                if (loaded > 100 || "minecraft".equals(modId)) {
                    LOGGER.info(">>> [Bot] 📚 从 [" + modId + "] 吸入汉化: " + loaded + " 条");
                }
            }
        } catch (Exception e) {
            // 忽略读取错误
        }
        return loaded;
    }

    // 加载你自己写的补丁文件 (vanilla_zh.json)，优先级最高，覆盖前面的
    private static int loadLocalPatch() {
        int loaded = 0;
        try (InputStream is = ChineseUtils.class.getResourceAsStream("/assets/aibot/lang/vanilla_zh.json")) {
            if (is != null) {
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                        TRANSLATIONS.put(entry.getKey(), entry.getValue().getAsString());
                        loaded++;
                    }
                }
                LOGGER.info(">>> [Bot] 🛠️ 加载本地补丁: " + loaded + " 条");
            }
        } catch (Exception e) {}
        return loaded;
    }

    // 最后的防线：如果服务器核心真的没中文，就用这个

    public static String translate(Component component) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            if (!TRANSLATIONS.containsKey(key)) return component.getString();

            String format = TRANSLATIONS.get(key);
            Object[] args = translatable.getArgs();
            Object[] translatedArgs = new Object[args.length];

            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component argComp) {
                    translatedArgs[i] = translate(argComp);
                } else {
                    translatedArgs[i] = args[i];
                }
            }

            try {
                return String.format(format.replace("%s", "%s"), translatedArgs);
            } catch (Exception e) {
                return component.getString(); // 格式化失败回退
            }
        }
        // ... 其他部分保持不变 (LiteralContents, Siblings) ...
        StringBuilder sb = new StringBuilder();
        if (component.getContents() instanceof LiteralContents literal) {
            sb.append(literal.text());
        }
        for (Component sibling : component.getSiblings()) {
            sb.append(translate(sibling));
        }
        return sb.toString();
    }
}