package com.bot.aibot.network.packet;

import com.bot.aibot.client.ClientMusicManager;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.utils.NeteaseApi;
import com.bot.aibot.utils.SongInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.Collections;
import java.util.List;
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
                    String songIdStr = NeteaseApi.search(keyword);
                    if (songIdStr == null) {
                        printError("未找到歌曲");
                        return;
                    }
                    long duration = 0;
                    String finalName = keyword;
                    try {
                        long songIdLong = Long.parseLong(songIdStr);
                        List<SongInfo> details = NeteaseApi.getSongsDetail(Collections.singletonList(songIdLong));
                        if (!details.isEmpty()) {
                            SongInfo info = details.get(0);
                            duration = info.duration;
                            finalName = info.name + " - " + info.artist;
                        }
                    } catch (Exception ignored) {
                        System.out.println(">>> [Client] 获取歌曲详情失败，将使用默认信息");
                    }

                    String url = NeteaseApi.getSongUrl(songIdStr);
                    if (url == null) {
                        printError("无法获取播放链接");
                        return;
                    }

                    // 【新增 Debug 代码】
                    System.out.println(">>> [Client Debug] 解析到的直链 URL: " + url);

                    // 2. 拿到链接后的分支判断
                    if (isGlobal) {
                        // 全服广播：发包给服务端 (带时长)
                        // 修复：补齐了第三个参数 duration
                        PacketHandler.INSTANCE.sendToServer(new C2SReportMusicPacket(url, finalName, duration));
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§a[Bot] 解析成功，正在上传服务器广播..."));
                    } else {
                        // 私享播放：本地播放 (带时长)
                        // 修复：补齐了第三个参数 duration
                        ClientMusicManager.play(url, finalName, duration);
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§b[私享] 正在播放: " + finalName));
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