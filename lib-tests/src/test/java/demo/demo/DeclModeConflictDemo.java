package demo.demo;

import demo.project.PricingService;
import demo.project.ProjectBaseIT;
import io.hit.ptest.PerTestMock;
import io.hit.ptest.PerTestSpy;
import org.junit.jupiter.api.Test;

/** Negative control: cùng bean vừa @PerTestMock vừa @PerTestSpy trong cùng lớp => MODE_CONFLICT (không chọn âm thầm một trong hai). */
class DeclModeConflictDemo extends ProjectBaseIT {

    @PerTestMock PricingService asMock;
    @PerTestSpy PricingService asSpy;

    @Test
    void test() {
    }
}
