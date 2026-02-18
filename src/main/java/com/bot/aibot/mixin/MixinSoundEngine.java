package com.bot.aibot.mixin;

import com.bot.aibot.client.ClientMusicManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class MixinSoundEngine {

    /**
     * 在声音播放逻辑开始前进行注入
     * 如果模组播放器正在播放，且新声音属于音乐或唱片分类，则取消播放
     */
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void onPlay(SoundInstance sound, CallbackInfo ci) {
        // 检查本模组播放器是否正在运行
        if (ClientMusicManager.isPlaying()) {
            // 获取声音来源分类
            SoundSource source = sound.getSource();

            // 压制背景音乐 (MUSIC) 和 唱片机音乐 (RECORDS)
            // 如果整合包有其他自定义频道重叠，也可以在这里添加判断
            if (source == SoundSource.MUSIC || source == SoundSource.RECORDS) {
                ci.cancel(); // 核心：直接拦截，不执行后续播放逻辑
            }
        }
    }
}