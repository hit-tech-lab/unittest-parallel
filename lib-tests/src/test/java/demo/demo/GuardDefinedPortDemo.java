package demo.demo;

import demo.core.DemoApp;
import com.hit.ptest.ParallelSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Guard PT08: cổng cố định. */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ParallelSpringTest(context = "guard-port")
class GuardDefinedPortDemo {

    @Test
    void neverRuns() {
    }
}
