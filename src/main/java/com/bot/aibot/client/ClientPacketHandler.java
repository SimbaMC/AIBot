package com.bot.aibot.client;

import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.C2SReportMusicPacket;
import com.bot.aibot.network.packet.S2CMusicCommandPacket; // 引入新包的枚举
import com.bot.aibot.utils.NeteaseApi;
import com.bot.aibot.utils.SongInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ClientPacketHandler {

    // 修改入口方法签名，适配新 Packet
    public static void handle(S2CMusicCommandPacket.Action action, String data, long extra) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. 停止指令
        if (action == S2CMusicCommandPacket.Action.STOP) {
            ClientMusicManager.stop();
            mc.player.displayClientMessage(Component.literal("§e[Bot] 音乐已停止。"), true);
            return;
        }

        // 2. 直接播放指令 (URL)
        if (action == S2CMusicCommandPacket.Action.PLAY_Direct) {
            String url = data;
            // extra 此时作为 duration
            if (!url.equals(MusicPlayerScreen.EXPECTED_URL)) {
                ClientMusicManager.onTrackFinishedCallback = null;
            }
            ClientMusicManager.play(url, "正在播放...", extra);
            return;
        }
        if (action == S2CMusicCommandPacket.Action.RESET_COOLDOWN) {
            MusicPlayerScreen.resetCooldown();
            mc.execute(() -> mc.player.displayClientMessage(Component.literal("§a[Bot] 广播模式冷却已强制重置！"), true));
            return;
        }

        // 3. 打开 GUI
        if (action == S2CMusicCommandPacket.Action.OPEN_GUI) {
            mc.execute(() -> mc.setScreen(new MusicPlayerScreen()));
            return;
        }

        // --- 以下功能需要 API 请求，开启新线程 ---
        new Thread(() -> {
            // 4. 搜索指令
            if (action == S2CMusicCommandPacket.Action.SEARCH_AND_PLAY) {
                String keyword = data;
                boolean isGlobal = (extra == 1); // 约定: extra=1 代表全服广播

                mc.execute(() -> mc.player.displayClientMessage(Component.literal("§7[Bot] 正在搜索: " + keyword), true));

                // 调用 API 搜索第一首
                String songId = NeteaseApi.search(keyword);
                if (songId != null) {
                    processPlayById(songId, mc,isGlobal);
                } else {
                    mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§c[Bot] 未找到相关歌曲: " + keyword)));
                }
            }

            // 5. 【新功能】随机播放红心歌单
            if (action == S2CMusicCommandPacket.Action.PLAY_MY_LIKE) {
                long uid = NeteaseApi.getMyUid();
                if (uid == 0) {
                    mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§c[Bot] 需要登录网易云才能播放红心歌单。请使用 /bot login")));
                    return;
                }

                mc.execute(() -> mc.player.displayClientMessage(Component.literal("§d[Bot] 正在获取您的红心歌单..."), true));

                // 获取歌单列表
                JsonArray playlists = NeteaseApi.getUserPlaylists(uid);
                if (playlists != null && playlists.size() > 0) {
                    // 通常第一个歌单就是“我喜欢的音乐”
                    JsonObject favList = playlists.get(0).getAsJsonObject();
                    long playlistId = favList.get("id").getAsLong();

                    // 获取歌单内所有歌曲 ID
                    List<Long> songIds = NeteaseApi.getPlaylistSongIds(playlistId);
                    if (songIds != null && !songIds.isEmpty()) {
                        // 随机抽取一个 ID
                        long randomId = songIds.get(new Random().nextInt(songIds.size()));
                        // 播放逻辑复用
                        processPlayById(String.valueOf(randomId), mc, false);
                    } else {
                        mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§c[Bot] 您的红心歌单是空的！")));
                    }
                }
            }
        }).start();
    }

    // 辅助方法：通过 ID 获取详情并汇报给服务端 (复用逻辑)
    private static void processPlayById(String songId, Minecraft mc,boolean isGlobal) {
        // 获取 URL
        String url = NeteaseApi.getSongUrl(songId);
        if (url == null) {
            mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§c[Bot] 无法播放该歌曲 (无版权或VIP)。")));
            return;
        }

        // 获取详情 (歌名、时长)
        String songName = "未知歌曲";
        long duration = 0;
        List<SongInfo> details = NeteaseApi.getSongsDetail(Collections.singletonList(Long.parseLong(songId)));
        if (!details.isEmpty()) {
            SongInfo info = details.get(0);
            songName = info.name + " - " + info.artist;
            duration = info.duration;
        }

        // 发送给服务端，请求全服/个人播放
        // 注意：这里我们统一用 ReportMusicPacket 汇报结果，服务端收到后会决定是广播还是怎样
        PacketHandler.sendToServer(new C2SReportMusicPacket(url, songName, duration, isGlobal));
    }
}