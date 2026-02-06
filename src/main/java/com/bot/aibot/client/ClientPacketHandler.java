package com.bot.aibot.client;

import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.C2SReportMusicPacket;
import com.bot.aibot.utils.NeteaseApi;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Random;

public class ClientPacketHandler {

    public static void handle(String action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // --- 功能 A: 打开 GUI ---
        if ("OPEN_GUI".equals(action)) {
            // 这里必须用 execute，因为网络线程不能直接操作 GUI
            mc.execute(() -> mc.setScreen(new MusicPlayerScreen()));
            return;
        }

        // 下面的功能需要登录 UID
        // 注意：这里是在网络线程运行，如果涉及耗时操作（API请求），建议新开线程
        new Thread(() -> {
            long uid = NeteaseApi.getMyUid();
            if (uid == 0) {
                mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§c[Bot] 未检测到登录状态，请先执行 /bot login")));
                return;
            }

            JsonArray playlists = NeteaseApi.getUserPlaylists(uid);
            if (playlists == null || playlists.size() == 0) {
                mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§c[Bot] 无法获取歌单列表。")));
                return;
            }

            // --- 功能 B: 列出歌单 ---
            if ("MINE".equals(action)) {
                mc.execute(() -> {
                    mc.player.sendSystemMessage(Component.literal("§e=== 您的歌单列表 ==="));
                    for (int i = 0; i < playlists.size(); i++) {
                        JsonObject pl = playlists.get(i).getAsJsonObject();
                        String name = pl.get("name").getAsString();
                        long count = pl.get("trackCount").getAsLong();
                        mc.player.sendSystemMessage(Component.literal("§7" + (i + 1) + ". §f" + name + " §7(" + count + "首)"));
                    }
                });
            }

            // --- 功能 C: 随机播放红心歌单 ---
            else if ("MYLIKE".equals(action)) {
                JsonObject favPlaylist = playlists.get(0).getAsJsonObject();
                long favId = favPlaylist.get("id").getAsLong();
                String plName = favPlaylist.get("name").getAsString();

                mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§e[Bot] 正在加载歌单: " + plName + "...")));

                List<Long> songIds = NeteaseApi.getPlaylistSongIds(favId);

                if (songIds == null || songIds.isEmpty()) {
                    mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§c[Bot] 歌单是空的！")));
                    return;
                }

                long randomSongId = songIds.get(new Random().nextInt(songIds.size()));
                String url = NeteaseApi.getSongUrl(String.valueOf(randomSongId));

                if (url != null) {
                    PacketHandler.sendToServer(new C2SReportMusicPacket(url, "§d随机收藏: " + randomSongId));
                    mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§a[Bot] 命中幸运歌曲 ID: " + randomSongId)));
                } else {
                    mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§c[Bot] 随机到的歌曲无法播放 (无版权)。")));
                }
            }
        }).start();
    }
}