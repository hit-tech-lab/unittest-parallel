package demo.demo;

import demo.core.DemoApp;
import com.hit.ptest.ParallelSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Guard PT11: pool 2 nhỏ hơn số thread song song của JUnit (24) => fail trước khi test chạy. */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "ptest.db.pool-size=2")
@ParallelSpringTest(context = "guard-pool")
class GuardPoolSmallDemo {

    @Test
    void neverRuns() {
    }
}
