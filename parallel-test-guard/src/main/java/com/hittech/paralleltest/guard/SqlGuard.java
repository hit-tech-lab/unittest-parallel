package com.hittech.paralleltest.guard;

import com.hittech.paralleltest.core.TestBindings;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Rejects whole-table statements while tests run in parallel (rule PT12). */
public final class SqlGuard {

    private static final Pattern WHERE = Pattern.compile("\\bwhere\\b");
    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private SqlGuard() {
    }

    public static boolean isForbidden(String sql) {
        String cleaned = BLOCK_COMMENT.matcher(LINE_COMMENT.matcher(sql).replaceAll(" ")).replaceAll(" ");
        for (String statement : cleaned.split(";")) {
            String s = statement.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (s.startsWith("truncate ") || (s.startsWith("delete ") && !WHERE.matcher(s).find())) {
                return true;
            }
        }
        return false;
    }

    /** Called for every SQL string reaching JDBC after the context has started. */
    public static void check(String sql) {
        if (sql == null || IsolationTracker.isolatedClassRunning() || !GuardSettings.get().enabled()
                || !GuardSettings.get().sqlGuard() || !isForbidden(sql)) {
            return;
        }
        Violation v = new Violation(Rule.PT12, "thread '" + Thread.currentThread().getName() + "'",
                "SQL: " + sql.strip());
        ParallelGuardViolation error = new ParallelGuardViolation("Whole-table SQL during parallel tests", List.of(v));
        TestBindings.reportFromAnyThread(error); // the owning test fails with this message even if the app swallowed it
        throw error;
    }
}
