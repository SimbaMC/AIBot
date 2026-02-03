package com.bot.aibot.client;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.net.URL;

public class ClientMusicManager {
    private static SourceDataLine line;
    private static Thread musicThread;

    // 状态控制
    private static volatile boolean isPlaying = false;
    private static volatile boolean isPaused = false;
    private static String currentMusicName = "";

    public static void play(String url, String name) {
        stop(); // 切换歌曲时先彻底停止老歌
        isPlaying = true;
        isPaused = false;
        currentMusicName = name;

        musicThread = new Thread(() -> {
            try (BufferedInputStream in = new BufferedInputStream(new URL(url).openStream())) {
                Bitstream bitstream = new Bitstream(in);
                Decoder decoder = new Decoder();

                Header header = bitstream.readFrame();
                if (header == null) return;

                AudioFormat format = new AudioFormat(header.frequency(), 16, 2, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();

                while (isPlaying && header != null) {
                    // --- 暂停逻辑 ---
                    if (isPaused) {
                        line.stop(); // 停止输出，保持硬件缓冲区
                        while (isPaused && isPlaying) {
                            Thread.sleep(100); // 线程阻塞，等待恢复
                        }
                        if (isPlaying) line.start(); // 恢复输出
                    }
                    // ----------------

                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    short[] pcm = output.getBuffer();
                    byte[] outBuffer = new byte[pcm.length * 2];
                    for (int i = 0; i < pcm.length; i++) {
                        outBuffer[i * 2] = (byte) (pcm[i] & 0xff);
                        outBuffer[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xff);
                    }

                    updateVolume(line);
                    line.write(outBuffer, 0, outBuffer.length);

                    bitstream.closeFrame();
                    header = bitstream.readFrame();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cleanup();
            }
        }, "BGM-Playback-Thread");

        musicThread.setPriority(Thread.MAX_PRIORITY);
        musicThread.start();
    }

    // 暂停/取消暂停 切换
    public static void togglePause() {
        isPaused = !isPaused;
        String status = isPaused ? "§6已暂停" : "§a已恢复播放";
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§b[Music] " + status + ": " + currentMusicName), true);
        }
    }

    // 彻底停止
    public static void stop() {
        isPlaying = false;
        isPaused = false;
        if (musicThread != null) {
            musicThread.interrupt();
        }
        cleanup();
    }

    private static void cleanup() {
        if (line != null) {
            line.drain(); // 等待缓冲区播放完
            line.stop();
            line.close();
            line = null;
        }
    }

    private static void updateVolume(SourceDataLine line) {
        try {
            if (line != null && line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                float mcVolume = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC);
                float dB = (float) (Math.log(Math.max(mcVolume, 0.0001)) / Math.log(10.0) * 20.0);
                FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(dB, gainControl.getMaximum())));
            }
        } catch (Exception ignored) {}
    }
}