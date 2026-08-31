package com.bot.aibot.utils;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Map;

import com.bot.aibot.BottyMod;
import com.bot.aibot.config.BotConfig;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;

public final class GeoIpResolver {

    private static DatabaseReader reader;
    private static String loadedPath;
    private static long loadedLastModified;

    private GeoIpResolver() {}

    public static synchronized String resolveCountry(InetAddress address) {
        if (address == null || address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) return null;
        DatabaseReader current = getReader();
        if (current == null) return null;
        try {
            CountryResponse response = current.country(address);
            Map<String, String> names = response.getCountry()
                .getNames();
            String name = names.get("zh-CN");
            if (name == null || name.trim()
                .isEmpty())
                name = response.getCountry()
                    .getName();
            return name == null || name.trim()
                .isEmpty() ? response.getCountry()
                    .getIsoCode() : name;
        } catch (IOException e) {
            BottyMod.LOG.warn(">>> [Bot] GeoIP 查询失败: " + address.getHostAddress(), e);
        } catch (GeoIp2Exception e) {
            return null;
        }
        return null;
    }

    private static DatabaseReader getReader() {
        String configured = BotConfig.geoIpDatabase == null ? "" : BotConfig.geoIpDatabase.trim();
        File file = configured.isEmpty() ? null : new File(configured);
        if (file != null && !file.isAbsolute()) file = new File(BottyMod.configDirectory, configured);
        String path = file == null ? "" : file.getAbsolutePath();
        long lastModified = file == null ? 0L : file.lastModified();
        if (path.equals(loadedPath) && lastModified == loadedLastModified) return reader;
        closeReader();
        loadedPath = path;
        loadedLastModified = lastModified;
        if (file == null || !file.isFile()) {
            if (file != null) BottyMod.LOG.warn(">>> [Bot] GeoIP 数据库不存在，国家识别未启用: " + path);
            return null;
        }
        try {
            reader = new DatabaseReader.Builder(file).locales(Arrays.asList("zh-CN", "en"))
                .build();
            BottyMod.LOG.info(">>> [Bot] 已加载 GeoIP 数据库: " + path);
        } catch (IOException e) {
            BottyMod.LOG.error(">>> [Bot] GeoIP 数据库加载失败: " + path, e);
        }
        return reader;
    }

    public static synchronized void shutdown() {
        closeReader();
        loadedPath = null;
        loadedLastModified = 0L;
    }

    private static void closeReader() {
        if (reader == null) return;
        try {
            reader.close();
        } catch (IOException e) {
            BottyMod.LOG.warn(">>> [Bot] GeoIP 数据库关闭失败", e);
        }
        reader = null;
    }
}
