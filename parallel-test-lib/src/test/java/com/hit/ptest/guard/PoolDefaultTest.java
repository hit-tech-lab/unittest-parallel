package com.hit.ptest.guard;

import com.hit.ptest.testsupport.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Cỡ pool mặc định của DB do thư viện cấp = max(4, số thread song song của JUnit), nên PT11 không hỏng khi chạy song song &gt; 4. */
@org.junit.jupiter.api.parallel.Isolated
class PoolDefaultTest {

    private static final String P = "junit.jupiter.execution.parallel.";

    @BeforeEach
    @AfterEach
    void reset() {
        JUnitConfig.reset();
        System.clearProperty(P + "enabled");
        System.clearProperty(P + "config.strategy");
        System.clearProperty(P + "config.fixed.parallelism");
    }

    private static void parallel(int threads) {
        System.setProperty(P + "enabled", "true");
        System.setProperty(P + "config.strategy", "fixed");
        System.setProperty(P + "config.fixed.parallelism", Integer.toString(threads));
    }

    private static String poolOf(Map<String, String> props) {
        Map<String, String> all = new java.util.HashMap<>(props);
        all.put("ptest.db.mode", "h2"); // không phụ thuộc -Dptest.db.mode của lần chạy ma trận
        AnnotationConfigApplicationContext c = Fixtures.started("pool-default", new String[0], all, Fixtures.AskForCredentialsConfig.class);
        try {
            return c.getBean("credentials", String.class).split("\\|")[2];
        } finally {
            Fixtures.closeAll();
        }
    }

    @Test
    void defaultPoolFollowsTheParallelismWhenAboveFour() {
        parallel(12);
        assertThat(poolOf(Map.of())).isEqualTo("12");
    }

    @Test
    void defaultPoolIsAtLeastFour() {
        parallel(2);
        assertThat(poolOf(Map.of())).isEqualTo("4");
        reset();
        assertThat(poolOf(Map.of())).as("no parallel execution").isEqualTo("4");
    }

    @Test
    void explicitPoolSizeWins() {
        parallel(12);
        assertThat(poolOf(Map.of("ptest.db.pool-size", "7"))).isEqualTo("7");
    }
}
