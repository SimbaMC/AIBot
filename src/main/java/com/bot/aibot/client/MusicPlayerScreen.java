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

    // === 播放模式 ===
    private enum PlaybackMode {
        LIST_LOOP("🔁", "列表循环"),
        SINGLE_LOOP("🔂", "单曲循环"),
        RANDOM("🔀", "随机播放");

        final String icon;
        final String name;
        PlaybackMode(String icon, String name) { this.icon = icon; this.name = name; }
    }

    // === 静态缓存 ===
    private static List<Long> CACHED_ALL_IDS = null;
    private static List<SongInfo> CACHED_CURRENT_LIST = null;
    private static int CACHED_PAGE = 0;
    private static Tab CACHED_TAB = Tab.SEARCH;
    private static boolean CACHED_BROADCAST_MODE = false;
    private static PlaybackMode CACHED_PLAYBACK_MODE = PlaybackMode.LIST_LOOP;
    private static SongInfo CACHED_PLAYING_SONG = null;
    private static long lastBroadcastTime = 0;

    private enum Tab { SEARCH, PLAYLIST }
    private Tab currentTab = Tab.SEARCH;
    private boolean isBroadcastMode = false;
    private PlaybackMode currentPlaybackMode = PlaybackMode.LIST_LOOP;

    // 控件
    private EditBox searchBox;
    private SongListWidget songList;
    private FlatButton btnSearch, btnLoadPlaylist;

    // 【修改】播放控制按钮组
    private FlatButton btnPlayPrev, btnToggle, btnPlayNext, btnStop;
    private FlatButton btnLoopMode, btnMode;
    private FlatButton btnPagePrev, btnPageNext; // 改名区分翻页和切歌

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

    private static final int COLOR_BG = 0xCC101010;
    private static final int COLOR_HEADER = 0xFF000000;
    private static final int COLOR_ACCENT = 0xFF2ECC71;
    private static final int COLOR_TEXT_IDLE = 0xFFAAAAAA;
    private static final int COLOR_TEXT_ACTIVE = 0xFFFFFFFF;
    private static final int COLOR_HOVER = 0x20FFFFFF;

    public MusicPlayerScreen() { super(Component.literal("AiBot Netease")); }

    @Override
    protected void init() {
        this.leftPos = (this.width - WINDOW_WIDTH) / 2;
        this.topPos = (this.height - WINDOW_HEIGHT) / 2;

        NeteaseApi.loadCookies();
        if (NeteaseApi.getMyUid() > 0) currentState = ScreenState.PLAYER;
        else if (currentState != ScreenState.LOGIN_QR) currentState = ScreenState.LOGIN_PROMPT;

        // 回调只负责自动播放 (isAuto = true)
        ClientMusicManager.onTrackFinishedCallback = () -> trySwitchSong(true, true);

        this.clearWidgets();
        if (currentState == ScreenState.PLAYER) initPlayerInterface();
        else if (currentState == ScreenState.LOGIN_PROMPT) initLoginPromptInterface();
        else if (currentState == ScreenState.LOGIN_QR) initLoginQrInterface();
    }

    // === 【核心逻辑】切歌控制 ===
    // isNext: true=下一首, false=上一首
    // isAuto: true=播放结束自动触发(受广播限制), false=手动点击(无视广播限制)
    private void trySwitchSong(boolean isNext, boolean isAuto) {
        // 1. 广播模式下，禁止自动切歌，但允许手动切歌
        if (isBroadcastMode && isAuto) return;

        if (CACHED_CURRENT_LIST == null || CACHED_CURRENT_LIST.isEmpty()) return;

        SongInfo nextSong = null;

        // 简单处理：随机模式下，上一首/下一首都是随机
        if (currentPlaybackMode == PlaybackMode.RANDOM) {
            int rnd = new Random().nextInt(CACHED_CURRENT_LIST.size());
            nextSong = CACHED_CURRENT_LIST.get(rnd);
        } else {
            // 列表循环 / 单曲循环
            // 如果是手动切歌(isAuto=false)，即使是单曲循环模式，也应该切到下一首，而不是重播当前这首
            // 如果是自动播放(isAuto=true) 且 单曲循环，则重播当前
            if (isAuto && currentPlaybackMode == PlaybackMode.SINGLE_LOOP && CACHED_PLAYING_SONG != null) {
                nextSong = CACHED_PLAYING_SONG;
            } else {
                // 找当前位置
                int idx = -1;
                if (CACHED_PLAYING_SONG != null) {
                    for (int i = 0; i < CACHED_CURRENT_LIST.size(); i++) {
                        if (CACHED_CURRENT_LIST.get(i).id.equals(CACHED_PLAYING_SONG.id)) {
                            idx = i; break;
                        }
                    }
                }

                // 计算新索引
                int size = CACHED_CURRENT_LIST.size();
                int nextIdx;
                if (isNext) {
                    nextIdx = (idx + 1) % size;
                } else {
                    nextIdx = (idx - 1 + size) % size; // 保证正数
                }
                nextSong = CACHED_CURRENT_LIST.get(nextIdx);
            }
        }

        if (nextSong != null) {
            playSong(nextSong);
        }
    }
    private void updateButtonStates() {
        if (btnPlayPrev == null || btnPlayNext == null || btnLoopMode == null) return;

        // 如果是广播模式：禁止切歌，禁止切循环模式（强制单次）
        if (isBroadcastMode) {
            btnPlayPrev.active = false;
            btnPlayNext.active = false;
            btnLoopMode.active = false;
            // 可以在这里把 loopMode 临时显示为 "1" (单次)，但为了逻辑简单，置灰即可
        } else {
            // 私享模式：全部可用
            btnPlayPrev.active = true;
            btnPlayNext.active = true;
            btnLoopMode.active = true;
        }
    }

    private void initPlayerInterface() {
        int contentTop = topPos + 35;

        // 恢复缓存
        if (CACHED_TAB != null) this.currentTab = CACHED_TAB;
        if (CACHED_ALL_IDS != null) this.allSongIdsCache = CACHED_ALL_IDS;
        this.currentPage = CACHED_PAGE;
        this.isBroadcastMode = CACHED_BROADCAST_MODE;
        this.currentPlaybackMode = CACHED_PLAYBACK_MODE;

        // 搜索区域
        this.searchBox = new EditBox(this.font, leftPos + 10, contentTop + 10, 200, 18, Component.literal("搜索"));
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFF);
        this.addRenderableWidget(this.searchBox);

        this.btnSearch = new FlatButton(leftPos + 220, contentTop + 9, 50, 20, "GO", b -> doSearch());
        this.addRenderableWidget(this.btnSearch);

        this.btnLoadPlaylist = new FlatButton(leftPos + 10, contentTop + 10, 100, 20, "刷新歌单", b -> loadMyPlaylist());
        this.btnLoadPlaylist.visible = false;
        this.addRenderableWidget(this.btnLoadPlaylist);

        // --- 底部控制栏布局 (Y=210) ---
        int bY = topPos + WINDOW_HEIGHT - 30;

        // 1. 播放控制组 (左侧)
        // |< (上一首)
        this.btnPlayPrev = new FlatButton(leftPos + 10, bY, 20, 20, "|<", b -> trySwitchSong(false, false));
        this.addRenderableWidget(this.btnPlayPrev);

        // || (暂停/播放)
        this.btnToggle = new FlatButton(leftPos + 34, bY, 24, 20, "||", b -> PacketHandler.sendToServer(new C2SMusicActionPacket(1)));
        this.addRenderableWidget(this.btnToggle);

        // >| (下一首)
        this.btnPlayNext = new FlatButton(leftPos + 62, bY, 20, 20, ">|", b -> trySwitchSong(true, false));
        this.addRenderableWidget(this.btnPlayNext);

        // ■ (停止)
        this.btnStop = new FlatButton(leftPos + 86, bY, 20, 20, "■", b -> PacketHandler.sendToServer(new C2SMusicActionPacket(0)));
        this.addRenderableWidget(this.btnStop);

        // 2. 模式组 (中间)
        // 循环模式
        this.btnLoopMode = new FlatButton(leftPos + 115, bY, 25, 20, currentPlaybackMode.icon, b -> {
            switch (currentPlaybackMode) {
                case LIST_LOOP -> currentPlaybackMode = PlaybackMode.SINGLE_LOOP;
                case SINGLE_LOOP -> currentPlaybackMode = PlaybackMode.RANDOM;
                case RANDOM -> currentPlaybackMode = PlaybackMode.LIST_LOOP;
            }
            CACHED_PLAYBACK_MODE = currentPlaybackMode;
            btnLoopMode.setMessage(Component.literal(currentPlaybackMode.icon));
        });
        this.addRenderableWidget(this.btnLoopMode);

        // 私享/广播
        this.btnMode = new FlatButton(leftPos + 145, bY, 50, 20, "", b -> {
            // 1. 如果准备开启广播模式，检查冷却
            if (!isBroadcastMode) {
                long now = System.currentTimeMillis();
                int cooldownSec = BotConfig.SERVER.broadcastCooldown.get();
                long cooldownMs = cooldownSec * 1000L;

                if (now - lastBroadcastTime < cooldownMs) {
                    long remain = (cooldownMs - (now - lastBroadcastTime)) / 1000;
                    statusText = Component.literal("§c冷却中: " + remain + "s");
                    return; // 阻止切换
                }
            }

            // 2. 切换状态
            isBroadcastMode = !isBroadcastMode;
            CACHED_BROADCAST_MODE = isBroadcastMode;
            updateModeButton();
            updateButtonStates(); // 【关键】刷新按钮状态
        });
        updateModeButton();
        this.addRenderableWidget(this.btnMode);

        // 3. 翻页组 (右侧)
        // < (上一页)
        this.btnPagePrev = new FlatButton(leftPos + WINDOW_WIDTH - 60, bY, 25, 20, "<", b -> changePage(-1));
        // > (下一页)
        this.btnPageNext = new FlatButton(leftPos + WINDOW_WIDTH - 30, bY, 25, 20, ">", b -> changePage(1));

        this.btnPagePrev.active = currentPage > 0;
        this.btnPageNext.active = (allSongIdsCache != null) && ((currentPage + 1) * PAGE_SIZE < allSongIdsCache.size());

        this.addRenderableWidget(this.btnPagePrev);
        this.addRenderableWidget(this.btnPageNext);

        // 列表
        int listY = contentTop + 40;
        int listH = WINDOW_HEIGHT - 35 - 40 - 65;
        this.songList = new SongListWidget(this.minecraft, WINDOW_WIDTH - 20, listH, listY);
        this.songList.setLeftPos(leftPos + 10);
        this.addWidget(this.songList);

        if (CACHED_CURRENT_LIST != null && !CACHED_CURRENT_LIST.isEmpty()) {
            this.songList.refreshList(CACHED_CURRENT_LIST);
        }
        updateTabVisibility();
        updateButtonStates();
    }

    private void playSong(SongInfo song) {
        CACHED_PLAYING_SONG = song;
        final boolean performBroadcast = isBroadcastMode;
        // 2. 如果是广播模式：记录时间 -> 自动切回私享 -> 刷新UI
        if (isBroadcastMode) {
            lastBroadcastTime = System.currentTimeMillis();

            isBroadcastMode = false;
            CACHED_BROADCAST_MODE = false;

            updateModeButton();
            updateButtonStates(); // 恢复按钮可用

            statusText = Component.literal("§e广播已发送，自动切回私享");
        }
        String modeText = isBroadcastMode ? "§c[全服广播]" : "§a[私享模式]";

        new Thread(() -> {
            String url = NeteaseApi.getSongUrl(song.id);
            if (url == null) {
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c播放失败: VIP/无版权")));
                return;
            }
            if (performBroadcast) {
                // 【修正】这里填 true，明确告诉服务端：“这是一次广播请求！”
                PacketHandler.sendToServer(new C2SReportMusicPacket(url, song.name + " - " + song.artist, song.duration, true));
            } else {
                Minecraft.getInstance().execute(() -> ClientMusicManager.play(url, song.name + " - " + song.artist, song.duration));
            }
        }).start();
    }
    public static void resetCooldown() {
        lastBroadcastTime = 0;
        // 如果需要，也可以在这里顺便把状态文字清空
        // CACHED_BROADCAST_MODE = false; // 可选：是否顺便重置回私享模式？看你需求，这里只重置时间
    }

    // ... (initLoginPromptInterface 等保持不变) ...
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
                    if (currentState != ScreenState.LOGIN_QR) currentState = ScreenState.LOGIN_PROMPT;
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
                btnPagePrev.active = currentPage > 0;
                btnPageNext.active = e < allSongIdsCache.size();
                songList.setScrollAmount(0);
                statusText = Component.literal("页码: " + (currentPage + 1));
            });
        }).start();
    }

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

        if (currentState == ScreenState.PLAYER && btnLoopMode.isHovered()) {
            g.renderTooltip(this.font, Component.literal(currentPlaybackMode.name), mx, my);
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

            public void playLogic(SongInfo song) {
                playSong(song);
            }
            @Override public Component getNarration() { return Component.literal(song.name); }
        }
    }
}