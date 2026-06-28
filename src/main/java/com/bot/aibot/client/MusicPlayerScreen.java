package com.bot.aibot.client;

import com.bot.aibot.API.QrCode;
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.C2SReportMusicPacket;
import com.bot.aibot.utils.NeteaseApi;
import com.bot.aibot.utils.SongInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MusicPlayerScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

    private final int WINDOW_WIDTH = 340;
    private final int WINDOW_HEIGHT = 240;
    private int leftPos, topPos;

    private enum ScreenState { PLAYER, LOGIN_PROMPT, LOGIN_QR }
    private ScreenState currentState = ScreenState.LOGIN_PROMPT;

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
    private static List<PlaylistInfo> CACHED_USER_PLAYLISTS = null;
    private static int CACHED_PAGE = 0;
    private static Tab CACHED_TAB = Tab.SEARCH;
    private static boolean CACHED_BROADCAST_MODE = false;
    private static PlaybackMode CACHED_PLAYBACK_MODE = PlaybackMode.LIST_LOOP;
    private static SongInfo CACHED_PLAYING_SONG = null;
    private static long lastBroadcastTime = 0;

    // 文件夹状态缓存
    private static boolean CACHED_IN_PLAYLIST_FOLDER = false;
    private static String CACHED_FOLDER_NAME = "";
    private static long CACHED_PLAYLIST_ID = -1; // 【新增】缓存当前歌单ID

    private enum Tab { SEARCH, MY_LIKE, PLAYLISTS }
    private Tab currentTab = Tab.SEARCH;

    // 歌单浏览状态
    private boolean inPlaylistFolder = false;
    private String currentFolderName = "";
    private long currentPlaylistId = -1; // 【新增】当前所在歌单ID

    private boolean isBroadcastMode = false;
    private PlaybackMode currentPlaybackMode = PlaybackMode.LIST_LOOP;

    public static volatile String EXPECTED_URL = "";

    // 控件
    private EditBox searchBox;
    private SongListWidget songList;
    private PlaylistListWidget playlistList;
    private FlatButton btnSearch, btnBack;
    private FlatButton btnPlayPrev, btnToggle, btnPlayNext, btnStop;
    private FlatButton btnLoopMode, btnMode;
    private FlatButton btnPagePrev, btnPageNext;
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

    public static class PlaylistInfo {
        public long id;
        public String name;
        public long trackCount;
        public PlaylistInfo(long id, String name, long trackCount) {
            this.id = id;
            this.name = name;
            this.trackCount = trackCount;
        }
    }

    public MusicPlayerScreen() { super(Component.literal("AiBot Netease")); }

    @Override
    protected void init() {
        this.leftPos = (this.width - WINDOW_WIDTH) / 2;
        this.topPos = (this.height - WINDOW_HEIGHT) / 2;

        NeteaseApi.loadCookies();
        if (NeteaseApi.getMyUid() > 0) currentState = ScreenState.PLAYER;
        else if (currentState != ScreenState.LOGIN_QR) currentState = ScreenState.LOGIN_PROMPT;

        ClientMusicManager.onTrackFinishedCallback = () -> trySwitchSong(true, true);

        this.clearWidgets();
        if (currentState == ScreenState.PLAYER) initPlayerInterface();
        else if (currentState == ScreenState.LOGIN_PROMPT) initLoginPromptInterface();
        else if (currentState == ScreenState.LOGIN_QR) initLoginQrInterface();
    }

    // 强制手动分发滚轮事件 (解决歌单列表滚动问题)
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (this.playlistList != null && this.playlistList.visible) {
            if (this.playlistList.mouseScrolled(mouseX, mouseY, deltaX, deltaY)) return true;
        }
        if (this.songList != null && this.songList.visible) {
            if (this.songList.mouseScrolled(mouseX, mouseY, deltaX, deltaY)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private void trySwitchSong(boolean isNext, boolean isAuto) {
        if (isBroadcastMode && isAuto) return;
        if (CACHED_CURRENT_LIST == null || CACHED_CURRENT_LIST.isEmpty()) return;

        SongInfo nextSong = null;
        if (currentPlaybackMode == PlaybackMode.RANDOM) {
            int rnd = new Random().nextInt(CACHED_CURRENT_LIST.size());
            nextSong = CACHED_CURRENT_LIST.get(rnd);
        } else {
            if (isAuto && currentPlaybackMode == PlaybackMode.SINGLE_LOOP && CACHED_PLAYING_SONG != null) {
                nextSong = CACHED_PLAYING_SONG;
            } else {
                int idx = -1;
                if (CACHED_PLAYING_SONG != null) {
                    for (int i = 0; i < CACHED_CURRENT_LIST.size(); i++) {
                        if (CACHED_CURRENT_LIST.get(i).id.equals(CACHED_PLAYING_SONG.id)) {
                            idx = i; break;
                        }
                    }
                }
                int size = CACHED_CURRENT_LIST.size();
                int nextIdx = isNext ? (idx + 1) % size : (idx - 1 + size) % size;
                nextSong = CACHED_CURRENT_LIST.get(nextIdx);
            }
        }
        if (nextSong != null) playSong(nextSong);
    }

    private void updateButtonStates() {
        if (btnPlayPrev == null) return;
        boolean active = !isBroadcastMode;
        btnPlayPrev.active = active;
        btnPlayNext.active = active;
        btnLoopMode.active = active;
    }

    private void initPlayerInterface() {
        int contentTop = topPos + 35;

        // 恢复缓存
        if (CACHED_TAB != null) this.currentTab = CACHED_TAB;
        if (CACHED_ALL_IDS != null) this.allSongIdsCache = CACHED_ALL_IDS;
        this.currentPage = CACHED_PAGE;
        this.isBroadcastMode = CACHED_BROADCAST_MODE;
        this.currentPlaybackMode = CACHED_PLAYBACK_MODE;

        // 恢复文件夹状态
        this.inPlaylistFolder = CACHED_IN_PLAYLIST_FOLDER;
        this.currentFolderName = CACHED_FOLDER_NAME;
        this.currentPlaylistId = CACHED_PLAYLIST_ID;

        // 搜索框
        this.searchBox = new EditBox(this.font, leftPos + 10, contentTop + 10, 200, 18, Component.literal("搜索"));
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFF);
        this.addRenderableWidget(this.searchBox);

        this.btnSearch = new FlatButton(leftPos + 220, contentTop + 9, 50, 20, "GO", b -> doSearch());
        this.addRenderableWidget(this.btnSearch);

        // 返回按钮
        this.btnBack = new FlatButton(leftPos + 10, contentTop + 9, 80, 20, "<- 返回列表", b -> {
            if (currentTab == Tab.PLAYLISTS && inPlaylistFolder) {
                inPlaylistFolder = false;
                CACHED_IN_PLAYLIST_FOLDER = false;
                updateTabVisibility();
            }
        });
        this.btnBack.visible = false;
        this.addRenderableWidget(this.btnBack);

        // 底部控制栏
        int bY = topPos + WINDOW_HEIGHT - 30;
        this.btnPlayPrev = new FlatButton(leftPos + 10, bY, 20, 20, "|<", b -> trySwitchSong(false, false));
        this.addRenderableWidget(this.btnPlayPrev);
        this.btnToggle = new FlatButton(leftPos + 34, bY, 24, 20, "||", b -> ClientMusicManager.togglePause());
        this.addRenderableWidget(this.btnToggle);
        this.btnPlayNext = new FlatButton(leftPos + 62, bY, 20, 20, ">|", b -> trySwitchSong(true, false));
        this.addRenderableWidget(this.btnPlayNext);
        this.btnStop = new FlatButton(leftPos + 86, bY, 20, 20, "■", b -> ClientMusicManager.stop());
        this.addRenderableWidget(this.btnStop);

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

        this.btnMode = new FlatButton(leftPos + 145, bY, 50, 20, "", b -> {
            if (!isBroadcastMode) {
                long now = System.currentTimeMillis();
                int cooldownSec = BotConfig.SERVER.broadcastCooldown.get();
                if (now - lastBroadcastTime < cooldownSec * 1000L) {
                    long remain = (cooldownSec * 1000L - (now - lastBroadcastTime)) / 1000;
                    statusText = Component.literal("§c冷却中: " + remain + "s");
                    return;
                }
            }
            isBroadcastMode = !isBroadcastMode;
            CACHED_BROADCAST_MODE = isBroadcastMode;
            updateModeButton();
            updateButtonStates();
        });
        updateModeButton();
        this.addRenderableWidget(this.btnMode);

        this.btnPagePrev = new FlatButton(leftPos + WINDOW_WIDTH - 60, bY, 25, 20, "<", b -> changePage(-1));
        this.btnPageNext = new FlatButton(leftPos + WINDOW_WIDTH - 30, bY, 25, 20, ">", b -> changePage(1));
        this.addRenderableWidget(this.btnPagePrev);
        this.addRenderableWidget(this.btnPageNext);

        // 列表
        int listY = contentTop + 40;
        int listH = WINDOW_HEIGHT - 35 - 40 - 65;

        this.songList = new SongListWidget(this.minecraft, WINDOW_WIDTH - 20, listH, listY);
        this.songList.setX(leftPos + 10);
        this.addWidget(this.songList);

        this.playlistList = new PlaylistListWidget(this.minecraft, WINDOW_WIDTH - 20, listH, listY);
        this.playlistList.setX(leftPos + 10);
        this.addWidget(this.playlistList);

        if (CACHED_CURRENT_LIST != null) this.songList.refreshList(CACHED_CURRENT_LIST);
        if (CACHED_USER_PLAYLISTS != null) this.playlistList.refreshList(CACHED_USER_PLAYLISTS);

        updateTabVisibility();
        updateButtonStates();
        updatePageButtons();
    }

    private void updatePageButtons() {
        if (currentTab == Tab.PLAYLISTS && !inPlaylistFolder) {
            btnPagePrev.active = false;
            btnPageNext.active = false;
        } else {
            btnPagePrev.active = currentPage > 0;
            btnPageNext.active = (allSongIdsCache != null) && ((currentPage + 1) * PAGE_SIZE < allSongIdsCache.size());
        }
    }

    private void playSong(SongInfo song) {
        CACHED_PLAYING_SONG = song;
        final boolean performBroadcast = isBroadcastMode;
        if (isBroadcastMode) {
            lastBroadcastTime = System.currentTimeMillis();
            isBroadcastMode = false;
            CACHED_BROADCAST_MODE = false;
            updateModeButton();
            updateButtonStates();
            statusText = Component.literal("§e广播已发送，自动切回私享");
        }
        new Thread(() -> {
            String url = NeteaseApi.getSongUrl(song.id);
            if (url == null) {
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c播放失败: VIP/无版权")));
                return;
            }
            EXPECTED_URL = url;
            ClientMusicManager.onTrackFinishedCallback = () -> trySwitchSong(true, true);
            if (performBroadcast) {
                LOGGER.info(">>> [Music] 尝试发送广播播放请求: song={}, artist={}, url={}", song.name, song.artist, url);
                PacketHandler.sendToServer(new C2SReportMusicPacket(url, song.name + " - " + song.artist, song.duration, true));
                Minecraft.getInstance().execute(() -> {
                    // 迁移期兜底：网络通道未就绪时，避免“广播模式点击后无响应”
                    ClientMusicManager.play(url, song.name + " - " + song.artist, song.duration);
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§e[Bot] 广播通道暂不可用，已回退为本地播放。"), true
                        );
                    }
                });
            } else {
                Minecraft.getInstance().execute(() -> ClientMusicManager.play(url, song.name + " - " + song.artist, song.duration));
            }
        }).start();
    }

    public static void resetCooldown() { lastBroadcastTime = 0; }

    // --- Tab 切换逻辑 (修复版) ---
    private void switchTab(Tab tab) {
        // 1. 如果在歌单文件夹内再次点击“我的歌单”Tab，则返回上一级
        if (this.currentTab == Tab.PLAYLISTS && tab == Tab.PLAYLISTS && inPlaylistFolder) {
            inPlaylistFolder = false;
            CACHED_IN_PLAYLIST_FOLDER = false;
            updateTabVisibility();
            return;
        }

        this.currentTab = tab;
        CACHED_TAB = tab;

        // 【关键】这里不再强制重置 inPlaylistFolder = false
        // 从而保留了“切出去再切回来”的状态

        updateTabVisibility();

        // 更新缓存
        CACHED_IN_PLAYLIST_FOLDER = inPlaylistFolder;
        CACHED_FOLDER_NAME = currentFolderName;
        CACHED_PLAYLIST_ID = currentPlaylistId;

        // 数据加载/恢复逻辑
        if (tab == Tab.MY_LIKE) {
            // 切到“我的喜欢”，必须重新加载，否则显示的是上次歌单的内容
            loadMyLikes();
        } else if (tab == Tab.PLAYLISTS) {
            if (inPlaylistFolder) {
                // 【核心修复】如果切回来时处于文件夹模式，必须重新加载该文件夹的歌曲
                // 因为刚才可能去“我的喜欢”或者“搜索”转了一圈，把 list 覆盖了
                if (currentPlaylistId != -1) {
                    // 避免不必要的加载：如果当前显示的已经是对的就不加载？
                    // 鉴于逻辑复杂，直接重载最稳妥
                    statusText = Component.literal("正在恢复: " + currentFolderName);
                    loadSongsByPlaylistId(currentPlaylistId);
                } else {
                    // 异常情况，回退到第一层
                    inPlaylistFolder = false;
                    loadAllUserPlaylists();
                }
            } else {
                // 处于第一层（歌单列表）
                if (CACHED_USER_PLAYLISTS == null || CACHED_USER_PLAYLISTS.isEmpty()) {
                    loadAllUserPlaylists();
                }
            }
        }
    }

    private void updateTabVisibility() {
        boolean isSearch = (currentTab == Tab.SEARCH);
        if (searchBox != null) {
            this.searchBox.visible = isSearch;
            this.searchBox.setEditable(isSearch);
            this.btnSearch.visible = isSearch;
        }

        // 返回按钮：仅在 歌单 Tab 且 进入文件夹时 显示
        if (btnBack != null) {
            boolean showBack = (currentTab == Tab.PLAYLISTS && inPlaylistFolder);
            this.btnBack.visible = showBack;
        }

        if (currentTab == Tab.PLAYLISTS && !inPlaylistFolder) {
            this.playlistList.visible = true;
            this.songList.visible = false;
        } else {
            this.playlistList.visible = false;
            this.songList.visible = true;
        }
        updatePageButtons();
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

    private void loadMyLikes() {
        Minecraft.getInstance().execute(() -> songList.refreshList(new ArrayList<>()));
        loadPlaylistByIndex(0, "我喜欢的音乐");
    }

    private void loadPlaylistByIndex(int index, String title) {
        statusText = Component.literal("正在获取: " + title + "...");
        new Thread(() -> {
            long uid = NeteaseApi.getMyUid();
            if (uid == 0) return;
            JsonArray pl = NeteaseApi.getUserPlaylists(uid);
            if (pl != null && pl.size() > index) {
                long fid = pl.get(index).getAsJsonObject().get("id").getAsLong();
                loadSongsByPlaylistId(fid);
            }
        }).start();
    }

    private void loadAllUserPlaylists() {
        statusText = Component.literal("正在获取歌单列表...");
        new Thread(() -> {
            long uid = NeteaseApi.getMyUid();
            if (uid == 0) return;
            JsonArray pl = NeteaseApi.getUserPlaylists(uid);
            if (pl != null) {
                List<PlaylistInfo> result = new ArrayList<>();
                for (int i = 0; i < pl.size(); i++) {
                    JsonObject obj = pl.get(i).getAsJsonObject();
                    long id = obj.get("id").getAsLong();
                    String name = obj.get("name").getAsString();
                    long count = obj.get("trackCount").getAsLong();
                    result.add(new PlaylistInfo(id, name, count));
                }
                CACHED_USER_PLAYLISTS = result;
                Minecraft.getInstance().execute(() -> {
                    playlistList.refreshList(result);
                    statusText = Component.literal("获取到 " + result.size() + " 个歌单");
                });
            }
        }).start();
    }

    private void openPlaylistFolder(PlaylistInfo info) {
        inPlaylistFolder = true;
        currentFolderName = info.name;
        currentPlaylistId = info.id; // 【新增】记录 ID

        // 更新缓存
        CACHED_IN_PLAYLIST_FOLDER = true;
        CACHED_FOLDER_NAME = info.name;
        CACHED_PLAYLIST_ID = info.id;

        updateTabVisibility();
        statusText = Component.literal("正在打开: " + info.name);
        new Thread(() -> loadSongsByPlaylistId(info.id)).start();
    }

    private void loadSongsByPlaylistId(long playlistId) {
        allSongIdsCache = NeteaseApi.getPlaylistSongIds(playlistId);
        CACHED_ALL_IDS = allSongIdsCache;
        currentPage = 0; CACHED_PAGE = 0;
        loadCurrentPageSongs();
    }

    private void changePage(int off) {
        currentPage += off; CACHED_PAGE = currentPage;
        loadCurrentPageSongs();
        updatePageButtons();
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
                songList.setScrollAmount(0);
                statusText = Component.literal("页码: " + (currentPage + 1) + " (歌单: " + (inPlaylistFolder ? currentFolderName : "列表") + ")");
                updatePageButtons();
            });
        }).start();
    }

    // === 渲染与输入 ===
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // 避免默认背景高斯模糊影响二维码扫码清晰度
        g.fill(0, 0, this.width, this.height, 0xAA000000);
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

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 禁用 Screen 默认背景渲染（包含模糊路径）
    }

    @Override
    public void renderTransparentBackground(GuiGraphics guiGraphics) {
        // 禁用 Screen 默认透明背景渲染（避免被其他调用触发模糊）
    }

    private void renderPlayerLayer(GuiGraphics g, int mx, int my, float pt) {
        renderTab(g, "搜 索", Tab.SEARCH, leftPos + 90, topPos + 8, mx, my);
        renderTab(g, "我的喜欢", Tab.MY_LIKE, leftPos + 140, topPos + 8, mx, my);
        renderTab(g, "我的歌单", Tab.PLAYLISTS, leftPos + 200, topPos + 8, mx, my);

        int activeX = 0, activeW = 0;
        if (currentTab == Tab.SEARCH) { activeX = leftPos + 90; activeW = 30; }
        else if (currentTab == Tab.MY_LIKE) { activeX = leftPos + 140; activeW = 45; }
        else if (currentTab == Tab.PLAYLISTS) { activeX = leftPos + 200; activeW = 45; }

        if (activeW > 0) g.fill(activeX - 2, topPos + 28, activeX + activeW + 2, topPos + 30, COLOR_ACCENT);

        if (currentTab == Tab.SEARCH && searchBox != null && searchBox.visible) {
            g.fill(searchBox.getX() - 2, searchBox.getY() - 2, searchBox.getX() + searchBox.getWidth() + 2, searchBox.getY() + searchBox.getHeight() + 2, 0xFF202020);
        }

        renderProgressBar(g);
        this.btnToggle.setMessage(Component.literal(ClientMusicManager.isPaused() ? "▶" : "||"));
        if (!statusText.getString().isEmpty()) {
            g.drawString(this.font, statusText, leftPos + 10, topPos + WINDOW_HEIGHT - 12, 0xFF666666, false);
        }

        if (this.songList.visible) this.songList.render(g, mx, my, pt);
        if (this.playlistList.visible) this.playlistList.render(g, mx, my, pt);

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
                if (mouseX >= leftPos + 90 && mouseX <= leftPos + 120) switchTab(Tab.SEARCH);
                if (mouseX >= leftPos + 140 && mouseX <= leftPos + 185) switchTab(Tab.MY_LIKE);
                if (mouseX >= leftPos + 200 && mouseX <= leftPos + 245) switchTab(Tab.PLAYLISTS);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // --- 内部类 ---

    class SongListWidget extends ObjectSelectionList<SongListWidget.SongEntry> {
        public boolean visible = true;
        public SongListWidget(Minecraft mc, int width, int height, int top) {
            super(mc, width, height, top, 20);
        }
        @Override public boolean mouseClicked(double mx, double my, int btn) {
            if (!this.visible) return false;
            return super.mouseClicked(mx, my, btn);
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            if (!this.visible) return false;
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (!this.visible) return false;
            return super.mouseDragged(mx, my, btn, dx, dy);
        }
        public void refreshList(List<SongInfo> songs) {
            this.clearEntries();
            for (SongInfo s : songs) this.addEntry(new SongEntry(s));
        }
        @Override protected int getScrollbarPosition() { return getX() + getRowWidth() + 6; }
        @Override public int getRowWidth() { return width - 10; }
        
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
                    if (now - lastClickTime < 500) playSong(song);
                    lastClickTime = now;
                    return true;
                }
                return false;
            }
            @Override public Component getNarration() { return Component.literal(song.name); }
        }
    }

    class PlaylistListWidget extends ObjectSelectionList<PlaylistListWidget.PlaylistEntry> {
        public boolean visible = false;
        public PlaylistListWidget(Minecraft mc, int width, int height, int top) {
            super(mc, width, height, top, 20);
        }
        @Override public boolean mouseClicked(double mx, double my, int btn) {
            if (!this.visible) return false;
            return super.mouseClicked(mx, my, btn);
        }
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            if (!this.visible) return false;
            return super.mouseScrolled(mx, my, dx, dy);
        }
        @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (!this.visible) return false;
            return super.mouseDragged(mx, my, btn, dx, dy);
        }
        public void refreshList(List<PlaylistInfo> playlists) {
            this.clearEntries();
            for (PlaylistInfo p : playlists) this.addEntry(new PlaylistEntry(p));
        }
        @Override protected int getScrollbarPosition() { return getX() + getRowWidth() + 6; }
        @Override public int getRowWidth() { return width - 10; }
        
        public class PlaylistEntry extends ObjectSelectionList.Entry<PlaylistEntry> {
            private final PlaylistInfo playlist;
            private long lastClickTime = 0;
            public PlaylistEntry(PlaylistInfo playlist) { this.playlist = playlist; }
            @Override
            public void render(GuiGraphics g, int idx, int top, int left, int w, int h, int mx, int my, boolean hover, float pt) {
                if (hover) g.fill(left, top, left + w, top + h, COLOR_HOVER);
                g.drawString(font, "📁 " + playlist.name, left + 4, top + 8, 0xFFFFEEAA, false);
                String count = playlist.trackCount + "首";
                g.drawString(font, count, left + w - font.width(count) - 4, top + 8, 0xFF888888, false);
            }
            @Override
            public boolean mouseClicked(double mx, double my, int btn) {
                if (btn == 0) {
                    PlaylistListWidget.this.setSelected(this);
                    long now = System.currentTimeMillis();
                    if (now - lastClickTime < 500) openPlaylistFolder(playlist);
                    lastClickTime = now;
                    return true;
                }
                return false;
            }
            @Override public Component getNarration() { return Component.literal(playlist.name); }
        }
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
                        Minecraft.getInstance().execute(() -> BotConfig.saveClientCookie(result.cookie));
                        NeteaseApi.loadCookies();
                        Minecraft.getInstance().execute(() -> {
                            currentState = ScreenState.PLAYER;
                            CACHED_ALL_IDS = null; CACHED_CURRENT_LIST = null;
                            loadMyLikes();
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
}
