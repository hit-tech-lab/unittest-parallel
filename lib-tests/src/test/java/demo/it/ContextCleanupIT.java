package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import demo.core.DemoApp;
import demo.support.DbProbe;
import com.hit.ptest.ParallelSpringTest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestContextManager;

/**
 * Tài nguyên không rò rỉ: context lỗi lúc khởi động (trước khi bean hạ tầng nào được tạo), context lỗi provision giữa chừng
 * và context đóng bình thường đều phải drop DB. Chạy lại theo từng chế độ DB của ma trận (h2, postgres database/schema).
 */
class ContextCleanupIT {

    record Credentials(String url, String user, String password) {
        boolean exists() {
            return DbProbe.exists(url, user, password);
        }
    }

    static final AtomicReference<Credentials> FAILED = new AtomicReference<>();

    /** Hỏi datasource (=> thư viện cấp DB) rồi làm context hỏng. */
    @Configuration
    static class FailAfterAskingForDatabase {
        FailAfterAskingForDatabase(Environment env) {
            FAILED.set(new Credentials(env.getProperty("spring.datasource.url"), env.getProperty("spring.datasource.username"),
                    env.getProperty("spring.datasource.password")));
            throw new IllegalStateException("deliberate startup failure");
        }
    }

    @SpringBootTest(classes = {DemoApp.class, FailAfterAskingForDatabase.class}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @ParallelSpringTest(context = "cleanup-fail")
    static class FailingStartup {
    }

    @SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @ParallelSpringTest(context = "cleanup-ok")
    static class Closable {
    }

    /** Context riêng chỉ để lấy kết nối tới server Postgres (không bị test khác đóng). */
    @SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @ParallelSpringTest(context = "cleanup-probe")
    static class Probe {
    }

    @SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @ParallelSpringTest(context = "cleanup-rollback", schemas = "bad;name")
    static class BadSchema {
    }

    private static ApplicationContext load(Class<?> helper) throws Exception {
        return new TestContextManager(helper).getTestContext().getApplicationContext();
    }

    private static Credentials credentialsOf(ApplicationContext ctx) {
        Environment env = ctx.getEnvironment();
        return new Credentials(env.getProperty("spring.datasource.url"), env.getProperty("spring.datasource.username"),
                env.getProperty("spring.datasource.password"));
    }

    @Test
    void contextThatFailsDuringStartupDropsItsDatabase() {
        assertThatThrownBy(() -> load(FailingStartup.class)).hasRootCauseMessage("deliberate startup failure");

        Credentials c = FAILED.get();
        assertThat(c).as("the failing bean ran, so the DB was provisioned").isNotNull();
        assertThat(c.exists()).as("DB of the failed context is gone: %s", c.url()).isFalse();
    }

    @Test
    void closingAContextDropsItsDatabaseAndTheProbeSeesTheDifference() throws Exception {
        ApplicationContext ctx = load(Closable.class);
        Credentials c = credentialsOf(ctx);

        assertThat(c.exists()).as("negative control: probe sees the live DB").isTrue();
        ((ConfigurableApplicationContext) ctx).close();
        assertThat(c.exists()).as("DB dropped after the context closed").isFalse();
    }

    @Test
    void provisioningFailureHalfwayLeavesNoDatabaseBehind() throws Exception {
        boolean postgres = "postgres".equals(System.getProperty("ptest.db.mode"));
        Credentials any = credentialsOf(load(Probe.class)); // kết nối tới cùng server Postgres để truy vấn pg_database

        assertThatThrownBy(() -> load(BadSchema.class)).hasStackTraceContaining("DB_PROVISION_FAILED");

        assumeTrue(postgres, "pg_database probe is Postgres-only (H2 rollback is covered by the library unit tests)");
        assertThat(DbProbe.postgresDatabasesLike(any.url(), any.user(), any.password(), "%cleanup_rollback%"))
                .as("CREATE DATABASE succeeded then CREATE SCHEMA failed: the database was rolled back").isZero();
    }
}
