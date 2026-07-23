package com.bot.aibot.client;

import com.bot.aibot.security.SecureMusicStream;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;

public class ClientMusicManager {
    private static SourceDataLine line;
    private static Thread musicThread;

    // 状态控制
    private static volatile boolean isPlaying = false;
    private static volatile boolean isPaused = false;
    private static String currentMusicName = "";

    // 状态字段
    public static volatile long currentDuration = 0;
    public static volatile long playStartTime = 0;
    public static volatile long pauseStartTime = 0;
    public static volatile long totalPausedTime = 0;

    public static Runnable onTrackFinishedCallback = null;

    /**
     * 开始播放音乐
     */
    public static void play(String url, String name, long duration) {
        // 1. 切换歌曲前，先停止当前播放并恢复其他声音，确保清理干净
        stop();

        Minecraft mc = Minecraft.getInstance();

        // 2. 尝试停止 MC 内部原生音乐
        try {
            mc.getSoundManager().stop(null, SoundSource.MUSIC);
            mc.getSoundManager().stop(null, SoundSource.RECORDS);
            mc.getSoundManager().stop(null, SoundSource.AMBIENT);
        } catch (Exception ignored) {}

        // 3. 初始化状态；安全连接建立后再提示正在播放。
        currentDuration = duration;
        playStartTime = System.currentTimeMillis();
        totalPausedTime = 0;
        isPlaying = true;
        isPaused = false;
        currentMusicName = name;

        musicThread = new Thread(() -> {
            boolean finishedNaturally = false;
            long lastSuppressTime = 0;

            try (BufferedInputStream in = new BufferedInputStream(SecureMusicStream.open(url))) {
                mc.execute(() -> {
                    if (mc.player != null) mc.player.displayClientMessage(
                            Component.literal("§b♪ §f正在播放: §a" + name + " §b♪"), true);
                });
                Bitstream bitstream = new Bitstream(in);
                Decoder decoder = new Decoder();

                Header header = bitstream.readFrame();
                if (header == null) return;

                // 准备音频格式
                AudioFormat format = new AudioFormat(header.frequency(), 16, 2, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();

                while (isPlaying && header != null) {
                    // 暂停控制逻辑
                    if (isPaused) {
                        if (line != null) line.stop();
                        while (isPaused && isPlaying) {
                            try { Thread.sleep(100); } catch (InterruptedException e) { break; }
                        }
                        if (isPlaying && line != null) line.start();
                    }

                    // 解码
                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    short[] pcm = output.getBuffer();
                    byte[] outBuffer = new byte[pcm.length * 2];
                    for (int i = 0; i < pcm.length; i++) {
                        outBuffer[i * 2] = (byte) (pcm[i] & 0xff);
                        outBuffer[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xff);
                    }

                    // --- 核心音量与压制逻辑 ---
                    updateVolume(line); // 更新自己

                    long now = System.currentTimeMillis();
                    if (now - lastSuppressTime > 500) { // 每0.5秒扫一次流氓模组
                        if (line != null && line.isOpen()) {
                            suppressOthers(line);
                        }
                        lastSuppressTime = now;
                    }

                    if (line != null) {
                        line.write(outBuffer, 0, outBuffer.length);
                    }

                    bitstream.closeFrame();
                    header = bitstream.readFrame();
                }

                if (isPlaying) finishedNaturally = true;
            } catch (Exception e) {
                System.err.println(">>> [AiBot] 音乐播放失败（地址详情已隐藏）: " + e.getClass().getSimpleName());
                mc.execute(() -> {
                    if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§c[Bot] 音乐地址被拒绝或连接失败。"));
                });
            } finally {
                // 关键：无论是因为切歌、停止还是崩溃，都要恢复世界声音
                cleanup();
                restoreOthers();
                if (finishedNaturally && onTrackFinishedCallback != null) {
                    onTrackFinishedCallback.run();
                }
            }
        }, "AiBot-Music-Thread");

        musicThread.setPriority(Thread.MAX_PRIORITY);
        musicThread.start();
    }

    public static void stop() {
        isPlaying = false;
        isPaused = false;

        // 先恢复声音，再中断线程
        restoreOthers();

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

    /**
     * 压制：遍历所有 Mixer，除了自己占用的那根 Line，其余全部静音。
     */
    private static void suppressOthers(SourceDataLine aibotLine) {
        if (aibotLine == null || !isPlaying) return;
        try {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                Mixer mixer = AudioSystem.getMixer(info);
                for (Line openLine : mixer.getSourceLines()) {
                    // 只要内存地址不一样，说明是别人的声音
                    if (openLine != aibotLine && openLine instanceof DataLine) {
                        try {
                            if (openLine.isControlSupported(BooleanControl.Type.MUTE)) {
                                ((BooleanControl) openLine.getControl(BooleanControl.Type.MUTE)).setValue(true);
                            } else if (openLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                                FloatControl gain = (FloatControl) openLine.getControl(FloatControl.Type.MASTER_GAIN);
                                gain.setValue(gain.getMinimum());
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * 还原：取消所有 Line 的静音状态。
     */
    public static void restoreOthers() {
        try {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                Mixer mixer = AudioSystem.getMixer(info);
                for (Line openLine : mixer.getSourceLines()) {
                    if (openLine instanceof DataLine) {
                        try {
                            if (openLine.isControlSupported(BooleanControl.Type.MUTE)) {
                                ((BooleanControl) openLine.getControl(BooleanControl.Type.MUTE)).setValue(false);
                            } else if (openLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                                FloatControl gain = (FloatControl) openLine.getControl(FloatControl.Type.MASTER_GAIN);
                                // 只有被我们压到最低的才恢复到 0dB (原声)
                                if (gain.getValue() <= gain.getMinimum() + 0.1f) {
                                    gain.setValue(0.0f);
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * 更新 AiBot 自身音量，支持 MC 滑块。
     */
    private static void updateVolume(SourceDataLine aibotLine) {
        try {
            if (aibotLine != null && aibotLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                Minecraft mc = Minecraft.getInstance();
                float masterVol = mc.options.getSoundSourceVolume(SoundSource.MASTER);
                float musicVol = mc.options.getSoundSourceVolume(SoundSource.MUSIC);
                float targetVol = masterVol * musicVol;

                // 转换到分贝
                float dB = (float) (Math.log(Math.max(targetVol, 0.0001f)) / Math.log(10.0) * 20.0);
                FloatControl gainControl = (FloatControl) aibotLine.getControl(FloatControl.Type.MASTER_GAIN);
                float clampedDB = Math.max(gainControl.getMinimum(), Math.min(dB, gainControl.getMaximum()));
                gainControl.setValue(clampedDB);
            }
        } catch (Exception ignored) {}
    }

    // --- 状态获取方法 ---
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
    public static String getCurrentMusicName() { return currentMusicName; }
}