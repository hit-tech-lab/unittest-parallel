package demo.demo;

import demo.project.PricingService;
import com.hit.ptest.PerTestSpy;
import org.junit.jupiter.api.Test;

/** Negative control: lớp cơ sở mock, lớp con spy cùng bean => MODE_CONFLICT. */
class DeclModeConflictHierarchyDemo extends DeclHierarchyBase {

    @PerTestSpy PricingService childSpy;

    @Test
    void test() {
    }
}
