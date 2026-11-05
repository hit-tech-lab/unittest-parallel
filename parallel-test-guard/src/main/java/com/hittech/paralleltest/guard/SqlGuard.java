package com.hittech.paralleltest.guard;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/** Rejects whole-table statements while tests run in parallel (rule PT12). */
public final class SqlGuard {

    private static final Pattern WHERE = Pattern.compile("\\bwhere\\b");
    private static final AtomicInteger ISOLATED_CLASSES_RUNNING = new AtomicInteger();

    private SqlGuard() {
    }

    /** JUnit @Isolated classes run alone, so whole-table statements are safe while one of them runs. */
    static void isolatedClassStarted() {
        ISOLATED_CLASSES_RUNNING.incrementAndGet();
    }

    static void isolatedClassFinished() {
        ISOLATED_CLASSES_RUNNING.decrementAndGet();
    }

    public static boolean isForbidden(String sql) {
        String s = sql.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (s.startsWith("truncate ")) {
            return true;
        }
        return s.startsWith("delete ") && !WHERE.matcher(s).find();
    }

    public static void check(String sql) {
        if (ISOLATED_CLASSES_RUNNING.get() == 0 && isForbidden(sql)) {
            throw new ParallelGuardViolation("Whole-table SQL during parallel tests", List.of(new Violation(
                    Rule.PT12, "thread '" + Thread.currentThread().getName() + "'", "SQL: " + sql.strip())));
        }
    }
}
