package com.bot.aibot.utils;

import com.bot.aibot.config.BotConfig;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

public final class GeoIpResolver {
    private static final Logger LOGGER = LogManager.getLogger();
    private static DatabaseReader reader;
    private static String loadedPath;
    private static long loadedLastModified;

    private GeoIpResolver() {}

    public static synchronized String resolveCountry(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) return null;
        DatabaseReader current = getReader();
        if (current == null) return null;
        try {
            CountryResponse response = current.country(address);
            Map<String, String> names = response.getCountry().getNames();
            String name = names.get("zh-CN");
            if (name == null || name.isBlank()) name = response.getCountry().getName();
            return name == null || name.isBlank() ? response.getCountry().getIsoCode() : name;
        } catch (GeoIp2Exception | IOException e) {
            return null;
        }
    }

    private static DatabaseReader getReader() {
        String configured = BotConfig.SERVER.geoIpDatabase.get().trim();
        File file = configured.isEmpty() ? null : new File(configured);
        if (file != null && !file.isAbsolute()) file = FMLPaths.CONFIGDIR.get().resolve(configured).toFile();
        String path = file == null ? "" : file.getAbsolutePath();
        long lastModified = file == null ? 0L : file.lastModified();
        if (path.equals(loadedPath) && lastModified == loadedLastModified) return reader;
        closeReader();
        loadedPath = path;
        loadedLastModified = lastModified;
        if (file == null || !file.isFile()) return null;
        try {
            reader = new DatabaseReader.Builder(file).locales(List.of("zh-CN", "en")).build();
            LOGGER.info(">>> [Bot] 已加载 GeoIP 数据库: {}", path);
        } catch (IOException e) {
            LOGGER.error(">>> [Bot] GeoIP 数据库加载失败: {}", path, e);
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
            LOGGER.warn(">>> [Bot] GeoIP 数据库关闭失败", e);
        }
        reader = null;
    }
}
