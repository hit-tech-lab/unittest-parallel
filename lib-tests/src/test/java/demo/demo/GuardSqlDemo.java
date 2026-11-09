package demo.demo;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.DemoApp;
import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.ParallelTest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Guard PT12 ở tầng JDBC (JdbcTemplate). Hai test PHẢI fail (SQL cả bảng trên thread của test; trên thread khác bị nuốt
 * nhưng vẫn gán đúng test), hai test control PHẢI pass (SQL theo khóa, trên thread test và thread khác).
 */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "guard-sql")
class GuardSqlDemo {

    @Autowired JdbcTemplate jdbc;

    @Test
    void wholeTableDeleteOnTheTestThread() {
        jdbc.update("delete from account");
    }

    @Test
    void wholeTableDeleteOnAnotherThreadIsSwallowedButStillFailsThisTest() throws Exception {
        AtomicReference<Throwable> swallowed = new AtomicReference<>();
        runOnOtherThread(() -> jdbc.update("truncate table account"), swallowed);
        assertThat(swallowed.get()).as("the app swallowed it; the test body itself sees nothing wrong").isNotNull();
    }

    @Test
    void controlTargetedDeleteOnTheTestThread() {
        assertThat(jdbc.update("delete from account where id = -1")).isZero();
    }

    @Test
    void controlTargetedStatementsOnAnotherThread() throws Exception {
        AtomicReference<Throwable> swallowed = new AtomicReference<>();
        runOnOtherThread(() -> jdbc.update("update account set balance = balance where id = -1"), swallowed);
        assertThat(swallowed.get()).isNull();
    }

    private static void runOnOtherThread(Runnable work, AtomicReference<Throwable> swallowed) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(ParallelTest.wrap(() -> {
                try {
                    work.run();
                } catch (Throwable t) {
                    swallowed.set(t); // như server nuốt lỗi thành HTTP 500
                }
            })).get();
        } finally {
            pool.shutdownNow();
        }
    }
}
