package com.bot.aibot.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class ClientMusicConfig {

    private static Configuration config;

    private ClientMusicConfig() {}

    public static synchronized void init(File directory) {
        if (config == null) config = new Configuration(new File(directory, "aibot-client.cfg"));
    }

    public static synchronized String cookie() {
        if (config == null) return "";
        config.load();
        String value = config.getString("netease_cookie", "music", "", "网易云登录 Cookie（仅保存在本机，绝不会发送给服务器）");
        if (config.hasChanged()) config.save();
        return value;
    }

    public static synchronized void saveCookie(String value) {
        if (config == null) return;
        config.load();
        config.get("music", "netease_cookie", "")
            .set(value == null ? "" : value);
        config.save();
    }
}
