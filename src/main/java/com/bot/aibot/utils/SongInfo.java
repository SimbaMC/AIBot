package com.bot.aibot.utils;

public class SongInfo {
    public String id;
    public String name;
    public String artist;
    public boolean isVip; // 可选：标记是否是 VIP 歌

    public SongInfo(String id, String name, String artist) {
        this.id = id;
        this.name = name;
        this.artist = artist;
    }
}