package demo.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** Container Postgres của CHÍNH test (không phải của thư viện) dùng cho các IT "người dùng tự sở hữu DataSource". */
public final class SharedPostgres {

    private static PostgreSQLContainer container;

    private SharedPostgres() {
    }

    public static synchronized PostgreSQLContainer get() {
        if (container == null) {
            PostgreSQLContainer c = new PostgreSQLContainer("postgres:16.4");
            c.start(); // Ryuk dọn dẹp khi JVM kết thúc
            container = c;
        }
        return container;
    }
}
