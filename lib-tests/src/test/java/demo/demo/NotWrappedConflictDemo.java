package demo.demo;

import demo.core.AuditLog;
import demo.core.DemoApp;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Negative control A: mock cho bean bị loại khỏi tập bọc (exclude) => lỗi rõ ràng BEAN_NOT_WRAPPED, kèm lý do. */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "narrow", exclude = "auditLog")
class NotWrappedConflictDemo {

    @PerTestMock AuditLog auditLog;

    @Test
    void test() {
    }
}
