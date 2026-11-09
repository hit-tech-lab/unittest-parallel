package com.hit.ptest.guard;

import org.junit.jupiter.api.Test;

/**
 * Kế thừa MỘT lần trong source set test, vd {@code class ParallelSafetyIT extends AbstractParallelSafetyTest {}}: một test
 * nhanh, không cần Spring, liệt kê MỌI vi phạm luật tĩnh của toàn bộ code test (kể cả lớp hàm tiện ích). Mỗi lớp
 * {@code @ParallelSpringTest} con được extension kiểm tra riêng lúc chạy (cùng luật); test này gom hết lại và thêm
 * luật PT01 cùng phần tiện ích.
 */
public abstract class AbstractParallelSafetyTest {

    @Test
    public void testCodeIsParallelSafe() {
        ParallelSafetyScanner.scanAndEnforce(getClass());
    }
}
