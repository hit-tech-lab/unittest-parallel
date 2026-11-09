package demo.demo;

import demo.core.DemoApp;
import com.hit.ptest.ParallelSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** F: @Transactional (rollback) + RANDOM_PORT bị chặn (TRANSACTIONAL_RANDOM_PORT_UNSUPPORTED). */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ParallelSpringTest(context = "txport")
@Transactional
class TransactionalRandomPortUnsupportedDemo {
    @Test
    void test() {
    }
}
