package io.hit.ptest.guard;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;

/** Bộ đếm của guard trong JVM, ghi vào báo cáo để script kiểm tra chứng minh guard THẬT SỰ đã chạy. */
public final class GuardStats {

    static final LongAdder SQL_CHECKED = new LongAdder();
    static final LongAdder VIOLATIONS_FAILED = new LongAdder();
    static final LongAdder VIOLATIONS_WARNED = new LongAdder();
    static final LongAdder CLASSES_CHECKED = new LongAdder();
    static final LongAdder POOLS_CHECKED = new LongAdder();
    static final LongAdder FOREIGN_THREAD = new LongAdder();

    private GuardStats() {
    }

    public static Map<String, String> snapshot() {
        Map<String, String> s = new TreeMap<>();
        s.put("guardClassesChecked", Long.toString(CLASSES_CHECKED.sum()));
        s.put("guardPoolsChecked", Long.toString(POOLS_CHECKED.sum()));
        s.put("guardSqlChecked", Long.toString(SQL_CHECKED.sum()));
        s.put("guardViolationsFailed", Long.toString(VIOLATIONS_FAILED.sum()));
        s.put("guardViolationsWarned", Long.toString(VIOLATIONS_WARNED.sum()));
        s.put("guardForeignThreadViolations", Long.toString(FOREIGN_THREAD.sum()));
        return s;
    }
}
