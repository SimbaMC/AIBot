package com.bot.aibot.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.net.URL;

public class ClientMusicManager {

    private static Thread currentThread;
    private static SourceDataLine currentLine;
    private static boolean isPlaying = false;

    /**
     * 播放音乐 (由 Packet 调用)
     */
    public static void play(String url, String name) {
        // 1. 停止当前音乐 (切歌)
        stop();

        // 2. 停止 MC 原版背景音乐 (BGM 接管!)
        stopVanillaMusic();

        Minecraft.getInstance().gui.getChat().addMessage(Component.literal("🎵 正在缓冲 BGM: " + name));

        // 3. 开启新线程播放
        currentThread = new Thread(() -> {
            try {
                isPlaying = true;
                URL audioUrl = new URL(url);
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(new BufferedInputStream(audioUrl.openStream()));

                // 获取音频格式
                AudioFormat baseFormat = audioStream.getFormat();

                // 转换为 PCM 格式 (解码 MP3)
                AudioFormat decodedFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false
                );

                AudioInputStream decodedStream = AudioSystem.getAudioInputStream(decodedFormat, audioStream);

                // 打开输出设备 (SourceDataLine)
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
                currentLine = (SourceDataLine) AudioSystem.getLine(info);
                currentLine.open(decodedFormat);

                // 应用音量 (读取 MC 设置的 "音乐" 音量)
                updateVolume();

                currentLine.start();

                // 写入数据 (开始播放)
                byte[] buffer = new byte[4096];
                int nBytesRead;
                while (isPlaying && (nBytesRead = decodedStream.read(buffer, 0, buffer.length)) != -1) {
                    currentLine.write(buffer, 0, nBytesRead);

                    // 动态更新音量 (可选，为了简单先不实时更新)
                    // updateVolume();
                }

                currentLine.drain();
                currentLine.close();
                decodedStream.close();

            } catch (Exception e) {
                e.printStackTrace();
                if (isPlaying) {
                    Minecraft.getInstance().gui.getChat().addMessage(Component.literal("❌ 播放失败: " + e.getMessage()));
                }
            } finally {
                isPlaying = false;
            }
        });
        currentThread.start();
    }

    public static void stop() {
        isPlaying = false;
        if (currentLine != null && currentLine.isOpen()) {
            currentLine.stop();
            currentLine.close();
        }
        if (currentThread != null) {
            currentThread.interrupt();
        }
    }

    private static void stopVanillaMusic() {
        Minecraft mc = Minecraft.getInstance();
        SoundManager soundManager = mc.getSoundManager();
        soundManager.stop(null, SoundSource.MUSIC); // 停止所有 MUSIC 类型的原版声音
    }

    private static void updateVolume() {
        if (currentLine != null && currentLine.isOpen()) {
            try {
                // 读取 MC "音乐" 选项的音量 (0.0 - 1.0)
                float mcVolume = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC);

                // 转换为分贝 (Gain)
                // 线性音量转对数音量: 20 * log10(vol)
                // 防止 -Infinity (音量为0时)
                float db = (mcVolume <= 0.0f) ? -80.0f : 20.0f * (float)Math.log10(mcVolume);

                FloatControl gainControl = (FloatControl) currentLine.getControl(FloatControl.Type.MASTER_GAIN);

                // 限制范围，防止报错
                float max = gainControl.getMaximum();
                float min = gainControl.getMinimum();
                if (db > max) db = max;
                if (db < min) db = min;

                gainControl.setValue(db);
            } catch (Exception ignored) {
                // 某些音频设备不支持 Gain 控制，忽略
            }
        }
    }
}