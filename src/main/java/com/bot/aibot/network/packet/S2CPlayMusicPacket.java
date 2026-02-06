package com.bot.aibot.network.packet;

import com.bot.aibot.client.ClientMusicManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CPlayMusicPacket {
    private final String url;
    private final String name;
    private final long duration;

    public S2CPlayMusicPacket(String url, String name, long duration) {
        this.url = url;
        this.name = name;
        this.duration = duration;
    }

    public S2CPlayMusicPacket(FriendlyByteBuf buf) {
        this.url = buf.readUtf();
        this.name = buf.readUtf();
        this.duration = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.url);
        buf.writeUtf(this.name);
        buf.writeLong(this.duration);
    }

    // 关键：处理逻辑
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 绝杀调试：在控制台强制打印

            // 安全地在客户端执行代码，防止 Side 冲突导致静默崩溃
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientMusicManager.play(url, name,duration);
            });
        });
        context.setPacketHandled(true);
    }
}