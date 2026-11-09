package com.hit.ptest.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hit.ptest.ParallelTestException;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** PT12 ở tầng JDBC thật (H2): mọi đường tới JDBC bị chặn sau refresh, không bị chặn lúc khởi động/đóng context. */
@Isolated
class JdbcSqlGuardPostProcessorTest {

    static final AtomicInteger SEQ = new AtomicInteger();

    @Configuration(proxyBeanMethods = false)
    static class Cfg {
        @Bean(destroyMethod = "") DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:sqlguard" + SEQ.incrementAndGet() + ";DB_CLOSE_DELAY=-1");
            return ds;
        }

        /** Chạy SQL cả bảng LÚC KHỞI ĐỘNG: guard chưa bật nên không chặn. */
        @Bean String startupSql(DataSource ds) throws Exception {
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                st.execute("create table t(id int primary key, v int)");
                st.executeUpdate("insert into t values (1, 1)");
                st.executeUpdate("update t set v = 2");
                st.executeUpdate("delete from t");
            }
            return "ok";
        }
    }

    private AnnotationConfigApplicationContext ctx;

    @AfterEach
    void close() {
        if (ctx != null) {
            ctx.close();
        }
    }

    private DataSource started() {
        ctx = new AnnotationConfigApplicationContext();
        ctx.register(Cfg.class);
        JdbcSqlGuardPostProcessor guard = new JdbcSqlGuardPostProcessor(ctx);
        ctx.addApplicationListener(guard);
        ctx.addBeanFactoryPostProcessor(bf -> bf.addBeanPostProcessor(guard));
        ctx.refresh();
        return ctx.getBean(DataSource.class);
    }

    @Test
    void statementsFromEveryJdbcPathAreCheckedAfterTheContextStarted() throws Exception {
        DataSource ds = started();
        try (Connection c = ds.getConnection()) {
            Statement st = c.createStatement();
            assertThatThrownBy(() -> st.execute("delete from t")).isInstanceOf(ParallelTestException.Guard.class).hasMessageContaining("[PT12]");
            assertThatThrownBy(() -> st.executeUpdate("update t set v = 0")).hasMessageContaining("UPDATE without WHERE");
            assertThatThrownBy(() -> st.addBatch("truncate table t")).hasMessageContaining("TRUNCATE");
            assertThatThrownBy(() -> c.prepareStatement("delete from t")).hasMessageContaining("DELETE without WHERE");
            assertThatThrownBy(() -> c.prepareCall("drop table t")).hasMessageContaining("DROP");
            assertThatThrownBy(() -> c.nativeSQL("truncate table t")).hasMessageContaining("TRUNCATE");
            assertThat(st.getConnection()).as("statement.getConnection returns the guarded connection").isSameAs(c);
        }
    }

    @Test
    void targetedStatementsStillWorkAndResultsAreReal() throws Exception {
        DataSource ds = started();
        try (Connection c = ds.getConnection()) {
            Statement st = c.createStatement();
            assertThatNoException().isThrownBy(() -> {
                st.executeUpdate("insert into t values (7, 70)");
                st.executeUpdate("update t set v = 71 where id = 7");
            });
            try (var ps = c.prepareStatement("select v from t where id = ?")) {
                ps.setInt(1, 7);
                try (var rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(71);
                }
            }
            assertThatNoException().isThrownBy(() -> st.executeUpdate("delete from t where id = 7"));
        }
    }

    @Test
    void dataSourceStaysUsableThroughUnwrapAndIsAnInterfaceProxy() throws Exception {
        DataSource ds = started();
        assertThat(ds.unwrap(JdbcDataSource.class)).isNotNull();
        assertThat(ds.isWrapperFor(JdbcDataSource.class)).isTrue();
        assertThat(ds).isNotInstanceOf(JdbcDataSource.class).isInstanceOf(JdbcSqlGuardPostProcessor.SqlGuarded.class);
    }

    @Test
    void sqlRunWhileTheContextClosesIsNotChecked() throws Exception {
        DataSource ds = started();
        ctx.close(); // ContextClosedEvent => guard tắt trước khi bean hủy (vd Hibernate create-drop chạy DROP)
        try (Connection c = ds.unwrap(JdbcDataSource.class).getConnection()) {
            assertThatNoException().isThrownBy(() -> c.createStatement().execute("drop table t"));
        }
    }

    @Test
    void ruleOffMeansTheGuardIsNotEvenInstalledByTheCustomizerButTheProcessorStillDelegates() throws Exception {
        org.springframework.core.SpringProperties.setProperty("ptest.guard.PT12", "off");
        try {
            DataSource ds = started();
            try (Connection c = ds.getConnection()) {
                assertThatNoException().isThrownBy(() -> c.createStatement().execute("delete from t"));
            }
        } finally {
            org.springframework.core.SpringProperties.setProperty("ptest.guard.PT12", null);
        }
    }
}
