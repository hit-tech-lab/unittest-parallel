package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.DemoApp;
import demo.support.DbIsolationBase;
import demo.support.Rendezvous;
import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.SuppressGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SuppressGuard(rules = "PT12", reason = "drops the table of its OWN context database (per-context DB) to prove the other context is unaffected")
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "scenario=db-B")
@ParallelSpringTest(context = "db-b")
class DbIsolationBIT extends DbIsolationBase {

    @Override protected String group() { return "dbiso"; }
    @Override protected boolean dropsItsTable() { return true; }

    @Test
    void ownDatabasePerContext() {
        scenario();
        assertThat(Rendezvous.shared("dbiso:urls")).as("two contexts, two databases").hasSize(2);
    }
}
