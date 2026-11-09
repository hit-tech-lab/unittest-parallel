package demo.matrix;

import demo.core.DemoApp;
import demo.project.Ports;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Base "two" của bộ 3 base có tập mock cấp class khác nhau nhưng cùng tên context "sets": mỗi base một context. */
@PerTestMock(Ports.Mailer.class)
@PerTestMock(Ports.SmsSender.class)
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "sets")
abstract class SetTwoBase {

    @Autowired ApplicationContext ctx;

    @Test
    void leavesOfTheSameBaseShareOneContext() {
        SetsProbe.check("two", ctx);
    }
}
