package demo.demo;

import demo.core.AuditLog;
import demo.project.ProjectBaseIT;
import com.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;

/**
 * Negative control: lớp con (dưới lớp mang @ParallelSpringTest) khai field @PerTestMock cho kiểu chưa có trong context: muốn
 * thêm bean mock-only phải khai @PerTestMock(AuditLog.class) CẤP CLASS ở lớp mang @ParallelSpringTest hoặc lớp cha (vì là phần khóa
 * context) => BEAN_NOT_FOUND kèm hướng dẫn.
 */
class DeclMockNotFoundDemo extends ProjectBaseIT {

    @PerTestMock AuditLog notInThisContext;

    @Test
    void test() {
    }
}
