package io.hit.ptest.guard;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chặn SQL cả bảng / đổi cấu trúc khi test chạy song song (luật PT12): {@code DELETE} và {@code UPDATE} không có
 * {@code WHERE} (kể cả trong câu {@code WITH ...}), {@code MERGE ... WHEN MATCHED THEN DELETE} không điều kiện, {@code TRUNCATE}, {@code DROP}. Chỉ phân tích văn bản SQL (bỏ comment và literal chuỗi, tách theo
 * {@code ;}); không hiểu mệnh đề đặc biệt như {@code WHERE 1=1} (coi là có WHERE).
 */
public final class SqlGuard {

    private static final Logger LOG = LoggerFactory.getLogger(SqlGuard.class);
    /** DML nằm trong câu {@code WITH}: bản thân CTE có thể là {@code DELETE ... RETURNING} (Postgres) hoặc câu chính là DELETE/UPDATE. */
    private static final Pattern CTE_DML = Pattern.compile("\\b(delete from|update (?:only )?[\\w.\"]+(?: (?:as )?[\\w\"]+)? set)\\b");
    /** {@code WHEN [NOT] MATCHED [BY SOURCE] THEN DELETE} không có {@code AND <điều kiện>}: xóa mọi dòng khớp mệnh đề ON. */
    private static final Pattern MERGE_DELETE_ALL = Pattern.compile("\\bwhen (?:not )?matched(?: by (?:source|target))? then delete\\b");
    private static final Pattern WHERE = Pattern.compile("\\bwhere\\b");
    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");

    private static final List<String> IGNORED_FRAMES = List.of("java.", "jdk.", "sun.", "org.springframework.", "org.hibernate.",
            "com.zaxxer.", "org.h2.", "org.postgresql.", "org.junit.", "io.hit.ptest.", "org.apache.", "org.mockito.");

    private SqlGuard() {
    }

    /** Lý do cấm ({@code "DELETE without WHERE"}...) hoặc null nếu câu lệnh được phép. */
    public static String forbiddenReason(String sql) {
        String cleaned = STRING_LITERAL.matcher(BLOCK_COMMENT.matcher(LINE_COMMENT.matcher(sql).replaceAll(" ")).replaceAll(" "))
                .replaceAll("''");
        for (String statement : cleaned.split(";")) {
            String s = statement.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (s.startsWith("truncate ")) {
                return "TRUNCATE";
            }
            if (s.startsWith("drop ")) {
                return "DROP";
            }
            if (s.startsWith("delete ") && !WHERE.matcher(s).find()) {
                return "DELETE without WHERE";
            }
            if (s.startsWith("update ") && !WHERE.matcher(s).find()) {
                return "UPDATE without WHERE";
            }
            if (s.startsWith("with ")) {
                String cte = dmlWithoutWhere(s);
                if (cte != null) {
                    return cte;
                }
            }
            if (s.startsWith("merge ") && MERGE_DELETE_ALL.matcher(s).find()) {
                return "MERGE ... DELETE without condition";
            }
        }
        return null;
    }

    /**
     * Với câu {@code WITH}: mỗi {@code DELETE FROM}/{@code UPDATE ... SET} (ở câu chính hoặc trong thân CTE) phải có {@code WHERE} trong
     * phạm vi của chính nó (đến dấu ngoặc đóng của nhóm chứa nó, hoặc hết câu). Trả về lý do hoặc null.
     */
    private static String dmlWithoutWhere(String s) {
        var m = CTE_DML.matcher(s);
        while (m.find()) {
            int depth = 0;
            int end = s.length();
            for (int i = m.start(); i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')' && --depth < 0) {
                    end = i;
                    break;
                }
            }
            if (!WHERE.matcher(s.substring(m.start(), end)).find()) {
                return m.group(1).startsWith("delete") ? "DELETE without WHERE" : "UPDATE without WHERE";
            }
        }
        return null;
    }

    /** Vị trí: test sở hữu (nếu biết), thread, và khung stack đầu tiên thuộc code của người dùng (nếu có trên thread này). */
    private static String location() {
        String test = GuardScope.currentLabel();
        String site = StackWalker.getInstance().walk(frames -> frames
                .filter(f -> !ignoredFrame(f.getClassName())).findFirst()
                .map(f -> f.getClassName() + "." + f.getMethodName() + "(" + f.getFileName() + ":" + f.getLineNumber() + ")")
                .orElse(null));
        return (test == null ? "no test bound to this thread" : "test " + test) + ", thread '" + Thread.currentThread().getName() + "'"
                + (site == null ? "" : ", called from " + site);
    }

    private static boolean ignoredFrame(String cls) {
        return IGNORED_FRAMES.stream().anyMatch(cls::startsWith) || cls.contains("$Proxy") || cls.contains("$$");
    }

    public static boolean isForbidden(String sql) {
        return forbiddenReason(sql) != null;
    }

    /** Gọi cho mỗi chuỗi SQL tới JDBC sau khi context khởi động xong. Vi phạm: ném lỗi (FAIL) hoặc chỉ log (WARN). */
    public static void check(String sql) {
        Severity severity = GuardSettings.severity(GuardRule.PT12);
        if (sql == null || severity == Severity.OFF || IsolationTracker.isolatedClassRunning()) {
            return;
        }
        GuardStats.SQL_CHECKED.increment();
        String reason = forbiddenReason(sql);
        if (reason == null || GuardScope.isSuppressed(GuardRule.PT12)) {
            return;
        }
        Violation v = new Violation(GuardRule.PT12, location(), reason + ": " + sql.strip());
        if (severity == Severity.WARN) {
            GuardStats.VIOLATIONS_WARNED.increment();
            LOG.warn("[ptest-guard] (warning only)\n{}", v);
            return;
        }
        GuardStats.VIOLATIONS_FAILED.increment();
        GuardScope.report(v);
        throw GuardEnforcer.error("Forbidden SQL during parallel tests", List.of(v));
    }
}
