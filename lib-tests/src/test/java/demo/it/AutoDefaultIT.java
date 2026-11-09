package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.DemoApp;
import io.hit.ptest.ParallelSpringTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * Đường Boot với chế độ MẶC ĐỊNH: KHÔNG đặt ptest.db.mode (script chạy với -Dtest.dbmode.arg= để JVM không có property
 * đó), nên auto chọn H2 vì H2 có trên classpath. Mặc định chỉ chạy trong lần verify "auto-h2" (tag auto-default).
 */
@Tag("auto-default")
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "auto-default")
class AutoDefaultIT {

    @Autowired Environment env;
    @Autowired DataSource dataSource;

    @Test
    void withNoModeConfiguredAutoPicksH2() throws Exception {
        assertThat(env.getProperty("ptest.db.mode")).as("scenario precondition: mode not configured anywhere").isNull();
        try (var c = dataSource.getConnection()) {
            assertThat(c.getMetaData().getURL()).startsWith("jdbc:h2:mem:ptest_f");
        }
    }
}
