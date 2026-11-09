package com.hit.ptest.intercept;

import java.util.concurrent.atomic.LongAdder;

/**
 * Bộ đếm lời gọi qua bean đã bọc của toàn JVM, dùng để báo cáo.
 *
 * @apiNote internal: không phải API công khai của thư viện.
 */
public final class InterceptStats {

    public static final LongAdder INTERCEPTED = new LongAdder();
    public static final LongAdder REAL = new LongAdder();
    public static final LongAdder UNBOUND = new LongAdder();

    private InterceptStats() {
    }
}
