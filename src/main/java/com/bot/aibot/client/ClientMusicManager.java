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
    // 新增状态字段
    public static volatile long currentDuration = 0; // 总时长 (ms)
    public static volatile long playStartTime = 0;   // 开始播放的时间戳
    public static volatile long pauseStartTime = 0;  // 暂停时的时间戳
    public static volatile long totalPausedTime = 0; // 累计暂停时间

    // 【新增】自动播放的回调接口
    public static Runnable onTrackFinishedCallback = null;

    public static void play(String url, String name,long duration) {
        stop(); // 切换歌曲时先彻底停止老歌

        Minecraft mc = Minecraft.getInstance();
        // --- [新增] UI 显示: 模仿原版唱片机，仅在开始时显示几秒钟 ---
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§b♪ §f正在播放: §a" + name + " §b♪"),
                    true // overlay = true 会将其显示在 Action Bar 位置
            );
        }

        if (mc.getSoundManager() != null) {
            // 初始播放时立刻停止一次，防止重叠
            mc.getSoundManager().stop(null, SoundSource.MUSIC);
            mc.getSoundManager().stop(null, SoundSource.RECORDS);
        }
        // 重置计时器
        currentDuration = duration;
        playStartTime = System.currentTimeMillis();
        totalPausedTime = 0;

        isPlaying = true;
        isPaused = false;
        currentMusicName = name;

        musicThread = new Thread(() -> {
            long lastSuppressTime = 0;
            boolean finishedNaturally = false; // 标记是否自然播放结束// 用于计时持续压制

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
                    // --- 核心修改：持续压制逻辑 ---
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastSuppressTime > 1000) { // 每 1000 毫秒执行一次
                        Minecraft client = Minecraft.getInstance();
                        if (client.getSoundManager() != null) {
                            // 持续停止 MUSIC 和 RECORDS 频道，确保唱片机也被拦截
                            client.getSoundManager().stop(null, SoundSource.MUSIC);
                            client.getSoundManager().stop(null, SoundSource.RECORDS);
                        }

                        lastSuppressTime = currentTime;
                    }
                    // ----------------------------

                    // --- 暂停逻辑 ---
                    if (isPaused) {
                        line.stop();
                        while (isPaused && isPlaying) {
                            Thread.sleep(100);
                        }
                        if (isPlaying) line.start();
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
                // 如果循环结束且 isPlaying 仍为 true，说明是自然播放完毕，不是被切歌打断的
                if (isPlaying) {
                    finishedNaturally = true;
                }
            } catch (Throwable e) { // 【修改】从 Exception 改为 Throwable
                System.err.println(">>> [Music Error] 播放线程崩溃: " + e.getMessage());
                e.printStackTrace();


                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§c[Bot] 播放失败: 缺少依赖库或解码错误"), false);
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

    public static void togglePause() {
        isPaused = !isPaused;
        if (isPaused) pauseStartTime = System.currentTimeMillis();
        else totalPausedTime += (System.currentTimeMillis() - pauseStartTime);
    }
    // 【新增】获取当前播放进度 (ms)
    public static long getProgress() {
        if (!isPlaying) return 0;
        long now = isPaused ? pauseStartTime : System.currentTimeMillis();
        long elapsed = now - playStartTime - totalPausedTime;
        return Math.max(0, Math.min(elapsed, currentDuration)); // 限制范围
    }

    public static void stop() {
        isPlaying = false;
        isPaused = false;
        // 清空回调，防止手动切歌时触发自动播放
        if (musicThread != null) musicThread.interrupt();
        cleanup();
    }

    private static void cleanup() {
        if (line != null) {
            try {
                // 如果是人为停止，不需要 drain 慢慢等缓冲区结束，直接 close
                line.stop();
                line.close();
            } catch (Exception ignored) {}
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
    // 【新增】获取状态 (供 GUI 使用)
    public static boolean isPaused() { return isPaused; }
    public static boolean isPlaying() { return isPlaying; }
}