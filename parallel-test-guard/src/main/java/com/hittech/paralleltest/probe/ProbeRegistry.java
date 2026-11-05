package com.hittech.paralleltest.probe;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.context.ApplicationContext;

/** JVM-wide record of the Spring contexts used by tests and their databases (reported by ParallelismProbe). */
public final class ProbeRegistry {

    private static final Map<Integer, String> CONTEXT_DATABASES = new ConcurrentHashMap<>();

    private ProbeRegistry() {
    }

    public static void recordContext(ApplicationContext ctx) {
        CONTEXT_DATABASES.computeIfAbsent(System.identityHashCode(ctx), k -> jdbcUrl(ctx));
    }

    static Map<Integer, String> contextDatabases() {
        return CONTEXT_DATABASES;
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
}
