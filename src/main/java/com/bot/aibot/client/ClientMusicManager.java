package com.bot.aibot.client;

import java.io.BufferedInputStream;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import com.bot.aibot.security.SecureMusicStream;

public final class ClientMusicManager {

    private static volatile SourceDataLine line;
    private static volatile Thread thread;
    private static volatile boolean playing, paused;
    private static long generation;
    private static volatile long started, pausedAt, pausedTotal;
    public static volatile long currentDuration;
    public static volatile Runnable onTrackFinishedCallback;

    private ClientMusicManager() {}

    public static synchronized void play(final String url, final String name, final long duration) {
        stop();
        final long session = ++generation;
        playing = true;
        paused = false;
        currentDuration = duration;
        started = System.currentTimeMillis();
        pausedTotal = 0;
        thread = new Thread(new Runnable() {

            public void run() {
                boolean natural = false;
                try (BufferedInputStream input = new BufferedInputStream(SecureMusicStream.open(url))) {
                    Bitstream bits = new Bitstream(input);
                    Decoder decoder = new Decoder();
                    Header h = bits.readFrame();
                    if (h == null) return;
                    AudioFormat format = new AudioFormat(h.frequency(), 16, 2, true, false);
                    SourceDataLine out = (SourceDataLine) AudioSystem
                        .getLine(new DataLine.Info(SourceDataLine.class, format));
                    out.open(format);
                    synchronized (ClientMusicManager.class) {
                        if (session != generation) {
                            out.close();
                            return;
                        }
                        line = out;
                    }
                    out.start();
                    Minecraft.getMinecraft()
                        .func_152344_a(new Runnable() {

                            public void run() {
                                if (Minecraft.getMinecraft().thePlayer != null) Minecraft.getMinecraft().thePlayer
                                    .addChatMessage(new ChatComponentText("§b♪ §f正在播放: §a" + name));
                            }
                        });
                    while (isCurrent(session) && h != null) {
                        while (paused && isCurrent(session)) {
                            out.stop();
                            try {
                                Thread.sleep(80);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                        if (!isCurrent(session)) break;
                        if (!out.isRunning()) out.start();
                        SampleBuffer sample = (SampleBuffer) decoder.decodeFrame(h, bits);
                        short[] pcm = sample.getBuffer();
                        byte[] bytes = new byte[pcm.length * 2];
                        for (int i = 0; i < pcm.length; i++) {
                            bytes[i * 2] = (byte) pcm[i];
                            bytes[i * 2 + 1] = (byte) (pcm[i] >> 8);
                        }
                        updateVolume(out);
                        out.write(bytes, 0, bytes.length);
                        bits.closeFrame();
                        h = bits.readFrame();
                    }
                    natural = isCurrent(session);
                } catch (final Exception e) {
                    if (isCurrent(session)) Minecraft.getMinecraft()
                        .func_152344_a(new Runnable() {

                            public void run() {
                                if (Minecraft.getMinecraft().thePlayer != null) Minecraft.getMinecraft().thePlayer
                                    .addChatMessage(new ChatComponentText("§c[Bot] 音乐地址被拒绝或连接失败。"));
                            }
                        });
                } finally {
                    cleanup(session);
                    if (natural && onTrackFinishedCallback != null) Minecraft.getMinecraft()
                        .func_152344_a(onTrackFinishedCallback);
                }
            }
        }, "AiBot-Music");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        generation++;
        playing = false;
        paused = false;
        if (thread != null) thread.interrupt();
        thread = null;
        cleanup();
    }

    private static void cleanup() {
        SourceDataLine l = line;
        line = null;
        if (l != null) try {
            l.stop();
            l.close();
        } catch (Exception ignored) {}
    }

    private static synchronized void cleanup(long session) {
        if (session != generation) return;
        cleanup();
        playing = false;
        thread = null;
    }

    private static synchronized boolean isCurrent(long session) {
        return playing && session == generation;
    }

    private static void updateVolume(SourceDataLine out) {
        try {
            float master = Minecraft.getMinecraft().gameSettings
                .getSoundLevel(net.minecraft.client.audio.SoundCategory.MASTER);
            float music = Minecraft.getMinecraft().gameSettings
                .getSoundLevel(net.minecraft.client.audio.SoundCategory.MUSIC);
            FloatControl gain = (FloatControl) out.getControl(FloatControl.Type.MASTER_GAIN);
            float db = (float) (20 * Math.log10(Math.max(.0001f, master * music)));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
        } catch (Exception ignored) {}
    }

    public static void togglePause() {
        if (!playing) return;
        paused = !paused;
        if (paused) pausedAt = System.currentTimeMillis();
        else pausedTotal += System.currentTimeMillis() - pausedAt;
    }

    public static boolean isPlaying() {
        return playing;
    }

    public static boolean isPaused() {
        return paused;
    }

    public static long getProgress() {
        if (!playing) return 0;
        long now = paused ? pausedAt : System.currentTimeMillis();
        return Math.max(0, Math.min(currentDuration, now - started - pausedTotal));
    }
}
