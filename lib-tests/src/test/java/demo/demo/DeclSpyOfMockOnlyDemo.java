package demo.demo;

import demo.project.Ports;
import demo.project.ProjectBaseIT;
import com.hit.ptest.PerTestSpy;
import org.junit.jupiter.api.Test;

/** Negative control: @PerTestSpy trên bean do @PerTestMock cấp class của lớp cơ sở tạo (không có bản thật để spy) (không có bản thật để spy) => SPY_NO_BEAN. */
class DeclSpyOfMockOnlyDemo extends ProjectBaseIT {

    @PerTestSpy Ports.Mailer mailerSpy;

    @Test
    void test() {
    }
}
