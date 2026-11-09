package demo.matrix;

import demo.core.DemoApp;
import demo.project.Ports;
import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Base "one" của bộ 3 base có tập mock cấp class khác nhau nhưng cùng tên context "sets": mỗi base một context. */
@PerTestMock(Ports.Mailer.class)
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "sets")
abstract class SetOneBase {

    @Autowired ApplicationContext ctx;

    @Test
    void leavesOfTheSameBaseShareOneContext() {
        SetsProbe.check("one", ctx);
    }
}
