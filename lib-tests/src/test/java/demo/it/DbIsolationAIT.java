package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.DemoApp;
import demo.support.DbIsolationBase;
import demo.support.Rendezvous;
import com.hit.ptest.ParallelSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "scenario=db-A")
@ParallelSpringTest(context = "db-a")
class DbIsolationAIT extends DbIsolationBase {

    @Override protected String group() { return "dbiso"; }
    @Override protected boolean dropsItsTable() { return false; }

    @Test
    void ownDatabasePerContext() {
        scenario();
        assertThat(Rendezvous.shared("dbiso:urls")).as("two contexts, two databases").hasSize(2);
    }
}
