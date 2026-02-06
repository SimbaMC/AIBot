package com.bot.aibot.client;

import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.C2SReportMusicPacket;
import com.bot.aibot.utils.NeteaseApi;
import com.bot.aibot.utils.SongInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class MusicPlayerScreen extends Screen {

    private EditBox searchBox;
    private SongListWidget songList;
    private final int SIDEBAR_WIDTH = 80;
    private List<Long> allSongIdsCache; // 存那 2000 个 ID
    private int currentPage = 0;
    private final int PAGE_SIZE = 50;   // 每页显示 50 首
    private Button btnPrev, btnNext;
    private Component title = Component.literal("云音乐");

    public MusicPlayerScreen() {
        super(Component.literal("Netease Music"));
    }

    @Override
    protected void init() {
        // 1. 初始化搜索框 (顶部)
        this.searchBox = new EditBox(this.font, SIDEBAR_WIDTH + 10, 10, this.width - SIDEBAR_WIDTH - 80, 20, Component.literal("搜索"));
        this.addRenderableWidget(this.searchBox);

        // 2. 初始化搜索按钮
        this.addRenderableWidget(Button.builder(Component.literal("搜索"), button -> doSearch())
                .bounds(this.width - 60, 10, 50, 20)
                .build());

        // 3. 初始化左侧功能按钮
        this.addRenderableWidget(Button.builder(Component.literal("我的歌单"), button -> loadMyPlaylist())
                .bounds(10, 40, 60, 20)
                .build());

        // 4. 初始化滚动列表 (占据主要区域)
        this.songList = new SongListWidget(this.minecraft, this.width - SIDEBAR_WIDTH, this.height - 40, 40, this.height);
        this.songList.setLeftPos(SIDEBAR_WIDTH); // 设置列表左边距
        this.addWidget(this.songList);

        // 【新增】翻页按钮 (放在列表底部或者顶部)
        this.btnPrev = Button.builder(Component.literal("< 上一页"), b -> changePage(-1))
                .bounds(this.width - 200, this.height - 30, 80, 20).build();
        this.btnNext = Button.builder(Component.literal("下一页 >"), b -> changePage(1))
                .bounds(this.width - 100, this.height - 30, 80, 20).build();

        this.btnPrev.active = false;
        this.btnNext.active = false;

        this.addRenderableWidget(this.btnPrev);
        this.addRenderableWidget(this.btnNext);
    }

    private void doSearch() {
        String keyword = searchBox.getValue();
        if (keyword.isEmpty()) return;

        // 异步搜索，防止卡顿
        new Thread(() -> {
            List<SongInfo> results = NeteaseApi.searchList(keyword);
            Minecraft.getInstance().execute(() -> {
                songList.refreshList(results);
            });
        }).start();
    }

    private void loadMyPlaylist() {
        new Thread(() -> {
            long uid = NeteaseApi.getMyUid();
            if (uid == 0) return;
            var playlists = NeteaseApi.getUserPlaylists(uid);
            if (playlists != null && playlists.size() > 0) {
                long favId = playlists.get(0).getAsJsonObject().get("id").getAsLong();
                String plName = playlists.get(0).getAsJsonObject().get("name").getAsString();

                // 1. 先只拿 ID (2000个也能秒拿)
                this.allSongIdsCache = NeteaseApi.getPlaylistSongIds(favId);
                this.currentPage = 0;

                // 更新标题
                this.title = Component.literal(plName + " (" + allSongIdsCache.size() + "首)");

                // 2. 加载第一页数据
                loadCurrentPageSongs();
            }
        }).start();
    }
    private void changePage(int offset) {
        this.currentPage += offset;
        loadCurrentPageSongs();
    }
    private void loadCurrentPageSongs() {
        if (allSongIdsCache == null || allSongIdsCache.isEmpty()) return;

        new Thread(() -> {
            // 计算分页偏移量
            int start = currentPage * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, allSongIdsCache.size());

            if (start >= allSongIdsCache.size()) return;

            // 截取这 50 个 ID
            List<Long> subList = allSongIdsCache.subList(start, end);

            // 去查这 50 个的详情
            List<SongInfo> details = NeteaseApi.getSongsDetail(subList);

            Minecraft.getInstance().execute(() -> {
                // 刷新列表
                songList.refreshList(details);

                // 更新按钮状态
                btnPrev.active = currentPage > 0;
                btnNext.active = end < allSongIdsCache.size();
            });
        }).start();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // 1. 绘制左侧边栏背景 (深灰色)
        graphics.fill(0, 0, SIDEBAR_WIDTH, this.height, 0xFF222222);

        // 2. 绘制顶部栏背景 (网易红)
        graphics.fill(SIDEBAR_WIDTH, 0, this.width, 40, 0xFFC20C0C);

        graphics.drawString(this.font, this.title, 10, 15, 0xFFFFFF);

        // 3. 绘制标题
        graphics.drawString(this.font, "云音乐", 10, 15, 0xFFFFFF);

        // 4. 渲染列表
        this.songList.render(graphics, mouseX, mouseY, partialTick);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ================= 内部类：歌曲列表控件 =================
    class SongListWidget extends ObjectSelectionList<SongListWidget.SongEntry> {

        public SongListWidget(Minecraft mc, int width, int height, int top, int bottom) {
            super(mc, width, height, top, bottom, 24); // 24 是每一行的高度
        }

        public void refreshList(List<SongInfo> songs) {
            this.clearEntries();
            for (SongInfo song : songs) {
                this.addEntry(new SongEntry(song));
            }
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getRight() - 6;
        }

        // ================= 内部类：单行歌曲条目 =================
        public class SongEntry extends ObjectSelectionList.Entry<SongEntry> {
            private final SongInfo song;
            private long lastClickTime = 0;

            public SongEntry(SongInfo song) {
                this.song = song;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
                // 歌手名 (灰色)
                graphics.drawString(Minecraft.getInstance().font, song.artist, left + width - 100, top + 6, 0xFFAAAAAA);
                // 歌名 (白色)
                graphics.drawString(Minecraft.getInstance().font, song.name, left + 5, top + 6, 0xFFFFFFFF);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) { // 左键点击
                    SongListWidget.this.setSelected(this);

                    // 双击检测 (500ms 内两次点击)
                    long now = System.currentTimeMillis();
                    if (now - lastClickTime < 500) {
                        playSong(this.song);
                    }
                    lastClickTime = now;
                    return true;
                }
                return false;
            }

            private void playSong(SongInfo song) {
                Minecraft.getInstance().setScreen(null); // 关闭界面
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§a[GUI] 正在请求播放: " + song.name));

                // 异步获取链接并发送给服务端
                new Thread(() -> {
                    String url = NeteaseApi.getSongUrl(song.id);
                    if (url != null) {
                        // 发送给服务端全服广播
                        PacketHandler.sendToServer(new C2SReportMusicPacket(url, song.name + " - " + song.artist));
                    } else {
                        Minecraft.getInstance().execute(() ->
                                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c无法获取播放链接 (可能无版权)"))
                        );
                    }
                }).start();
            }

            @Override
            public Component getNarration() {
                return Component.literal(song.name);
            }
        }
    }
}