package com.bot.aibot.security;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import javax.net.ssl.HttpsURLConnection;

public final class SecureMusicStream {

    private SecureMusicStream() {}

    public static InputStream open(String value) throws IOException, MusicUrlPolicy.MusicUrlException {
        URI current;
        try {
            current = new URI(value);
        } catch (Exception e) {
            throw new IOException("Invalid URL", e);
        }
        for (int redirects = 0; redirects <= 5; redirects++) {
            current = MusicUrlPolicy.validate(current.toASCIIString());
            // DNS is checked immediately before connect and per redirect; a residual DNS TOCTOU window remains.
            final HttpsURLConnection connection = (HttpsURLConnection) current.toURL()
                .openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
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
                if (location == null || location.trim()
                    .length() == 0) throw new IOException("Redirect missing Location");
                if (redirects == 5) throw new IOException("Too many redirects");
                try {
                    current = current.resolve(location);
                } catch (Exception e) {
                    throw new IOException("Invalid redirect", e);
                }
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IOException("HTTP " + status);
            }
            try {
                return new DisconnectingInputStream(connection.getInputStream(), connection);
            } catch (IOException e) {
                connection.disconnect();
                throw e;
            }
        }
        throw new IOException("Too many redirects");
    }

    private static final class DisconnectingInputStream extends FilterInputStream {

        private final HttpsURLConnection connection;

        DisconnectingInputStream(InputStream in, HttpsURLConnection connection) {
            super(in);
            this.connection = connection;
        }

        public void close() throws IOException {
            try {
                super.close();
            } finally {
                connection.disconnect();
            }
        }
    }
}
