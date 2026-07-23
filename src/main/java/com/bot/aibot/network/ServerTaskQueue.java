package com.bot.aibot.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.bot.aibot.BottyMod;

public final class ServerTaskQueue {

    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<Runnable>();

    private ServerTaskQueue() {}

    public static void submit(Runnable task) {
        TASKS.add(task);
    }

    public static void runPending() {
        Runnable task;
        while ((task = TASKS.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                BottyMod.LOG.error(">>> [Bot] 主线程任务执行异常", e);
            }
        }
    }
}
