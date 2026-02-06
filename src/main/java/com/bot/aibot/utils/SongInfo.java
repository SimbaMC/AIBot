package com.bot.aibot.utils;

public class SongInfo {
    public String id;
    public String name;
    public String artist;
    public long duration; // 歌曲时长 (毫秒)
    public boolean isVip;

    public SongInfo(String id, String name, String artist, long duration) {
        this.id = id;
        this.name = name;
        this.artist = artist;
        this.duration = duration;
    }
}