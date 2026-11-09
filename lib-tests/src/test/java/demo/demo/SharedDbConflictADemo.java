package demo.demo;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.DemoApp;
import demo.support.DbIsolationBase;
import demo.support.Rendezvous;
import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.SuppressGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Negative control C: ptest.db.mode=none + cùng URL H2 cố định => hai context dùng chung DB => phải FAIL. */
@SuppressGuard(rules = "PT11", reason = "negative control: user-owned shared DB keeps the Hikari default pool; the point is the cross-context data conflict")
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "ptest.db.mode=none", "spring.datasource.url=jdbc:h2:mem:shared-demo;DB_CLOSE_DELAY=-1", "scenario=shared-A"})
@ParallelSpringTest(context = "shared-a")
class SharedDbConflictADemo extends DbIsolationBase {

    @Override protected String group() { return "dbshared"; }
    @Override protected boolean dropsItsTable() { return false; }

    @Test
    void sharedDatabase() {
        scenario();
    }
}
