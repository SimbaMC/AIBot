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
import java.util.HashMap;
import java.util.Map;

public class ClientMusicManager {
    private static SourceDataLine line;
    private static Thread musicThread;

    // 状态控制
    private static volatile boolean isPlaying = false;
    private static volatile boolean isPaused = false;
    private static String currentMusicName = "";

    // 状态字段
    public static volatile long currentDuration = 0; // 总时长 (ms)
    public static volatile long playStartTime = 0;   // 开始播放的时间戳
    public static volatile long pauseStartTime = 0;  // 暂停时的时间戳
    public static volatile long totalPausedTime = 0; // 累计暂停时间

    // 自动播放的回调接口
    public static Runnable onTrackFinishedCallback = null;

    // 用于保存原版音量的 Map
    private static final Map<SoundSource, Float> originalVolumes = new HashMap<>();

    public static void play(String url, String name, long duration) {
        stop(); // 切换歌曲时先彻底停止老歌

        Minecraft mc = Minecraft.getInstance();

        // --- 【核心修改】暴力静音干扰源 ---
        // 针对 Music Triggers 等模组，直接把相关频道的音量拉到 0
        // 1.20.1 修复：使用 getSoundSourceOptionInstance().set()
        muteGameCategory(mc, SoundSource.MUSIC);
        muteGameCategory(mc, SoundSource.RECORDS);
        muteGameCategory(mc, SoundSource.AMBIENT);
        // ----------------------------------

        // UI 显示
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§b♪ §f正在播放: §a" + name + " §b♪"),
                    true
            );
        }

        // 重置计时器
        currentDuration = duration;
        playStartTime = System.currentTimeMillis();
        totalPausedTime = 0;

        isPlaying = true;
        isPaused = false;
        currentMusicName = name;

        musicThread = new Thread(() -> {
            boolean finishedNaturally = false; // 标记是否自然播放结束

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
                        if (line != null) line.stop();
                        while (isPaused && isPlaying) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                        if (isPlaying && line != null) line.start();
                    }
                    // ----------------

                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    short[] pcm = output.getBuffer();
                    byte[] outBuffer = new byte[pcm.length * 2];
                    for (int i = 0; i < pcm.length; i++) {
                        outBuffer[i * 2] = (byte) (pcm[i] & 0xff);
                        outBuffer[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xff);
                    }

                    // 实时更新音量 (Bot 只跟随“主音量”)
                    updateVolume(line);

                    if (line != null) {
                        line.write(outBuffer, 0, outBuffer.length);
                    }

                    bitstream.closeFrame();
                    header = bitstream.readFrame();
                }

                if (isPlaying) {
                    finishedNaturally = true;
                }
            } catch (Throwable e) {
                System.err.println(">>> [Music Error] 播放线程崩溃: " + e.getMessage());
                e.printStackTrace();

                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§c[Bot] 播放失败: " + e.getMessage()), false);
                }
            } finally {
                cleanup();
                if (finishedNaturally && onTrackFinishedCallback != null) {
                    onTrackFinishedCallback.run();
                }
            }
        }, "BGM-Playback-Thread");

        musicThread.setPriority(Thread.MAX_PRIORITY);
        musicThread.start();
    }

    // --- 【修复】保存并静音指定声道 ---
    private static void muteGameCategory(Minecraft mc, SoundSource category) {
        // Getter 依然可用
        float current = mc.options.getSoundSourceVolume(category);

        if (current > 0) {
            originalVolumes.put(category, current);
            // Setter 修复：使用 OptionInstance 设置 Double 值
            mc.options.getSoundSourceOptionInstance(category).set(0.0);
        }
    }

    // --- 【修复】恢复所有被静音的声道 ---
    private static void restoreGameVolumes() {
        Minecraft mc = Minecraft.getInstance();
        if (!originalVolumes.isEmpty()) {
            originalVolumes.forEach((category, vol) -> {
                // Setter 修复
                mc.options.getSoundSourceOptionInstance(category).set((double) vol);
            });
            originalVolumes.clear();
        }
    }

    public static void stop() {
        isPlaying = false;
        isPaused = false;

        restoreGameVolumes();

        if (musicThread != null) {
            musicThread.interrupt();
            musicThread = null;
        }
        cleanup();
    }

    private static void cleanup() {
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (Exception ignored) {}
            line = null;
        }
    }

    // --- 音量计算 ---
    private static void updateVolume(SourceDataLine line) {
        try {
            if (line != null && line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                // 读取主音量
                float masterVol = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);

                float targetVol = Math.min(masterVol, 1.0f);
                float dB = (float) (Math.log(Math.max(targetVol, 0.0001)) / Math.log(10.0) * 20.0);
                FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(dB, gainControl.getMaximum())));
            }
        } catch (Exception ignored) {}
    }

    public static void togglePause() {
        isPaused = !isPaused;
        if (isPaused) pauseStartTime = System.currentTimeMillis();
        else totalPausedTime += (System.currentTimeMillis() - pauseStartTime);
    }

    public static long getProgress() {
        if (!isPlaying) return 0;
        long now = isPaused ? pauseStartTime : System.currentTimeMillis();
        long elapsed = now - playStartTime - totalPausedTime;
        return Math.max(0, Math.min(elapsed, currentDuration));
    }

    public static boolean isPaused() { return isPaused; }
    public static boolean isPlaying() { return isPlaying; }
}