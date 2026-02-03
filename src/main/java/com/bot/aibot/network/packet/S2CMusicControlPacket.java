package com.bot.aibot.network.packet;

import com.bot.aibot.client.ClientMusicManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CMusicControlPacket {
    private final int action; // 0: Stop, 1: TogglePause

    public S2CMusicControlPacket(int action) {
        this.action = action;
    }

    public S2CMusicControlPacket(FriendlyByteBuf buf) {
        this.action = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.action);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (action == 0) {
                    ClientMusicManager.stop();
                } else if (action == 1) {
                    ClientMusicManager.togglePause();
                }
            });
        });
        context.setPacketHandled(true);
    }
}