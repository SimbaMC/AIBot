package com.bot.aibot.client;

import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

public class AnimatedTexture {
    public final List<Frame> frames = new ArrayList<>();
    public int totalDuration = 0;

    public void addFrame(ResourceLocation tex, int duration) {
        frames.add(new Frame(tex, duration));
        totalDuration += duration;
    }

    public ResourceLocation getCurrentFrame() {
        if (frames.isEmpty()) return null;
        if (frames.size() == 1 || totalDuration <= 0) return frames.get(0).tex;

        long now = System.currentTimeMillis();
        long cycleTime = now % totalDuration;

        int currentTimer = 0;
        for (Frame frame : frames) {
            currentTimer += frame.duration;
            if (cycleTime < currentTimer) {
                return frame.tex;
            }
        }
        return frames.get(0).tex;
    }

    public static class Frame {
        public final ResourceLocation tex;
        public final int duration;

        public Frame(ResourceLocation tex, int duration) {
            this.tex = tex;
            this.duration = duration;
        }
    }
}