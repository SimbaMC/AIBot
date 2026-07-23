package com.bot.aibot.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class HttpUtils {

    private HttpUtils() {}

    public static Response request(String url, String method, String body, Map<String, String> headers)
        throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod(method);
        for (Map.Entry<String, String> entry : headers.entrySet())
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        if (body != null) {
            connection.setDoOutput(true);
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            OutputStream out = connection.getOutputStream();
            out.write(data);
            out.close();
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        StringBuilder result = new StringBuilder();
        if (stream != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
            reader.close();
        }
        return new Response(status, result.toString());
    }

    public static final class Response {

        public final int status;
        public final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
