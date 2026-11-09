package demo.demo;

import demo.core.DemoApp;
import com.hit.ptest.ParallelSpringTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;

/** Guard PT13: PER_CLASS + field mutable + method chạy song song. */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "guard-perclass")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GuardPerClassDemo {

    String lastSeen;

    @Test
    void a() {
        lastSeen = "a";
    }

    @Test
    void b() {
        lastSeen = "b";
    }
}
