package demo.demo;

import demo.project.Ports;
import demo.project.ProjectBaseIT;
import com.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/** Negative control: PER_CLASS + method chạy đồng thời + field @PerTestMock => CONFIG (instance dùng chung). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
class PerClassConcurrentDemo extends ProjectBaseIT {

    @PerTestMock Ports.Mailer mailer;

    @Test
    void a() {
    }

    @Test
    void b() {
    }
}
