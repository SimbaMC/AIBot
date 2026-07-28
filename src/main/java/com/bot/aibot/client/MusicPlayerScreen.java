package com.bot.aibot.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.opengl.GL11;

import com.bot.aibot.API.QrCode;
import com.bot.aibot.network.PacketHandler;
import com.bot.aibot.network.packet.C2SReportMusicPacket;
import com.bot.aibot.utils.NeteaseApi;
import com.bot.aibot.utils.SongInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class MusicPlayerScreen extends GuiScreen {

    private static final ThreadPoolExecutor API_EXECUTOR = new ThreadPoolExecutor(
        1,
        2,
        30,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(16),
        new ThreadFactory() {

            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "AiBot-Netease");
                thread.setDaemon(true);
                return thread;
            }
        },
        new ThreadPoolExecutor.AbortPolicy());

    private enum Tab {
        SEARCH,
        LIKES,
        PLAYLISTS
    }

    private enum Mode {
        LIST,
        SINGLE,
        RANDOM
    }

    private static List<SongInfo> songs = new ArrayList<SongInfo>();
    private static List<Playlist> playlists = new ArrayList<Playlist>();
    private static SongInfo current;
    private static Mode mode = Mode.LIST;
    private static boolean global;
    private static long lastBroadcast;
    private Tab tab = Tab.SEARCH;
    private GuiTextField search;
    private MusicSlot slot;
    private boolean playlistFolders, loggedIn;
    private volatile boolean qrLoading;
    private volatile long loginGeneration;
    private List<Long> allIds = new ArrayList<Long>();
    private int page;
    private String status = "";
    private QrCode qr;
    private int panelLeft, panelTop, panelRight, panelBottom;
    private int listLeft, listTop, listRight, listBottom, statusY;

    public void initGui() {
        String previousSearch = search == null ? "" : search.getText();
        buttonList.clear();
        int panelWidth = Math.max(1, Math.min(320, width - 8));
        int panelHeight = Math.max(1, Math.min(230, height - 8));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        panelRight = panelLeft + panelWidth;
        panelBottom = panelTop + panelHeight;
        int padding = panelWidth < 290 ? 6 : 10;
        int innerLeft = panelLeft + padding;
        int innerRight = panelRight - padding;

        int tabY = panelTop + 20;
        addAt(1, innerLeft, tabY, 48, "搜索");
        addAt(2, innerLeft + 54, tabY, 58, "我的喜欢");
        addAt(3, innerLeft + 118, tabY, 58, "我的歌单");

        int searchY = panelTop + 45;
        int loginWidth = 48, goWidth = 42, rowGap = 5;
        int loginX = innerRight - loginWidth;
        int goX = loginX - rowGap - goWidth;
        int searchWidth = Math.max(40, goX - rowGap - innerLeft);
        search = new GuiTextField(fontRendererObj, innerLeft, searchY, searchWidth, 18);
        search.setMaxStringLength(80);
        search.setText(previousSearch);
        search.setFocused(tab == Tab.SEARCH);
        addAt(4, goX, searchY - 1, goWidth, "GO");
        addAt(5, loginX, searchY - 1, loginWidth, "登录");

        int controlsY = panelBottom - 24;
        statusY = controlsY - 13;
        listLeft = innerLeft;
        listRight = innerRight;
        listTop = panelTop + 69;
        listBottom = statusY - 4;
        slot = new MusicSlot(mc, width, height, listTop, listBottom);

        int[] controlWidths = { 24, 26, 24, 24, 44, 52, 24, 24 };
        int[] controlIds = { 10, 11, 12, 13, 14, 15, 16, 17 };
        String[] controlLabels = { "|<", "||", ">|", "■", modeName(), global ? "全服" : "私享", "<", ">" };
        int controlGap = panelWidth < 280 ? 2 : 4;
        int controlsWidth = controlGap * (controlWidths.length - 1);
        for (int controlWidth : controlWidths) controlsWidth += controlWidth;
        int controlX = panelLeft + (panelWidth - controlsWidth) / 2;
        for (int i = 0; i < controlWidths.length; i++) {
            addAt(controlIds[i], controlX, controlsY, controlWidths[i], controlLabels[i]);
            controlX += controlWidths[i] + controlGap;
        }
        NeteaseApi.loadCookies();
        async(new Runnable() {

            public void run() {
                final boolean ok = NeteaseApi.getMyUid() > 0;
                ui(new Runnable() {

                    public void run() {
                        loggedIn = ok;
                        if (ok && qrLoading) {
                            loginGeneration++;
                            qrLoading = false;
                            qr = null;
                        }
                        status = ok ? "已登录" : "请扫码登录";
                        setLoginButtonText();
                    }
                });
            }
        });
        ClientMusicManager.onTrackFinishedCallback = new Runnable() {

            public void run() {
                switchSong(true, true);
            }
        };
    }

    private void addAt(int id, int x, int y, int w, String text) {
        buttonList.add(new GuiButton(id, x, y, w, 20, text));
    }

    protected void actionPerformed(GuiButton b) {
        if (b.id == 1) {
            tab = Tab.SEARCH;
            playlistFolders = false;
            search.setFocused(true);
        } else if (b.id == 2) {
            tab = Tab.LIKES;
            search.setFocused(false);
            loadLikes();
        } else if (b.id == 3) {
            tab = Tab.PLAYLISTS;
            search.setFocused(false);
            loadPlaylists();
        } else if (b.id == 4) doSearch();
        else if (b.id == 5) startLogin();
        else if (b.id == 10) switchSong(false, false);
        else if (b.id == 11) ClientMusicManager.togglePause();
        else if (b.id == 12) switchSong(true, false);
        else if (b.id == 13) ClientMusicManager.stop();
        else if (b.id == 14) {
            mode = Mode.values()[(mode.ordinal() + 1) % 3];
            b.displayString = modeName();
        } else if (b.id == 15) {
            global = !global;
            b.displayString = global ? "全服" : "私享";
        } else if (b.id == 16 && page > 0) {
            page--;
            loadPage();
        } else if (b.id == 17 && (page + 1) * 50 < allIds.size()) {
            page++;
            loadPage();
        }
    }

    private String modeName() {
        return mode == Mode.LIST ? "列表" : mode == Mode.SINGLE ? "单曲" : "随机";
    }

    private void doSearch() {
        final String q = search.getText()
            .trim();
        if (q.length() == 0) return;
        status = "搜索中...";
        async(new Runnable() {

            public void run() {
                setSongs(NeteaseApi.searchList(q), "搜索结果");
            }
        });
    }

    private void loadLikes() {
        status = "加载喜欢...";
        async(new Runnable() {

            public void run() {
                long uid = NeteaseApi.getMyUid();
                JsonArray p = NeteaseApi.getUserPlaylists(uid);
                if (p != null && p.size() > 0) loadIds(
                    p.get(0)
                        .getAsJsonObject()
                        .get("id")
                        .getAsLong());
            }
        });
    }

    private void loadPlaylists() {
        status = "加载歌单...";
        async(new Runnable() {

            public void run() {
                JsonArray p = NeteaseApi.getUserPlaylists(NeteaseApi.getMyUid());
                final List<Playlist> r = new ArrayList<Playlist>();
                if (p != null) for (int i = 0; i < p.size(); i++) {
                    JsonObject o = p.get(i)
                        .getAsJsonObject();
                    r.add(
                        new Playlist(
                            o.get("id")
                                .getAsLong(),
                            o.get("name")
                                .getAsString(),
                            o.get("trackCount")
                                .getAsLong()));
                }
                ui(new Runnable() {

                    public void run() {
                        playlists = r;
                        playlistFolders = true;
                        status = "歌单 " + r.size();
                    }
                });
            }
        });
    }

    private void openPlaylist(final Playlist p) {
        playlistFolders = false;
        status = "打开 " + p.name;
        async(new Runnable() {

            public void run() {
                loadIds(p.id);
            }
        });
    }

    private void loadIds(long id) {
        allIds = NeteaseApi.getPlaylistSongIds(id);
        page = 0;
        loadPage();
    }

    private void loadPage() {
        final int a = page * 50, b = Math.min(a + 50, allIds.size());
        if (a >= b) {
            setSongs(new ArrayList<SongInfo>(), "空歌单");
            return;
        }
        final List<Long> ids = new ArrayList<Long>(allIds.subList(a, b));
        async(new Runnable() {

            public void run() {
                setSongs(NeteaseApi.getSongsDetail(ids), "第 " + (page + 1) + " 页");
            }
        });
    }

    private void setSongs(final List<SongInfo> r, final String s) {
        ui(new Runnable() {

            public void run() {
                songs = r;
                playlistFolders = false;
                status = s + " (" + r.size() + ")";
            }
        });
    }

    private void play(final SongInfo song) {
        current = song;
        final boolean broadcast = global;
        ClientMusicManager.onTrackFinishedCallback = broadcast ? null : new Runnable() {

            public void run() {
                switchSong(true, true);
            }
        };
        global = false;
        for (Object o : buttonList) if (((GuiButton) o).id == 15) ((GuiButton) o).displayString = "私享";
        status = "解析播放地址...";
        async(new Runnable() {

            public void run() {
                final String url = NeteaseApi.getSongUrl(song.id);
                ui(new Runnable() {

                    public void run() {
                        if (url == null) {
                            status = "VIP/无版权";
                            return;
                        }
                        if (broadcast) lastBroadcast = System.currentTimeMillis();
                        PacketHandler.sendToServer(
                            new C2SReportMusicPacket(url, song.name + " - " + song.artist, song.duration, broadcast));
                        status = "等待服务器验证播放地址...";
                    }
                });
            }
        });
    }

    private void switchSong(boolean next, boolean auto) {
        if (songs.isEmpty() || (auto && global)) return;
        SongInfo target = current;
        if (mode == Mode.RANDOM) target = songs.get(new Random().nextInt(songs.size()));
        else if (!(auto && mode == Mode.SINGLE)) {
            int i = current == null ? -1 : songs.indexOf(current);
            target = songs.get(next ? (i + 1) % songs.size() : (i - 1 + songs.size()) % songs.size());
        }
        if (target != null) play(target);
    }

    private void startLogin() {
        if (qrLoading || loggedIn) return;
        qrLoading = true;
        final long login = ++loginGeneration;
        status = "获取二维码...";
        setLoginButtonText();
        async(new Runnable() {

            public void run() {
                final String key = NeteaseApi.getLoginKey();
                if (key == null) {
                    ui(new Runnable() {

                        public void run() {
                            if (login != loginGeneration) return;
                            status = "二维码接口失败";
                            qrLoading = false;
                            setLoginButtonText();
                        }
                    });
                    return;
                }
                try {
                    final QrCode code = QrCode.encodeText(NeteaseApi.getLoginQrUrl(key), QrCode.Ecc.LOW);
                    ui(new Runnable() {

                        public void run() {
                            if (!isLoginCurrent(login)) return;
                            qr = code;
                            status = "请使用网易云 APP 扫码";
                        }
                    });
                } catch (Exception e) {
                    ui(new Runnable() {

                        public void run() {
                            status = "二维码生成失败";
                            qrLoading = false;
                            setLoginButtonText();
                        }
                    });
                    return;
                }
                while (isLoginCurrent(login)) {
                    final NeteaseApi.LoginResult r = NeteaseApi.checkLoginStatus(key);
                    ui(new Runnable() {

                        public void run() {
                            if (!isLoginCurrent(login)) return;
                            status = loginStatus(r);
                            if (r.code == 803) {
                                loggedIn = true;
                                qrLoading = false;
                                qr = null;
                                setLoginButtonText();
                            } else if (r.code == 800) {
                                qrLoading = false;
                                setLoginButtonText();
                            }
                        }
                    });
                    if (r.code == 803) {
                        break;
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });
    }

    private String loginStatus(NeteaseApi.LoginResult result) {
        if (result.code == 800) return "二维码已过期，请重新登录";
        if (result.code == 801) return "等待扫码...";
        if (result.code == 802) return "请在手机确认";
        if (result.code == 803) return "登录成功";
        return result.message == null || result.message.length() == 0 ? "登录接口异常，正在重试..." : result.message + "，正在重试...";
    }

    private void setLoginButtonText() {
        for (Object button : buttonList) {
            GuiButton guiButton = (GuiButton) button;
            if (guiButton.id == 5) {
                guiButton.displayString = loggedIn ? "已登录" : "登录";
                guiButton.enabled = !loggedIn && !qrLoading;
                return;
            }
        }
    }

    public void onGuiClosed() {
        loginGeneration++;
        qrLoading = false;
        qr = null;
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    static void showPlaybackStarted(boolean broadcast) {
        if (Minecraft.getMinecraft().currentScreen instanceof MusicPlayerScreen)
            ((MusicPlayerScreen) Minecraft.getMinecraft().currentScreen).status = broadcast ? "正在全服播放" : "正在播放";
    }

    static void showPlaybackRejected(String message) {
        if (Minecraft.getMinecraft().currentScreen instanceof MusicPlayerScreen)
            ((MusicPlayerScreen) Minecraft.getMinecraft().currentScreen).status = message;
    }

    private boolean isLoginCurrent(long login) {
        return qrLoading && login == loginGeneration;
    }

    protected void keyTyped(char c, int key) {
        if (key == 1) {
            mc.displayGuiScreen(null);
            return;
        }
        if (search.textboxKeyTyped(c, key)) return;
        super.keyTyped(c, key);
    }

    protected void mouseClicked(int x, int y, int b) {
        super.mouseClicked(x, y, b);
        search.mouseClicked(x, y, b);
    }

    public void handleMouseInput() {
        super.handleMouseInput();
        if (slot != null) {
            int wheel = org.lwjgl.input.Mouse.getEventDWheel();
            if (wheel != 0) slot.scrollBy(wheel > 0 ? -20 : 20);
        }
    }

    public void drawScreen(int x, int y, float pt) {
        drawDefaultBackground();
        drawRect(panelLeft, panelTop, panelRight, panelBottom, 0xdd101010);
        if (qr != null) drawQr();
        else drawMusicList(x, y, pt);
        drawCenteredString(fontRendererObj, "AiBot 网易云音乐", width / 2, panelTop + 5, 0xffffff);
        if (tab == Tab.SEARCH && qr == null) search.drawTextBox();
        drawString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(status, Math.max(20, listRight - listLeft - 90)),
            listLeft,
            statusY,
            0xaaaaaa);
        if (ClientMusicManager.isPlaying()) {
            String progress = format(ClientMusicManager.getProgress()) + " / "
                + format(ClientMusicManager.currentDuration);
            drawString(
                fontRendererObj,
                progress,
                listRight - fontRendererObj.getStringWidth(progress),
                statusY,
                0x55ff88);
        }
        super.drawScreen(x, y, pt);
    }

    private void drawMusicList(int x, int y, float pt) {
        ScaledResolution scaled = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int factor = scaled.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
            listLeft * factor,
            mc.displayHeight - listBottom * factor,
            Math.max(1, listRight - listLeft) * factor,
            Math.max(1, listBottom - listTop) * factor);
        try {
            slot.drawScreen(x, y, pt);
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private void drawQr() {
        int availableWidth = Math.max(1, listRight - listLeft - 10);
        int availableHeight = Math.max(1, listBottom - listTop - 10);
        int scale = Math.max(1, Math.min(4, Math.min(availableWidth / qr.size, availableHeight / qr.size)));
        int sx = width / 2 - qr.size * scale / 2;
        int sy = listTop + (listBottom - listTop - qr.size * scale) / 2;
        drawRect(sx - 5, sy - 5, sx + qr.size * scale + 5, sy + qr.size * scale + 5, 0xffffffff);
        for (int y = 0; y < qr.size; y++) for (int x = 0; x < qr.size; x++) if (qr.getModule(x, y))
            drawRect(sx + x * scale, sy + y * scale, sx + (x + 1) * scale, sy + (y + 1) * scale, 0xff000000);
    }

    private static String format(long m) {
        long s = m / 1000;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    public static void resetBroadcastCooldown() {
        lastBroadcast = 0;
    }

    private static void async(Runnable r) {
        try {
            API_EXECUTOR.execute(r);
        } catch (RuntimeException e) {
            ui(new Runnable() {

                public void run() {
                    if (Minecraft.getMinecraft().currentScreen instanceof MusicPlayerScreen)
                        ((MusicPlayerScreen) Minecraft.getMinecraft().currentScreen).status = "请求过多，请稍后重试";
                }
            });
        }
    }

    private static void ui(Runnable r) {
        Minecraft.getMinecraft()
            .func_152344_a(r);
    }

    private static final class Playlist {

        final long id, count;
        final String name;

        Playlist(long i, String n, long c) {
            id = i;
            name = n;
            count = c;
        }
    }

    private final class MusicSlot extends GuiSlot {

        MusicSlot(Minecraft m, int screenWidth, int screenHeight, int t, int b) {
            super(m, screenWidth, screenHeight, t, b, 20);
        }

        protected int getSize() {
            return playlistFolders ? playlists.size() : songs.size();
        }

        protected void elementClicked(int i, boolean dbl, int x, int y) {
            if (playlistFolders) openPlaylist(playlists.get(i));
            else if (dbl) play(songs.get(i));
        }

        protected boolean isSelected(int i) {
            return !playlistFolders && current == songs.get(i);
        }

        protected void drawBackground() {}

        protected void drawSlot(int i, int x, int y, int h, net.minecraft.client.renderer.Tessellator t, int mx,
            int my) {
            String a;
            if (playlistFolders) {
                Playlist p = playlists.get(i);
                a = p.name + "  (" + p.count + ")";
            } else {
                SongInfo s = songs.get(i);
                a = s.name + " - " + s.artist;
            }
            fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(a, Math.max(20, getListWidth() - 20)),
                x + 5,
                y + 5,
                0xdddddd);
        }

        public int getListWidth() {
            return Math.max(20, listRight - listLeft);
        }

        protected int getScrollBarX() {
            return listRight - 6;
        }
    }
}
