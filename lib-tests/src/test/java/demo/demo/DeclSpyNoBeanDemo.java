package demo.demo;

import demo.core.AuditLog;
import demo.project.ProjectBaseIT;
import io.hit.ptest.PerTestSpy;
import org.junit.jupiter.api.Test;

/** Negative control: @PerTestSpy trên kiểu không có bean nào trong context => SPY_NO_BEAN. */
class DeclSpyNoBeanDemo extends ProjectBaseIT {

    @PerTestSpy AuditLog notInThisContext;

    @Test
    void test() {
    }
}
