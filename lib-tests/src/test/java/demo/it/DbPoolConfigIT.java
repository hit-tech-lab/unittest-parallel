package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zaxxer.hikari.HikariDataSource;
import demo.core.DemoApp;
import com.hit.ptest.ParallelSpringTest;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Cấu hình pool/server của DB do thư viện cấp, kiểm trên kết nối THẬT (không chỉ đọc property):
 * <ul>
 *   <li>Hikari của context có {@code minimumIdle = 1} (mặc định của thư viện) và {@code maximumPoolSize} = {@code ptest.db.pool-size};</li>
 *   <li>Postgres: {@code SHOW max_connections} của server trả đúng giá trị {@code ptest.db.postgres.max-connections}
 *   (mặc định 300). Chỉ chạy ở chế độ postgres; ở H2 bị bỏ qua (khai báo trong check_reports.py).</li>
 * </ul>
 */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "db-pool")
class DbPoolConfigIT {

    @Autowired DataSource dataSource;

    @Test
    void libraryDefaultsHikariMinimumIdleToOne() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);

        assertThat(hikari.getMinimumIdle()).as("library default spring.datasource.hikari.minimum-idle").isEqualTo(1);
        assertThat(hikari.getMaximumPoolSize()).as("ptest.db.pool-size").isEqualTo(Integer.getInteger("ptest.db.pool-size"));
    }

    @Test
    void postgresServerMaxConnectionsIsTheConfiguredValue() throws Exception {
        assumeTrue("postgres".equals(System.getProperty("ptest.db.mode")), "SHOW max_connections is Postgres-only");
        int expected = Integer.getInteger("ptest.db.postgres.max-connections", 300);

        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SHOW max_connections")) {
            assertThat(rs.next()).isTrue();
            assertThat(Integer.parseInt(rs.getString(1))).as("SHOW max_connections").isEqualTo(expected);
        }
    }
}
