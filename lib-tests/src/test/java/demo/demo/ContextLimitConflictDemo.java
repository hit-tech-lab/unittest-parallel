package demo.demo;

import demo.core.DemoApp;
import demo.it.MainIT;
import io.hit.ptest.ParallelSpringTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestContextManager;

/**
 * Negative control B: vượt ngưỡng toàn cục ptest.max-contexts (đặt bằng system property lúc test, đọc lại mỗi lần tạo
 * context) => phải FAIL rõ ràng. {@code @Isolated}: không lớp nào khác chạy cùng lúc, nên việc đổi ngưỡng tạm thời không
 * ảnh hưởng demo khác.
 */
@Isolated
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "over-limit")
class ContextLimitConflictDemo {

    static class Helper extends MainIT {
    }

    private static String previous;

    @BeforeAll
    static void limitToOneActiveContext() throws Exception {
        previous = System.getProperty("ptest.max-contexts");
        System.setProperty("ptest.max-contexts", "1");
        new TestContextManager(Helper.class).getTestContext().getApplicationContext(); // chiếm slot duy nhất (hoặc đã có context khác => lỗi ngay ở đây)
    }

    @AfterAll
    static void restoreLimit() {
        if (previous == null) {
            System.clearProperty("ptest.max-contexts");
        } else {
            System.setProperty("ptest.max-contexts", previous);
        }
    }

    @Test
    void neverRuns() {
    }
}
