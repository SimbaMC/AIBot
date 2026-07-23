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
    private boolean playlistFolders, loggedIn, qrLoading;
    private long loginGeneration;
    private List<Long> allIds = new ArrayList<Long>();
    private int page;
    private String status = "";
    private QrCode qr;

    public void initGui() {
        buttonList.clear();
        search = new GuiTextField(fontRendererObj, width / 2 - 150, height / 2 - 82, 190, 18);
        search.setMaxStringLength(80);
        slot = new MusicSlot(mc, width / 2 - 155, width / 2 + 155, height / 2 - 55, height / 2 + 65);
        add(1, -150, -110, 48, "搜索");
        add(2, -95, -110, 58, "我的喜欢");
        add(3, -30, -110, 58, "我的歌单");
        add(4, 50, -82, 45, "GO");
        add(5, 102, -82, 45, "登录");
        add(10, -150, 78, 25, "|<");
        add(11, -120, 78, 28, "||");
        add(12, -87, 78, 25, ">|");
        add(13, -57, 78, 25, "■");
        add(14, -25, 78, 48, modeName());
        add(15, 28, 78, 58, global ? "全服" : "私享");
        add(16, 92, 78, 25, "<");
        add(17, 122, 78, 25, ">");
        NeteaseApi.loadCookies();
        async(new Runnable() {

            public void run() {
                final boolean ok = NeteaseApi.getMyUid() > 0;
                ui(new Runnable() {

                    public void run() {
                        loggedIn = ok;
                        status = ok ? "已登录" : "请扫码登录";
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

    private void add(int id, int x, int y, int w, String text) {
        buttonList.add(new GuiButton(id, width / 2 + x, height / 2 + y, w, 20, text));
    }

    protected void actionPerformed(GuiButton b) {
        if (b.id == 1) {
            tab = Tab.SEARCH;
            playlistFolders = false;
        } else if (b.id == 2) {
            tab = Tab.LIKES;
            loadLikes();
        } else if (b.id == 3) {
            tab = Tab.PLAYLISTS;
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
        if (qrLoading) return;
        qrLoading = true;
        final long login = ++loginGeneration;
        status = "获取二维码...";
        async(new Runnable() {

            public void run() {
                final String key = NeteaseApi.getLoginKey();
                if (key == null) {
                    ui(new Runnable() {

                        public void run() {
                            status = "二维码接口失败";
                            qrLoading = false;
                        }
                    });
                    return;
                }
                try {
                    final QrCode code = QrCode.encodeText(NeteaseApi.getLoginQrUrl(key), QrCode.Ecc.LOW);
                    ui(new Runnable() {

                        public void run() {
                            qr = code;
                        }
                    });
                } catch (Exception e) {
                    ui(new Runnable() {

                        public void run() {
                            status = "二维码生成失败";
                            qrLoading = false;
                        }
                    });
                    return;
                }
                while (isLoginCurrent(login)) {
                    final NeteaseApi.LoginResult r = NeteaseApi.checkLoginStatus(key);
                    ui(new Runnable() {

                        public void run() {
                            if (!isLoginCurrent(login)) return;
                            status = r.code == 802 ? "请在手机确认" : r.code == 803 ? "登录成功" : "等待扫码...";
                            if (r.code == 803) {
                                loggedIn = true;
                                qrLoading = false;
                                qr = null;
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

    public void onGuiClosed() {
        loginGeneration++;
        qrLoading = false;
        qr = null;
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
        drawRect(width / 2 - 160, height / 2 - 120, width / 2 + 160, height / 2 + 110, 0xdd101010);
        drawCenteredString(fontRendererObj, "AiBot 网易云音乐", width / 2, height / 2 - 115, 0xffffff);
        if (tab == Tab.SEARCH) search.drawTextBox();
        if (qr != null) drawQr();
        else slot.drawScreen(x, y, pt);
        drawString(fontRendererObj, status, width / 2 - 150, height / 2 + 68, 0xaaaaaa);
        if (ClientMusicManager.isPlaying()) drawString(
            fontRendererObj,
            format(ClientMusicManager.getProgress()) + " / " + format(ClientMusicManager.currentDuration),
            width / 2 + 45,
            height / 2 + 68,
            0x55ff88);
        super.drawScreen(x, y, pt);
    }

    private void drawQr() {
        int scale = Math.max(2, Math.min(4, 100 / qr.size)), sx = width / 2 - qr.size * scale / 2, sy = height / 2 - 55;
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

        MusicSlot(Minecraft m, int l, int r, int t, int b) {
            super(m, r - l, b - t, t, b, 20);
            this.left = l;
            this.right = r;
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
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(a, 285), x + 5, y + 5, 0xdddddd);
        }

        public int getListWidth() {
            return 310;
        }

        protected int getScrollBarX() {
            return width / 2 + 145;
        }
    }
}
