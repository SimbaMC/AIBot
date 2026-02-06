package com.bot.aibot.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.http.HttpClient;
import java.time.Duration;

public class HttpUtils {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static HttpClient getClient() {
        return CLIENT;
    }

    public static Gson getGson() {
        return GSON;
    }
}