package demo.demo;

import demo.core.DemoApp;
import io.hit.ptest.ParallelSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Negative control B: cùng tên context "dup" nhưng khai báo wrap khác nhau (xem NameConflictBDemo) => ít nhất một bên FAIL (CONTEXT_NAME_CONFLICT), bất kể lớp nào được nạp trước. */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "dup", wrap = "backupFx")
class NameConflictBDemo {
    @Test
    void test() {
    }
}
