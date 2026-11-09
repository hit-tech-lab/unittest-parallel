package com.hit.ptest.db;

import com.hit.ptest.ParallelTestException;
import com.hit.ptest.config.PtestKeys;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers Postgres: MỘT container cho cả JVM (singleton), mỗi context một database hoặc một schema.
 * Context khai báo schema tùy chỉnh luôn dùng database riêng, vì schema đó phải tồn tại trước ddl và tên schema
 * trong entity là cố định (không thể thêm tiền tố riêng cho từng context).
 */
public final class PostgresProvisioner implements DatabaseProvisioner {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresProvisioner.class);
    private static PostgreSQLContainer container;
    private static String startedImage;
    /** Lỗi khởi động container (cache): không thử lại ở mỗi context, mỗi context chỉ trả lại cùng lỗi ngay lập tức. */
    private static ParallelTestException.Config startFailure;
    private static int startAttempts;
    private static BiFunction<String, Integer, PostgreSQLContainer> starter = PostgresProvisioner::startContainer;

    private final String image;
    private final boolean schemaIsolation;
    private final int maxConnections;

    /**
     * @param maxConnections {@code max_connections} của server (container singleton: context đầu tiên quyết định);
     *                       mỗi context giữ pool Hikari riêng nên giá trị mặc định 100 của Postgres dễ bị vượt khi có nhiều context
     */
    public PostgresProvisioner(String image, boolean schemaIsolation, int maxConnections) {
        this.image = image;
        this.schemaIsolation = schemaIsolation;
        this.maxConnections = maxConnections;
    }

    private static synchronized PostgreSQLContainer container(String image, int maxConnections) {
        if (startFailure != null) {
            throw startFailure;
        }
        if (container == null) {
            startAttempts++;
            PostgreSQLContainer c;
            try {
                c = starter.apply(image, maxConnections);
            } catch (RuntimeException e) {
                startFailure = new ParallelTestException.Config("DB_PROVISION_FAILED: cannot start the Postgres container (image "
                        + image + "); not retried for the other contexts of this JVM: " + e);
                throw startFailure;
            }
            container = c;
            startedImage = image;
            LOG.info("Postgres singleton container started: {}", c.getJdbcUrl());
        } else {
            String warning = imageMismatchWarning(startedImage, image);
            if (warning != null) {
                LOG.warn(warning);
            }
        }
        return container;
    }

    /** Khởi động container (Ryuk dọn dẹp khi JVM kết thúc); tách ra để test lỗi khởi động không cần Docker thật. */
    private static PostgreSQLContainer startContainer(String image, int maxConnections) {
        // image do người dùng chọn (vd mirror nội bộ): khai báo tương thích để Testcontainers không từ chối tên lạ
        PostgreSQLContainer c = new PostgreSQLContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"))
                .withCommand("postgres", "-c", "max_connections=" + maxConnections);
        c.start();
        return c;
    }

    /** Chỉ cho test: quên container và lỗi đã cache. */
    public static synchronized void resetForTest() {
        container = null;
        startedImage = null;
        startFailure = null;
        startAttempts = 0;
        starter = PostgresProvisioner::startContainer;
    }

    public static synchronized void setStarterForTest(BiFunction<String, Integer, PostgreSQLContainer> replacement) {
        starter = replacement;
    }

    public static synchronized int startAttempts() {
        return startAttempts;
    }

    @Override
    public String mode() {
        return "postgres";
    }

    /** Container là singleton của JVM: image yêu cầu ở context sau bị bỏ qua. Trả về cảnh báo hoặc null. */
    public static String imageMismatchWarning(String started, String requested) {
        return started.equals(requested) ? null
                : PtestKeys.DB_POSTGRES_IMAGE + "=" + requested + " is IGNORED: this JVM already started the singleton Postgres container with image "
                + started + " (the first context decides the image)";
    }

    @Override
    public DbResource provision(String name, Collection<String> schemas) {
        PostgreSQLContainer c = container(image, maxConnections);
        boolean useSchema = schemaIsolation && schemas.isEmpty();
        String host = c.getHost() + ":" + c.getMappedPort(5432);
        boolean created = false;
        try {
            if (useSchema) {
                admin(c, "CREATE SCHEMA " + name);
                created = true;
                String url = "jdbc:postgresql://" + host + "/" + c.getDatabaseName() + "?currentSchema=" + name + ",public"; // public sau: extension/hàm dùng chung (vd uuid-ossp) vẫn tìm thấy; bảng mới nằm ở schema đầu tiên
                return new DbResource("postgres-schema:" + name, url, c.getUsername(), c.getPassword(),
                        () -> dropSchema(c, name), () -> schemaExists(c, name));
            }
            admin(c, "CREATE DATABASE " + name);
            created = true;
            String url = "jdbc:postgresql://" + host + "/" + name;
            if (!schemas.isEmpty()) {
                try (Connection conn = DriverManager.getConnection(url, c.getUsername(), c.getPassword());
                     Statement st = conn.createStatement()) {
                    for (String s : schemas) {
                        st.execute("CREATE SCHEMA IF NOT EXISTS " + s);
                    }
                }
            }
            return new DbResource("postgres-database:" + name, url, c.getUsername(), c.getPassword(),
                    () -> dropDatabase(c, name), () -> databaseExists(c, name));
        } catch (SQLException | RuntimeException e) {
            if (created) { // rollback: không để lại database/schema mới cô lập
                if (useSchema) {
                    dropSchema(c, name);
                } else {
                    dropDatabase(c, name);
                }
            }
            throw new ParallelTestException.Config("DB_PROVISION_FAILED: postgres " + name + ": " + e);
        }
    }

    private static boolean databaseExists(PostgreSQLContainer c, String name) {
        return exists(c, "SELECT 1 FROM pg_database WHERE datname = '" + name + "'");
    }

    private static boolean schemaExists(PostgreSQLContainer c, String name) {
        return exists(c, "SELECT 1 FROM pg_namespace WHERE nspname = '" + name + "'");
    }

    /** Không kiểm tra được (lỗi kết nối) tính là còn sót: báo cáo không bao giờ che giấu. */
    private static boolean exists(PostgreSQLContainer c, String sql) {
        try (Connection conn = DriverManager.getConnection(c.getJdbcUrl(), c.getUsername(), c.getPassword());
             Statement st = conn.createStatement(); var rs = st.executeQuery(sql)) {
            return rs.next();
        } catch (SQLException e) {
            return true;
        }
    }

    private static void dropSchema(PostgreSQLContainer c, String name) {
        adminQuietly(c, "DROP SCHEMA IF EXISTS " + name + " CASCADE");
    }

    /** WITH (FORCE) cần Postgres 13+; bản cũ hơn rơi về lệnh thường (sẽ thất bại nếu còn kết nối, có WARN). */
    private static void dropDatabase(PostgreSQLContainer c, String name) {
        try {
            admin(c, "DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
        } catch (SQLException e) {
            if ("42601".equals(e.getSQLState())) { // syntax error: server không hỗ trợ FORCE
                adminQuietly(c, "DROP DATABASE IF EXISTS " + name);
            } else {
                LOG.warn("DB cleanup failed, database '{}' is left behind: {}", name, e.toString());
            }
        }
    }

    private static void admin(PostgreSQLContainer c, String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(c.getJdbcUrl(), c.getUsername(), c.getPassword());
             Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private static void adminQuietly(PostgreSQLContainer c, String sql) {
        try {
            admin(c, sql);
        } catch (SQLException e) {
            LOG.warn("DB cleanup failed, resource may be left behind ({}): {}", sql, e.toString());
        }
    }
}
