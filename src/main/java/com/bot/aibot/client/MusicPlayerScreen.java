package com.bot.aibot.client;

import com.bot.aibot.API.QrCode;
import com.bot.aibot.config.BotConfig;
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
import java.util.Random;

public class MusicPlayerScreen extends Screen {

    private final int WINDOW_WIDTH = 340;
    private final int WINDOW_HEIGHT = 240;
    private int leftPos, topPos;

    private enum ScreenState { PLAYER, LOGIN_PROMPT, LOGIN_QR }
    private ScreenState currentState = ScreenState.LOGIN_PROMPT;

    // === 静态缓存 ===
    private static List<Long> CACHED_ALL_IDS = null;
    private static List<SongInfo> CACHED_CURRENT_LIST = null;
    private static int CACHED_PAGE = 0;
    private static Tab CACHED_TAB = Tab.SEARCH;
    private static boolean CACHED_BROADCAST_MODE = false;
    // 【修复 Bug 1】删除了 HAS_CHECKED_LOGIN 静态变量，防止状态死锁

    private enum Tab { SEARCH, PLAYLIST }
    private Tab currentTab = Tab.SEARCH;
    private boolean isBroadcastMode = false;

    // 控件
    private EditBox searchBox;
    private SongListWidget songList;
    private FlatButton btnSearch, btnLoadPlaylist, btnPrev, btnNext;
    private FlatButton btnToggle, btnStop, btnMode, btnRandom; // 新增随机按钮
    private FlatButton btnStartLogin, btnCancelLogin;

    // 登录变量
    private QrCode qrCodeCache;
    private String loginKey;
    private Thread loginThread;
    private String loginStatusText = "等待获取二维码...";
    private boolean isQrLoading = false;

    // 数据
    private List<Long> allSongIdsCache;
    private int currentPage = 0;
    private final int PAGE_SIZE = 50;
    private Component statusText = Component.empty();

    // 颜色
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

        // 【修复 Bug 1】每次打开都重新检查登录状态
        // 1. 先加载 Cookie
        NeteaseApi.loadCookies();
        // 2. 简单检查 UID (如果已登录，这个检查是毫秒级的)
        if (NeteaseApi.getMyUid() > 0) {
            currentState = ScreenState.PLAYER;
        } else {
            currentState = ScreenState.LOGIN_PROMPT;
        }

        // 设置自动播放回调
        ClientMusicManager.onTrackFinishedCallback = this::autoPlayNext;

        this.clearWidgets();
        if (currentState == ScreenState.PLAYER) {
            initPlayerInterface();
        } else if (currentState == ScreenState.LOGIN_PROMPT) {
            initLoginPromptInterface();
        } else if (currentState == ScreenState.LOGIN_QR) {
            initLoginQrInterface();
        }
    }

    // === 【新增】自动播放逻辑 ===
    private void autoPlayNext() {
        // 如果当前没有缓存列表，无法自动播放
        if (CACHED_CURRENT_LIST == null || CACHED_CURRENT_LIST.isEmpty()) return;

        // 随机选择一首 (或者是顺序播放，这里用随机比较适合“听歌”场景)
        // 既然用户有随机播放的需求，我们就默认随机下一首，增加趣味性
        int nextIndex = new Random().nextInt(CACHED_CURRENT_LIST.size());
        SongInfo nextSong = CACHED_CURRENT_LIST.get(nextIndex);

        System.out.println(">>> [AutoPlay] 自动播放下一首: " + nextSong.name);

        // 执行播放逻辑 (复用 playLogic)
        // 注意：这里是在子线程回调的，playLogic 内部会处理线程安全，但为了保险，我们只调用逻辑部分
        playSong(nextSong);
    }

    private void initPlayerInterface() {
        int contentTop = topPos + 35;

        if (CACHED_TAB != null) this.currentTab = CACHED_TAB;
        if (CACHED_ALL_IDS != null) this.allSongIdsCache = CACHED_ALL_IDS;
        this.currentPage = CACHED_PAGE;
        this.isBroadcastMode = CACHED_BROADCAST_MODE;

        this.searchBox = new EditBox(this.font, leftPos + 10, contentTop + 10, 200, 18, Component.literal("搜索"));
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFF);
        this.addRenderableWidget(this.searchBox);

        this.btnSearch = new FlatButton(leftPos + 220, contentTop + 9, 50, 20, "GO", b -> doSearch());
        this.addRenderableWidget(this.btnSearch);

        this.btnLoadPlaylist = new FlatButton(leftPos + 10, contentTop + 10, 100, 20, "刷新歌单", b -> loadMyPlaylist());
        this.btnLoadPlaylist.visible = false;
        this.addRenderableWidget(this.btnLoadPlaylist);

        // --- 底部控制栏 ---
        int buttonsY = topPos + WINDOW_HEIGHT - 30;

        this.btnToggle = new FlatButton(leftPos + 10, buttonsY, 25, 20, "||", b -> PacketHandler.sendToServer(new C2SMusicActionPacket(1)));
        this.addRenderableWidget(this.btnToggle);

        this.btnStop = new FlatButton(leftPos + 40, buttonsY, 25, 20, "■", b -> PacketHandler.sendToServer(new C2SMusicActionPacket(0)));
        this.addRenderableWidget(this.btnStop);

        this.btnMode = new FlatButton(leftPos + 75, buttonsY, 60, 20, "🎧 私享", b -> {
            isBroadcastMode = !isBroadcastMode;
            CACHED_BROADCAST_MODE = isBroadcastMode;
            updateModeButton();
        });
        updateModeButton();
        this.addRenderableWidget(this.btnMode);

        // 【新增】随机播放按钮
        this.btnRandom = new FlatButton(leftPos + 140, buttonsY, 40, 20, "🎲", b -> playRandomSong());
        this.addRenderableWidget(this.btnRandom);

        this.btnPrev = new FlatButton(leftPos + WINDOW_WIDTH - 90, buttonsY, 35, 20, "<", b -> changePage(-1));
        this.btnNext = new FlatButton(leftPos + WINDOW_WIDTH - 50, buttonsY, 35, 20, ">", b -> changePage(1));
        this.btnPrev.active = currentPage > 0;
        if (allSongIdsCache != null) {
            this.btnNext.active = (currentPage + 1) * PAGE_SIZE < allSongIdsCache.size();
        } else {
            this.btnNext.active = false;
        }
        this.addRenderableWidget(this.btnPrev);
        this.addRenderableWidget(this.btnNext);

        int listY = contentTop + 40;
        int listH = WINDOW_HEIGHT - 35 - 40 - 65;
        this.songList = new SongListWidget(this.minecraft, WINDOW_WIDTH - 20, listH, listY);
        this.songList.setLeftPos(leftPos + 10);
        this.addWidget(this.songList);

        if (CACHED_CURRENT_LIST != null && !CACHED_CURRENT_LIST.isEmpty()) {
            this.songList.refreshList(CACHED_CURRENT_LIST);
        }
        updateTabVisibility();
    }

    // 【新增】随机播放一首当前列表的歌
    private void playRandomSong() {
        if (songList.children().isEmpty()) {
            statusText = Component.literal("列表为空，无法随机");
            return;
        }
        int index = new Random().nextInt(songList.children().size());
        SongListWidget.SongEntry entry = songList.children().get(index);
        // 调用条目的点击逻辑
        entry.playLogic(entry.song);
    }

    // 【核心】统一播放逻辑 (供点击、随机、自动播放调用)
    // 把它提取出来，方便复用
    private void playSong(SongInfo song) {
        String modeText = isBroadcastMode ? "§c[全服广播]" : "§a[私享模式]";
        new Thread(() -> {
            String url = NeteaseApi.getSongUrl(song.id);
            if (url == null) {
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c播放失败: VIP/无版权")));
                return;
            }
            // 【修复 Bug 2】随机/自动播放也必须遵守当前的广播模式
            if (isBroadcastMode) {
                PacketHandler.sendToServer(new C2SReportMusicPacket(url, song.name + " - " + song.artist, song.duration));
            } else {
                Minecraft.getInstance().execute(() -> ClientMusicManager.play(url, song.name + " - " + song.artist, song.duration));
            }
        }).start();
    }

    // ... (initLoginPromptInterface, initLoginQrInterface, startLoginProcess, stopLoginProcess 保持不变) ...
    private void initLoginPromptInterface() {
        this.btnStartLogin = new FlatButton(leftPos + (WINDOW_WIDTH - 120) / 2, topPos + (WINDOW_HEIGHT - 30) / 2, 120, 30, "扫码登录网易云", b -> {
            currentState = ScreenState.LOGIN_QR;
            startLoginProcess();
            init();
        });
        this.addRenderableWidget(this.btnStartLogin);
    }
    private void initLoginQrInterface() {
        this.btnCancelLogin = new FlatButton(leftPos + (WINDOW_WIDTH - 80) / 2, topPos + WINDOW_HEIGHT - 40, 80, 20, "取消", b -> {
            stopLoginProcess();
            currentState = ScreenState.LOGIN_PROMPT;
            init();
        });
        this.addRenderableWidget(this.btnCancelLogin);
    }
    private void startLoginProcess() {
        if (loginThread != null && loginThread.isAlive()) return;
        isQrLoading = true;
        loginStatusText = "正在连接服务器...";
        loginThread = new Thread(() -> {
            try {
                loginKey = NeteaseApi.getLoginKey();
                if (loginKey == null) { loginStatusText = "§c获取 Key 失败"; return; }
                String url = NeteaseApi.getLoginQrUrl(loginKey);
                this.qrCodeCache = QrCode.encodeText(url, QrCode.Ecc.MEDIUM);
                isQrLoading = false;
                loginStatusText = "请使用网易云 APP 扫码";
                while (currentState == ScreenState.LOGIN_QR) {
                    if (this.minecraft == null) break;
                    NeteaseApi.LoginResult result = NeteaseApi.checkLoginStatus(loginKey);
                    if (result.code == 800) { loginStatusText = "§c二维码已过期"; break; }
                    else if (result.code == 802) { loginStatusText = "§a扫描成功，请确认"; }
                    else if (result.code == 803) {
                        loginStatusText = "§a登录成功！";
                        BotConfig.CLIENT.neteaseCookie.set(result.cookie);
                        NeteaseApi.loadCookies();
                        Minecraft.getInstance().execute(() -> {
                            currentState = ScreenState.PLAYER;
                            CACHED_ALL_IDS = null; CACHED_CURRENT_LIST = null;
                            loadMyPlaylist();
                            init();
                        });
                        break;
                    }
                    Thread.sleep(1500);
                }
            } catch (Exception e) { e.printStackTrace(); loginStatusText = "错误: " + e.getMessage(); }
        });
        loginThread.start();
    }
    private void stopLoginProcess() { qrCodeCache = null; }
    @Override public void onClose() { stopLoginProcess(); super.onClose(); }

    private void updateModeButton() {
        if (isBroadcastMode) btnMode.setMessage(Component.literal("📢 全服"));
        else btnMode.setMessage(Component.literal("🎧 私享"));
    }

    private void switchTab(Tab tab) {
        this.currentTab = tab;
        CACHED_TAB = tab;
        updateTabVisibility();
        if (tab == Tab.PLAYLIST && CACHED_CURRENT_LIST != null && songList.children().isEmpty()) {
            this.songList.refreshList(CACHED_CURRENT_LIST);
        }
    }

    private void updateTabVisibility() {
        boolean isSearch = (currentTab == Tab.SEARCH);
        if (searchBox != null) {
            this.searchBox.visible = isSearch;
            this.searchBox.setEditable(isSearch);
            this.btnSearch.visible = isSearch;
            this.btnLoadPlaylist.visible = !isSearch;
        }
    }

    private void doSearch() {
        String k = searchBox.getValue();
        if (k.isEmpty()) return;
        statusText = Component.literal("正在搜索...");
        new Thread(() -> {
            List<SongInfo> res = NeteaseApi.searchList(k);
            Minecraft.getInstance().execute(() -> {
                songList.refreshList(res);
                CACHED_CURRENT_LIST = res;
                statusText = Component.literal("找到 " + res.size() + " 首歌曲");
            });
        }).start();
    }

    private void loadMyPlaylist() {
        statusText = Component.literal("正在获取歌单...");
        new Thread(() -> {
            long uid = NeteaseApi.getMyUid();
            if (uid == 0) {
                Minecraft.getInstance().execute(() -> {
                    currentState = ScreenState.LOGIN_PROMPT;
                    init();
                });
                return;
            }
            var pl = NeteaseApi.getUserPlaylists(uid);
            if (pl != null && pl.size() > 0) {
                long fid = pl.get(0).getAsJsonObject().get("id").getAsLong();
                allSongIdsCache = NeteaseApi.getPlaylistSongIds(fid);
                CACHED_ALL_IDS = allSongIdsCache;
                currentPage = 0; CACHED_PAGE = 0;
                loadCurrentPageSongs();
            }
        }).start();
    }

    private void changePage(int off) {
        currentPage += off; CACHED_PAGE = currentPage;
        loadCurrentPageSongs();
    }

    private void loadCurrentPageSongs() {
        if (allSongIdsCache == null) return;
        new Thread(() -> {
            int s = currentPage * PAGE_SIZE;
            int e = Math.min(s + PAGE_SIZE, allSongIdsCache.size());
            if (s >= allSongIdsCache.size()) return;
            List<SongInfo> d = NeteaseApi.getSongsDetail(allSongIdsCache.subList(s, e));
            Minecraft.getInstance().execute(() -> {
                songList.refreshList(d);
                CACHED_CURRENT_LIST = d;
                btnPrev.active = currentPage > 0;
                btnNext.active = e < allSongIdsCache.size();
                songList.setScrollAmount(0);
                statusText = Component.literal("页码: " + (currentPage + 1));
            });
        }).start();
    }

    // ... (render, renderProgressBar, renderTab 保持不变) ...
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        g.fill(leftPos, topPos, leftPos + WINDOW_WIDTH, topPos + WINDOW_HEIGHT, COLOR_BG);
        g.fill(leftPos, topPos, leftPos + WINDOW_WIDTH, topPos + 30, COLOR_HEADER);
        g.drawString(this.font, "AiBot 云音乐", leftPos + 10, topPos + 10, COLOR_TEXT_ACTIVE, false);

        if (currentState == ScreenState.PLAYER) {
            renderPlayerLayer(g, mx, my, pt);
        } else if (currentState == ScreenState.LOGIN_PROMPT) {
            g.drawCenteredString(this.font, "您尚未登录", leftPos + WINDOW_WIDTH / 2, topPos + 60, 0xFFE0E0E0);
            super.render(g, mx, my, pt);
        } else if (currentState == ScreenState.LOGIN_QR) {
            renderQrLayer(g, mx, my, pt);
            super.render(g, mx, my, pt);
        }
    }
    private void renderQrLayer(GuiGraphics g, int mx, int my, float pt) {
        g.drawCenteredString(this.font, loginStatusText, leftPos + WINDOW_WIDTH / 2, topPos + 45, 0xFFE0E0E0);
        if (qrCodeCache != null) {
            int scale = 3; int border = 2;
            int qrPixelSize = (qrCodeCache.size + border * 2) * scale;
            int startX = leftPos + (WINDOW_WIDTH - qrPixelSize) / 2;
            int startY = topPos + 70;
            g.fill(startX, startY, startX + qrPixelSize, startY + qrPixelSize, 0xFFFFFFFF);
            for (int y = 0; y < qrCodeCache.size; y++) {
                for (int x = 0; x < qrCodeCache.size; x++) {
                    if (qrCodeCache.getModule(x, y)) {
                        int dx = startX + (x + border) * scale;
                        int dy = startY + (y + border) * scale;
                        g.fill(dx, dy, dx + scale, dy + scale, 0xFF000000);
                    }
                }
            }
        } else if (isQrLoading) {
            g.drawCenteredString(this.font, "Loading...", leftPos + WINDOW_WIDTH / 2, topPos + 100, 0xFFAAAAAA);
        }
    }
    private void renderPlayerLayer(GuiGraphics g, int mx, int my, float pt) {
        renderTab(g, "搜 索", Tab.SEARCH, leftPos + 100, topPos + 8, mx, my);
        renderTab(g, "我的喜欢", Tab.PLAYLIST, leftPos + 160, topPos + 8, mx, my);
        int activeX = (currentTab == Tab.SEARCH) ? leftPos + 100 : leftPos + 160;
        int activeW = (currentTab == Tab.SEARCH) ? 30 : 45;
        g.fill(activeX - 2, topPos + 28, activeX + activeW + 2, topPos + 30, COLOR_ACCENT);
        if (currentTab == Tab.SEARCH) {
            g.fill(searchBox.getX() - 2, searchBox.getY() - 2, searchBox.getX() + searchBox.getWidth() + 2, searchBox.getY() + searchBox.getHeight() + 2, 0xFF202020);
        }
        renderProgressBar(g);
        this.btnToggle.setMessage(Component.literal(ClientMusicManager.isPaused() ? "▶" : "||"));
        if (!statusText.getString().isEmpty()) {
            g.drawString(this.font, statusText, leftPos + 10, topPos + WINDOW_HEIGHT - 12, 0xFF666666, false);
        }
        this.songList.render(g, mx, my, pt);
        super.render(g, mx, my, pt);
    }
    private void renderProgressBar(GuiGraphics g) {
        if (!ClientMusicManager.isPlaying()) return;
        long current = ClientMusicManager.getProgress();
        long total = ClientMusicManager.currentDuration;
        if (total <= 0) total = 1;
        float percent = (float) current / total;
        percent = Math.min(1.0f, Math.max(0.0f, percent));
        int barX = leftPos + 10;
        int barY = topPos + WINDOW_HEIGHT - 45;
        int barW = WINDOW_WIDTH - 20;
        int barH = 3;
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF303030);
        g.fill(barX, barY, barX + (int) (barW * percent), barY + barH, COLOR_ACCENT);
        String timeStr = formatTime(current) + " / " + formatTime(total);
        g.pose().pushPose();
        float scale = 0.8f;
        g.pose().scale(scale, scale, scale);
        int textX = (int)((barX + barW) / scale) - this.font.width(timeStr);
        int textY = (int)((barY - 10) / scale);
        g.drawString(this.font, timeStr, textX, textY, 0xFFAAAAAA, false);
        g.pose().popPose();
    }
    private void renderTab(GuiGraphics g, String text, Tab tab, int x, int y, int mx, int my) {
        boolean isActive = (currentTab == tab);
        boolean isHover = mx >= x && mx <= x + font.width(text) && my >= topPos && my <= topPos + 30;
        int color = isActive ? COLOR_TEXT_ACTIVE : (isHover ? 0xFFE0E0E0 : COLOR_TEXT_IDLE);
        g.drawString(this.font, text, x, y, color, false);
    }
    private String formatTime(long ms) {
        long sec = ms / 1000;
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (currentState == ScreenState.PLAYER) {
            if (mouseY >= topPos && mouseY <= topPos + 30) {
                if (mouseX >= leftPos + 100 && mouseX <= leftPos + 130) switchTab(Tab.SEARCH);
                if (mouseX >= leftPos + 160 && mouseX <= leftPos + 220) switchTab(Tab.PLAYLIST);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // FlatButton (保持不变)
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
            if (this == btnMode && isBroadcastMode) textColor = 0xFFFF5555;
            g.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
        }
    }

    // SongListWidget
    class SongListWidget extends ObjectSelectionList<SongListWidget.SongEntry> {
        private final int listY;
        public SongListWidget(Minecraft mc, int width, int height, int top) {
            super(mc, width, height, top, top + height, 24);
            this.listY = top;
        }
        @Override protected int getScrollbarPosition() { return getLeft() + getRowWidth() + 6; }
        @Override public int getRowWidth() { return width - 10; }
        @Override public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
            g.enableScissor(getLeft(), getTop(), getRight(), getBottom());
            super.render(g, mx, my, pt);
            g.disableScissor();
        }
        @Override protected void renderBackground(@NotNull GuiGraphics g) {}
        @Override protected void renderDecorations(@NotNull GuiGraphics g, int mx, int my) {}

        public void refreshList(List<SongInfo> songs) {
            this.clearEntries();
            for (SongInfo s : songs) this.addEntry(new SongEntry(s));
        }

        public class SongEntry extends ObjectSelectionList.Entry<SongEntry> {
            private final SongInfo song;
            private long lastClickTime = 0;
            public SongEntry(SongInfo song) { this.song = song; }

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
                        playLogic(song);
                    }
                    lastClickTime = now;
                    return true;
                }
                return false;
            }

            // 公开这个方法，供随机播放调用
            public void playLogic(SongInfo song) {
                playSong(song); // 委托给外部类的方法
            }
            @Override public Component getNarration() { return Component.literal(song.name); }
        }
    }
}