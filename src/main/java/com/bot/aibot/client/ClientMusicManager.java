package com.bot.aibot.client;

import java.io.BufferedInputStream;
import java.net.URL;
import javazoom.jl.player.Player;

public final class ClientMusicManager {

    private static volatile Player player;
    private static volatile Thread thread;

    public static synchronized void play(final String url) {
        stop();
        thread = new Thread(new Runnable() {

            public void run() {
                try {
                    player = new Player(new BufferedInputStream(new URL(url).openStream()));
                    player.play();
                } catch (Exception e) {
                    System.err.println("[AiBot] Music: " + e.getMessage());
                }
            }
        }, "AiBot-Music");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        if (player != null) player.close();
        player = null;
        if (thread != null) thread.interrupt();
        thread = null;
    }
}
