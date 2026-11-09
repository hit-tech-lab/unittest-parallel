package io.hit.ptest.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** PT11: pool nhỏ hơn số thread song song bị báo; đủ pool, không song song, hoặc không đọc được kích thước thì không. */
class PoolSizeCheckTest {

    public abstract static class BaseSource implements DataSource {
        @Override public Connection getConnection() throws SQLException { throw new SQLException("unused"); }
        @Override public Connection getConnection(String u, String p) throws SQLException { throw new SQLException("unused"); }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("unused"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    public static class PooledSource extends BaseSource {
        private final int max;

        PooledSource(int max) {
            this.max = max;
        }

        public int getMaximumPoolSize() {
            return max;
        }
    }

    /** DataSource sau cùng không có getter kích thước pool: guard không thể kiểm tra nên bỏ qua. */
    public static class NoGetter extends BaseSource {
    }

    @Configuration(proxyBeanMethods = false)
    static class Small {
        @Bean DataSource main() {
            return new PooledSource(2);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Enough {
        @Bean DataSource main() {
            return new PooledSource(8);
        }
    }

    private AnnotationConfigApplicationContext ctx;

    @AfterEach
    void close() {
        ctx.close();
    }

    private AnnotationConfigApplicationContext started(Class<?> cfg, boolean guarded) {
        ctx = new AnnotationConfigApplicationContext();
        ctx.register(cfg);
        if (guarded) {
            JdbcSqlGuardPostProcessor guard = new JdbcSqlGuardPostProcessor(ctx);
            ctx.addApplicationListener(guard);
            ctx.addBeanFactoryPostProcessor(bf -> bf.addBeanPostProcessor(guard));
        }
        ctx.refresh();
        return ctx;
    }

    @Test
    void poolSmallerThanParallelismIsReportedWithBothNumbers() {
        List<Violation> v = PoolSizeCheck.check(started(Small.class, false), PoolSizeCheckTest.class, 8);
        assertThat(v).singleElement().satisfies(x -> {
            assertThat(x.rule()).isEqualTo(GuardRule.PT11);
            assertThat(x.detail()).contains("maximum pool size 2 < JUnit parallelism 8");
            assertThat(x.location()).contains("DataSource bean 'main'").contains("PoolSizeCheckTest");
        });
    }

    @Test
    void sizeIsReadThroughTheSqlGuardProxyToo() {
        assertThat(PoolSizeCheck.check(started(Small.class, true), PoolSizeCheckTest.class, 8)).hasSize(1);
        ctx.close();
        assertThat(PoolSizeCheck.check(started(Enough.class, true), PoolSizeCheckTest.class, 8)).isEmpty();
    }

    @Test
    void enoughPoolOrNoParallelismIsNotReported() {
        assertThat(PoolSizeCheck.check(started(Enough.class, false), PoolSizeCheckTest.class, 8)).isEmpty();
        assertThat(PoolSizeCheck.check(ctx, PoolSizeCheckTest.class, 9)).hasSize(1);
        assertThat(PoolSizeCheck.check(ctx, PoolSizeCheckTest.class, 1)).isEmpty();
    }

    @Test
    void dataSourceWithoutASizeGetterIsSkipped() {
        ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean("noGetter", DataSource.class, NoGetter::new);
        ctx.refresh();
        assertThat(PoolSizeCheck.check(ctx, PoolSizeCheckTest.class, 8)).isEmpty();
    }
}
