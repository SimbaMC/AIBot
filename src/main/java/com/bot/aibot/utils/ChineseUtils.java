package com.bot.aibot.utils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.bot.aibot.BottyMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public final class ChineseUtils {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final Type TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Map<String, String> CACHE = new LinkedHashMap<String, String>();
    private static File file;

    private ChineseUtils() {}

    public static synchronized void load() {
        file = new File(new File(BottyMod.configDirectory, "aibot"), "custom_death.json");
        CACHE.clear();
        if (!file.isFile()) return;
        try {
            FileReader reader = new FileReader(file);
            Map<String, String> values = GSON.fromJson(reader, TYPE);
            reader.close();
            if (values != null) CACHE.putAll(values);
        } catch (Exception ignored) {}
    }

    public static synchronized void learn(String key, String value) {
        CACHE.put(key, value);
        try {
            file.getParentFile()
                .mkdirs();
            FileWriter writer = new FileWriter(file);
            GSON.toJson(CACHE, TYPE, writer);
            writer.close();
        } catch (Exception ignored) {}
    }

    public static synchronized String getCached(String key) {
        return CACHE.get(key);
    }

    public static String translate(IChatComponent component) {
        if (component instanceof ChatComponentTranslation) {
            ChatComponentTranslation translation = (ChatComponentTranslation) component;
            String key = translation.getKey();
            String cached = CACHE.get(key);
            if (cached != null) {
                try {
                    return String.format(cached, translation.getFormatArgs());
                } catch (Exception ignored) {}
            }
        }
        return component.getUnformattedText();
    }
}
