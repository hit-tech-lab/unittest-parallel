package demo.demo;

import demo.project.PaymentGateway;
import demo.project.ProjectBaseIT;
import io.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;

/** Negative control: ba bean cùng kiểu PaymentGateway, field không có @Qualifier và tên field không trùng tên bean => BEAN_AMBIGUOUS (liệt kê ứng viên). */
class DeclAmbiguousDemo extends ProjectBaseIT {

    @PerTestMock PaymentGateway anyGateway;

    @Test
    void test() {
    }
}
