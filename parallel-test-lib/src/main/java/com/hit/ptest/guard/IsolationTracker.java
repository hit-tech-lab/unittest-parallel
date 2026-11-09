package com.hit.ptest.guard;

import java.util.concurrent.atomic.AtomicInteger;

/** Lớp JUnit {@code @Isolated} chạy một mình: trong lúc đó SQL cả bảng không ảnh hưởng test khác. */
public final class IsolationTracker {

    private static final AtomicInteger RUNNING = new AtomicInteger();

    private IsolationTracker() {
    }

    public static void started() {
        RUNNING.incrementAndGet();
    }

    public static void finished() {
        RUNNING.decrementAndGet();
    }

    static boolean isolatedClassRunning() {
        return RUNNING.get() > 0;
    }
}
