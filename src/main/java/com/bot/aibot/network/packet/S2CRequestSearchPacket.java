package com.bot.aibot.network.packet;

import com.bot.aibot.client.ClientMusicManager;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.utils.NeteaseApi;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CRequestSearchPacket {
    private final String keyword;
    private final boolean isGlobal;

    public S2CRequestSearchPacket(String keyword, boolean isGlobal) {
        this.keyword = keyword;
        this.isGlobal = isGlobal;
    }

    public S2CRequestSearchPacket(FriendlyByteBuf buf) {
        this.keyword = buf.readUtf();
        this.isGlobal = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.keyword);
        buf.writeBoolean(this.isGlobal);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // --- 这里的代码在【客户端】执行 ---
            System.out.println(">>> [Client] 收到搜索任务: " + keyword);
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§e[Bot] 正在本地搜索: " + keyword + "..."));

            new Thread(() -> {
                try {
                    // 1. 客户端本地调用 API (走玩家自己的梯子/网络)
                    // 确保 NeteaseApi 已在客户端初始化 (客户端启动时会自动读取 Config)
                    String songId = NeteaseApi.search(keyword);
                    if (songId == null) {
                        printError("未找到歌曲");
                        return;
                    }

                    String url = NeteaseApi.getSongUrl(songId);
                    if (url == null) {
                        printError("无法获取播放链接");
                        return;
                    }

                    // 2. 拿到链接后的分支判断
                    if (isGlobal) {
                        // 模式 A: 全服广播 -> 把结果发回给服务端
                        PacketHandler.INSTANCE.sendToServer(new C2SReportMusicPacket(url, keyword));
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§a[Bot] 解析成功，正在上传服务器广播..."));
                    } else {
                        // 模式 B: 私享 -> 直接本地播放
                        ClientMusicManager.play(url, keyword);
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§b[私享] 正在播放: " + keyword));
                    }

                } catch (Exception e) {
                    printError("搜索异常: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        });
        ctx.get().setPacketHandled(true);
    }

    private void printError(String msg) {
        Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c[Bot] " + msg))
        );
    }
}