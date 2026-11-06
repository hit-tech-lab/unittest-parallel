package com.hittech.paralleltest.guard;

import java.util.concurrent.atomic.AtomicInteger;

/** JUnit @Isolated classes run alone: while one runs, whole-table SQL is harmless. */
final class IsolationTracker {

    private static final AtomicInteger RUNNING = new AtomicInteger();

    private IsolationTracker() {
    }

    static void started() {
        RUNNING.incrementAndGet();
    }

    static void finished() {
        RUNNING.decrementAndGet();
    }

    static boolean isolatedClassRunning() {
        return RUNNING.get() > 0;
    }
}
