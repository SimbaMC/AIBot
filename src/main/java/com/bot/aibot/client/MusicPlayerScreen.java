package com.bot.aibot.client;

import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.C2SMusicActionPacket;
import com.bot.aibot.network.packet.C2SReportMusicPacket;
import com.bot.aibot.utils.NeteaseApi;
import com.bot.aibot.utils.SongInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MusicPlayerScreen extends Screen {

    // === 窗口尺寸 ===
    private final int WINDOW_WIDTH = 340;
    private final int WINDOW_HEIGHT = 220;
    private int leftPos, topPos;

    // === 状态管理 ===
    private enum Tab { SEARCH, PLAYLIST }
    private Tab currentTab = Tab.SEARCH;
    private boolean isBroadcastMode = false; // 默认私享模式

    // === 控件 ===
    private EditBox searchBox;
    private SongListWidget songList;
    private FlatButton btnSearch, btnLoadPlaylist, btnPrev, btnNext;
    private FlatButton btnToggle, btnStop;
    private FlatButton btnMode;

    // === 数据缓存 ===
    private List<Long> allSongIdsCache;
    private int currentPage = 0;
    private final int PAGE_SIZE = 50;
    private Component statusText = Component.empty();

    // === Sodium 风格配色 ===
    private static final int COLOR_BG = 0xCC101010;
    private static final int COLOR_HEADER = 0xFF000000;
    private static final int COLOR_ACCENT = 0xFF2ECC71;
    private static final int COLOR_TEXT_IDLE = 0xFFAAAAAA;
    private static final int COLOR_TEXT_ACTIVE = 0xFFFFFFFF;
    private static final int COLOR_HOVER = 0x20FFFFFF;

    public MusicPlayerScreen() {
        super(Component.literal("AiBot Netease"));
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - WINDOW_WIDTH) / 2;
        this.topPos = (this.height - WINDOW_HEIGHT) / 2;

        int contentTop = topPos + 35;

        // 1. 搜索页控件
        this.searchBox = new EditBox(this.font, leftPos + 10, contentTop + 10, 200, 18, Component.literal("搜索"));
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFF);
        this.addRenderableWidget(this.searchBox);

        this.btnSearch = new FlatButton(leftPos + 220, contentTop + 9, 50, 20, "GO", b -> doSearch());
        this.addRenderableWidget(this.btnSearch);

        // 2. 歌单页控件
        this.btnLoadPlaylist = new FlatButton(leftPos + 10, contentTop + 10, 100, 20, "刷新", b -> loadMyPlaylist());
        this.btnLoadPlaylist.visible = false;
        this.addRenderableWidget(this.btnLoadPlaylist);

        // 3. 底部控制栏
        int bottomY = topPos + WINDOW_HEIGHT - 35;

        // 暂停/播放
        this.btnToggle = new FlatButton(leftPos + 10, bottomY, 25, 20, "||", b -> {
            PacketHandler.sendToServer(new C2SMusicActionPacket(1));
        });
        this.addRenderableWidget(this.btnToggle);

        // 停止
        this.btnStop = new FlatButton(leftPos + 40, bottomY, 25, 20, "■", b -> {
            PacketHandler.sendToServer(new C2SMusicActionPacket(0));
        });
        this.addRenderableWidget(this.btnStop);

        // 模式切换
        this.btnMode = new FlatButton(leftPos + 75, bottomY, 60, 20, "🎧 私享", b -> {
            isBroadcastMode = !isBroadcastMode;
            updateModeButton();
        });
        updateModeButton(); // 初始化文字
        this.addRenderableWidget(this.btnMode);

        // 翻页
        this.btnPrev = new FlatButton(leftPos + WINDOW_WIDTH - 90, bottomY, 35, 20, "<", b -> changePage(-1));
        this.btnNext = new FlatButton(leftPos + WINDOW_WIDTH - 50, bottomY, 35, 20, ">", b -> changePage(1));
        this.btnPrev.active = false;
        this.btnNext.active = false;
        this.addRenderableWidget(this.btnPrev);
        this.addRenderableWidget(this.btnNext);

        // 列表
        int listY = contentTop + 40;
        int listH = WINDOW_HEIGHT - 35 - 40 - 45;
        this.songList = new SongListWidget(this.minecraft, WINDOW_WIDTH - 20, listH, listY);
        this.songList.setLeftPos(leftPos + 10);
        this.addWidget(this.songList);

        updateTabVisibility();
    }

    private void updateModeButton() {
        if (isBroadcastMode) {
            btnMode.setMessage(Component.literal("📢 全服"));
        } else {
            btnMode.setMessage(Component.literal("🎧 私享"));
        }
    }

    private void switchTab(Tab tab) {
        this.currentTab = tab;
        updateTabVisibility();
    }

    private void updateTabVisibility() {
        boolean isSearch = (currentTab == Tab.SEARCH);
        this.searchBox.visible = isSearch;
        this.searchBox.setEditable(isSearch);
        this.btnSearch.visible = isSearch;
        this.btnLoadPlaylist.visible = !isSearch;
    }

    // --- 逻辑部分 ---
    private void doSearch() {
        String k = searchBox.getValue();
        if (k.isEmpty()) return;
        statusText = Component.literal("正在搜索...");
        new Thread(() -> {
            List<SongInfo> res = NeteaseApi.searchList(k);
            Minecraft.getInstance().execute(() -> {
                songList.refreshList(res);
                statusText = Component.literal("找到 " + res.size() + " 首歌曲");
            });
        }).start();
    }

    private void loadMyPlaylist() {
        statusText = Component.literal("正在获取歌单...");
        new Thread(() -> {
            long uid = NeteaseApi.getMyUid();
            if (uid == 0) {
                statusText = Component.literal("未登录，请先 /bot login");
                return;
            }
            var pl = NeteaseApi.getUserPlaylists(uid);
            if (pl != null && pl.size() > 0) {
                long fid = pl.get(0).getAsJsonObject().get("id").getAsLong();
                allSongIdsCache = NeteaseApi.getPlaylistSongIds(fid);
                currentPage = 0;
                loadCurrentPageSongs();
            }
        }).start();
    }

    private void changePage(int off) { currentPage += off; loadCurrentPageSongs(); }

    private void loadCurrentPageSongs() {
        if (allSongIdsCache == null) return;
        new Thread(() -> {
            int s = currentPage * PAGE_SIZE;
            int e = Math.min(s + PAGE_SIZE, allSongIdsCache.size());
            if (s >= allSongIdsCache.size()) return;
            List<SongInfo> d = NeteaseApi.getSongsDetail(allSongIdsCache.subList(s, e));
            Minecraft.getInstance().execute(() -> {
                songList.refreshList(d);
                btnPrev.active = currentPage > 0;
                btnNext.active = e < allSongIdsCache.size();
                songList.setScrollAmount(0);
                statusText = Component.literal("页码: " + (currentPage + 1));
            });
        }).start();
    }

    // === 核心渲染逻辑 ===
    // 【修复】修改参数名 graphics -> g, mouseX -> mx, mouseY -> my, partialTick -> pt
    // 这样下面的代码就不会报错了
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);

        // 窗口背景
        g.fill(leftPos, topPos, leftPos + WINDOW_WIDTH, topPos + WINDOW_HEIGHT, COLOR_BG);
        g.fill(leftPos, topPos, leftPos + WINDOW_WIDTH, topPos + 30, COLOR_HEADER);

        // Tab
        renderTab(g, "搜 索", Tab.SEARCH, leftPos + 20, topPos + 8, mx, my);
        renderTab(g, "我的喜欢", Tab.PLAYLIST, leftPos + 80, topPos + 8, mx, my);

        int activeX = (currentTab == Tab.SEARCH) ? leftPos + 20 : leftPos + 80;
        int activeW = (currentTab == Tab.SEARCH) ? 30 : 45;
        g.fill(activeX - 2, topPos + 28, activeX + activeW + 2, topPos + 30, COLOR_ACCENT);

        // 搜索框背景
        if (currentTab == Tab.SEARCH) {
            g.fill(searchBox.getX() - 2, searchBox.getY() - 2, searchBox.getX() + searchBox.getWidth() + 2, searchBox.getY() + searchBox.getHeight() + 2, 0xFF202020);
        }

        // 进度条
        renderProgressBar(g);

        // 更新按钮文字
        this.btnToggle.setMessage(Component.literal(ClientMusicManager.isPaused() ? "▶" : "||"));

        // 状态文字
        if (!statusText.getString().isEmpty()) {
            g.drawString(this.font, statusText, leftPos + 10, topPos + WINDOW_HEIGHT - 45, 0xFF666666, false);
        }

        // 绘制列表和按钮
        this.songList.render(g, mx, my, pt);
        super.render(g, mx, my, pt);
    }

    private void renderTab(GuiGraphics g, String text, Tab tab, int x, int y, int mx, int my) {
        boolean isActive = (currentTab == tab);
        boolean isHover = mx >= x && mx <= x + font.width(text) && my >= topPos && my <= topPos + 30;
        int color = isActive ? COLOR_TEXT_ACTIVE : (isHover ? 0xFFE0E0E0 : COLOR_TEXT_IDLE);
        g.drawString(this.font, text, x, y, color, false);
    }

    private void renderProgressBar(GuiGraphics g) {
        if (!ClientMusicManager.isPlaying()) return;

        long current = ClientMusicManager.getProgress();
        long total = ClientMusicManager.currentDuration;
        if (total <= 0) total = 1;

        float percent = (float) current / total;
        percent = Math.min(1.0f, Math.max(0.0f, percent));

        int barX = leftPos + 10;
        int barY = topPos + WINDOW_HEIGHT - 40;
        int barW = WINDOW_WIDTH - 20;
        int barH = 2;

        g.fill(barX, barY, barX + barW, barY + barH, 0xFF303030);
        g.fill(barX, barY, barX + (int) (barW * percent), barY + barH, COLOR_ACCENT);

        String timeStr = formatTime(current) + " / " + formatTime(total);
        g.pose().pushPose();
        g.pose().scale(0.8f, 0.8f, 0.8f);
        g.drawString(this.font, timeStr, (int) ((barX + 2) / 0.8), (int) ((barY - 8) / 0.8), 0xFFAAAAAA, false);
        g.pose().popPose();
    }

    private String formatTime(long ms) {
        long sec = ms / 1000;
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY >= topPos && mouseY <= topPos + 30) {
            if (mouseX >= leftPos + 20 && mouseX <= leftPos + 60) switchTab(Tab.SEARCH);
            if (mouseX >= leftPos + 80 && mouseX <= leftPos + 140) switchTab(Tab.PLAYLIST);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ================= 自定义扁平按钮 =================
    private class FlatButton extends Button {
        public FlatButton(int x, int y, int w, int h, String label, OnPress onPress) {
            super(x, y, w, h, Component.literal(label), onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int bgColor = this.isHoveredOrFocused() ? 0xFF404040 : 0xFF202020;
            if (!this.active) bgColor = 0xFF101010;
            g.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            int textColor = this.active ? 0xFFFFFFFF : 0xFF555555;
            // 模式按钮特殊颜色
            if (this == btnMode && isBroadcastMode) textColor = 0xFFFF5555;
            g.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
        }
    }

    // ================= 列表控件 =================
    class SongListWidget extends ObjectSelectionList<SongListWidget.SongEntry> {
        private final int listY;

        public SongListWidget(Minecraft mc, int width, int height, int top) {
            super(mc, width, height, top, top + height, 24);
            this.listY = top;
        }

        @Override
        protected int getScrollbarPosition() {
            return getLeft() + getRowWidth() + 6;
        }

        @Override
        public int getRowWidth() {
            return width - 10;
        }

        @Override
        public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
            g.enableScissor(getLeft(), getTop(), getRight(), getBottom());
            super.render(g, mx, my, pt);
            g.disableScissor();
        }

        @Override
        protected void renderBackground(@NotNull GuiGraphics g) {
        }

        @Override
        protected void renderDecorations(@NotNull GuiGraphics g, int mx, int my) {
        }

        public void refreshList(List<SongInfo> songs) {
            this.clearEntries();
            for (SongInfo s : songs) this.addEntry(new SongEntry(s));
        }

        public class SongEntry extends ObjectSelectionList.Entry<SongEntry> {
            private final SongInfo song;
            private long lastClickTime = 0;

            public SongEntry(SongInfo song) {
                this.song = song;
            }

            @Override
            public void render(GuiGraphics g, int idx, int top, int left, int w, int h, int mx, int my, boolean hover, float pt) {
                if (hover) g.fill(left, top, left + w, top + h, COLOR_HOVER);

                String name = font.plainSubstrByWidth(song.name, w - 80);
                g.drawString(font, name, left + 4, top + 8, 0xFFDDDDDD, false);

                String artist = font.plainSubstrByWidth(song.artist, 70);
                g.drawString(font, artist, left + w - font.width(artist) - 4, top + 8, 0xFF666666, false);
            }

            @Override
            public boolean mouseClicked(double mx, double my, int btn) {
                if (btn == 0) {
                    SongListWidget.this.setSelected(this);
                    long now = System.currentTimeMillis();
                    if (now - lastClickTime < 500) {
                        playLogic(song); // 统一使用 playLogic
                    }
                    lastClickTime = now;
                    return true;
                }
                return false;
            }

            private void playLogic(SongInfo song) {
                String modeText = isBroadcastMode ? "§c[全服广播]" : "§a[私享模式]";
                Minecraft.getInstance().player.sendSystemMessage(Component.literal(modeText + " §f正在请求: " + song.name));

                new Thread(() -> {
                    String url = NeteaseApi.getSongUrl(song.id);
                    if (url == null) {
                        Minecraft.getInstance().execute(() ->
                                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c播放失败：无法获取链接 (VIP/无版权)"))
                        );
                        return;
                    }

                    if (isBroadcastMode) {
                        PacketHandler.sendToServer(new C2SReportMusicPacket(url, song.name + " - " + song.artist, song.duration));
                    } else {
                        Minecraft.getInstance().execute(() -> {
                            ClientMusicManager.play(url, song.name + " - " + song.artist, song.duration);
                        });
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