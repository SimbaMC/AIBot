package com.bot.aibot.security;

import javax.net.ssl.HttpsURLConnection;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public final class SecureMusicStream {
    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private SecureMusicStream() {}

    public static InputStream open(String value) throws IOException, MusicUrlPolicy.MusicUrlException {
        URI current = URI.create(value);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            current = MusicUrlPolicy.validate(current.toString());
            // HttpsURLConnection resolves again when connecting, leaving a small DNS TOCTOU window.
            // Every returned address is checked immediately before this connection and on every redirect.
            HttpsURLConnection connection = (HttpsURLConnection) current.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "audio/mpeg,audio/*;q=0.9,*/*;q=0.1");
            connection.setUseCaches(false);

            int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException e) {
                connection.disconnect();
                throw e;
            }
            if (status >= 300 && status <= 399) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isBlank()) throw new IOException("Redirect missing Location");
                if (redirects == MAX_REDIRECTS) throw new IOException("Too many music redirects");
                try {
                    current = current.resolve(location);
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid music redirect", e);
                }
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IOException("Music server returned HTTP " + status);
            }
            try {
                return new DisconnectingInputStream(connection.getInputStream(), connection);
            } catch (IOException e) {
                connection.disconnect();
                throw e;
            }
        }
        throw new IOException("Too many music redirects");
    }

    private static final class DisconnectingInputStream extends FilterInputStream {
        private final HttpsURLConnection connection;

        private DisconnectingInputStream(InputStream input, HttpsURLConnection connection) {
            super(input);
            this.connection = connection;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                connection.disconnect();
            }
        }
    }
}
