package demo.demo;

import demo.core.DemoApp;
import com.hit.ptest.ParallelSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/** F: @DirtiesContext bị chặn (DIRTIES_CONTEXT_UNSUPPORTED). */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "dirties")
@DirtiesContext
class DirtiesContextUnsupportedDemo {
    @Test
    void test() {
    }
}
