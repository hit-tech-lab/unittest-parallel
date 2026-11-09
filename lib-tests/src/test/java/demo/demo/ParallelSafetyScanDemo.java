package demo.demo;

import com.hit.ptest.guard.AbstractParallelSafetyTest;

/**
 * Quét tĩnh cả source set KHÔNG loại trừ package demo (profile conflict-demo đặt ptest.guard.scan-exclude rỗng): phải
 * fail và liệt kê vi phạm của các lớp Guard*Demo (cùng một lần), nhưng KHÔNG báo các lớp đã miễn trừ đúng cách.
 */
class ParallelSafetyScanDemo extends AbstractParallelSafetyTest {
}
