package demo.arch;

import com.hit.ptest.guard.AbstractParallelSafetyTest;

/**
 * Quét tĩnh toàn bộ code test của consumer (trừ package demo.demo = negative control): phải sạch vi phạm FAIL.
 * Các miễn trừ còn lại đều có {@code @SuppressGuard} kèm lý do.
 */
class ParallelSafetyIT extends AbstractParallelSafetyTest {
}
