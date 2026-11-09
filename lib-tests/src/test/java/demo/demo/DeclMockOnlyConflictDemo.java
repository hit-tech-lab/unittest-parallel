package demo.demo;

import demo.core.AuditLog;
import demo.core.DemoApp;
import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Negative control: cùng tên bean 'dup' khai cấp class với hai kiểu khác nhau => MOCK_ONLY_CONFLICT lúc dựng context. */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@PerTestMock(name = "dup", classes = AuditLog.class)
@PerTestMock(name = "dup", classes = StringBuilder.class)
@ParallelSpringTest(context = "decl-mock-only-conflict")
class DeclMockOnlyConflictDemo {

    @Test
    void test() {
    }
}
