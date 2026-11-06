package com.hittech.paralleltest.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One binding per running Spring test method: identifies the test on its own thread and on every thread
 * the binding is carried to (HTTP header, wrapped tasks). Holds the test's per-test mocks and collects
 * guard violations raised on OTHER threads (server, executors), which the guard re-throws in the test
 * itself so they are never lost behind an HTTP 500 or a swallowed exception.
 */
public final class TestBindings {

    public static final class Binding {
        private final String id = UUID.randomUUID().toString();
        private final String testName;
        private final Thread owner = Thread.currentThread();
        private final Map<Class<?>, Object> mocks;
        private final ConcurrentLinkedQueue<Throwable> foreignViolations = new ConcurrentLinkedQueue<>();
        private volatile boolean open = true;

        private Binding(String testName, Map<Class<?>, Object> mocks) {
            this.testName = testName;
            this.mocks = Map.copyOf(mocks);
        }

        public String id() {
            return id;
        }

        public String testName() {
            return testName;
        }

        public Object mockFor(Class<?> type) {
            return mocks.get(type);
        }

        public boolean isOpen() {
            return open;
        }
    }

    private static final ThreadLocal<Binding> CURRENT = new ThreadLocal<>();
    private static final Map<String, Binding> BY_ID = new ConcurrentHashMap<>();
    private static final AtomicInteger UNATTRIBUTED = new AtomicInteger();

    private TestBindings() {
    }

    public static Binding open(String testName, Map<Class<?>, Object> mocks) {
        Binding b = new Binding(testName, mocks);
        BY_ID.put(b.id, b);
        CURRENT.set(b);
        return b;
    }

    /** Closes the binding and returns the violations raised for this test on other threads. */
    public static List<Throwable> close(Binding b) {
        b.open = false;
        BY_ID.remove(b.id);
        if (CURRENT.get() == b) {
            CURRENT.remove();
        }
        return new ArrayList<>(b.foreignViolations);
    }

    public static Binding current() {
        return CURRENT.get();
    }

    public static Binding byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** Runs {@code action} with {@code binding} attached to the current thread, restoring the previous one. */
    public static <T> T callWith(Binding binding, Callable<T> action) throws Exception {
        if (!binding.open) {
            throw new IllegalStateException("[parallel-test-guard] test '" + binding.testName
                    + "' has already finished: a task carrying its binding outlived it (await your async work in the test)");
        }
        Binding previous = CURRENT.get();
        CURRENT.set(binding);
        try {
            return action.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** Remember a violation raised outside the test thread so the test itself fails with it. */
    public static void reportFromAnyThread(Throwable violation) {
        Binding b = CURRENT.get();
        if (b == null) {
            UNATTRIBUTED.incrementAndGet();
            System.err.println("[parallel-test-guard] violation on thread '" + Thread.currentThread().getName()
                    + "' that belongs to no test:\n" + violation.getMessage());
        } else if (b.owner != Thread.currentThread()) {
            b.foreignViolations.add(violation);
        }
    }

    public static int unattributedViolations() {
        return UNATTRIBUTED.get();
    }
}
