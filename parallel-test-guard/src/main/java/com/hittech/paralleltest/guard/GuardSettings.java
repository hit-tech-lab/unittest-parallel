package com.hittech.paralleltest.guard;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Settings from {@code parallel-test-guard.properties} on the test classpath, each overridable by
 * a system property {@code paralleltest.<key>}.
 * <pre>
 * enabled=true
 * max-contexts=4
 * check-pool-size=true
 * sql-guard=true
 * forbidden-calls=com.example.AuditLog#clear(), com.example.Cache#evictAll(*)
 * </pre>
 */
public final class GuardSettings {

    public static final String FILE = "parallel-test-guard.properties";

    static final List<String> DEFAULT_FORBIDDEN_CALLS = List.of(
            "org.springframework.data.repository.CrudRepository#deleteAll()",
            "org.springframework.data.repository.CrudRepository#count()",
            "org.springframework.data.repository.CrudRepository#findAll()",
            "org.springframework.data.repository.PagingAndSortingRepository#findAll(org.springframework.data.domain.Sort)",
            "org.springframework.data.repository.PagingAndSortingRepository#findAll(org.springframework.data.domain.Pageable)",
            "org.springframework.data.jpa.repository.JpaRepository#deleteAllInBatch()",
            "org.springframework.test.jdbc.JdbcTestUtils#deleteFromTables(*)",
            "org.springframework.test.jdbc.JdbcTestUtils#countRowsInTable(*)",
            "org.springframework.test.jdbc.JdbcTestUtils#dropTables(*)");

    private static volatile GuardSettings instance;

    private final Properties props;

    private GuardSettings(Properties props) {
        this.props = props;
    }

    public static GuardSettings get() {
        GuardSettings s = instance;
        if (s == null) {
            synchronized (GuardSettings.class) {
                if (instance == null) {
                    instance = load(Thread.currentThread().getContextClassLoader());
                }
                s = instance;
            }
        }
        return s;
    }

    static GuardSettings load(ClassLoader loader) {
        Properties p = new Properties();
        try (InputStream in = loader == null ? null : loader.getResourceAsStream(FILE)) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + FILE, e);
        }
        return new GuardSettings(p);
    }

    public static GuardSettings of(Properties props) {
        return new GuardSettings(props);
    }

    private String value(String key, String def) {
        String sys = System.getProperty("paralleltest." + key);
        if (sys != null) {
            return sys.trim();
        }
        return props.getProperty(key, def).trim();
    }

    public boolean enabled() {
        return Boolean.parseBoolean(value("enabled", "true"));
    }

    public int maxContexts() {
        return Integer.parseInt(value("max-contexts", "4"));
    }

    public boolean checkPoolSize() {
        return Boolean.parseBoolean(value("check-pool-size", "true"));
    }

    public boolean sqlGuard() {
        return Boolean.parseBoolean(value("sql-guard", "true"));
    }

    public List<ForbiddenCall> forbiddenCalls() {
        List<String> specs = new ArrayList<>(DEFAULT_FORBIDDEN_CALLS);
        Arrays.stream(value("forbidden-calls", "").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).forEach(specs::add);
        return specs.stream().map(ForbiddenCall::parse).toList();
    }
}
