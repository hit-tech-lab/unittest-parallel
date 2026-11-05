package com.example.parallel.support;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Registered globally via META-INF/spring.factories. Records every distinct ApplicationContext
 * used by tests and the database it points to, so the build can verify:
 *  - the context cache is effective (contexts are shared, not re-created per class);
 *  - no two contexts share the same database.
 */
public class ContextTrackingListener implements TestExecutionListener, Ordered {

    @Override
    public void prepareTestInstance(TestContext testContext) {
        ApplicationContext ctx = testContext.getApplicationContext();
        ProbeRegistry.CONTEXT_DATABASES.computeIfAbsent(System.identityHashCode(ctx), k -> jdbcUrl(ctx));
    }

    private static String jdbcUrl(ApplicationContext ctx) {
        DataSource ds = ctx.getBeanProvider(DataSource.class).getIfAvailable();
        if (ds == null) {
            return "none";
        }
        try (Connection c = ds.getConnection()) {
            return c.getMetaData().getURL();
        } catch (Exception e) {
            return "error:" + e.getMessage();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
