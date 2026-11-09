package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.DemoApp;
import demo.support.SharedPostgres;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.SuppressGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Người dùng dùng @ServiceConnection (Testcontainers): DataSource trỏ tới container của họ; report phải ghi không có DB thư viện cấp. */
@SuppressGuard(rules = "PT11", reason = "single test method: the user-owned Testcontainers DataSource keeps the Hikari default pool, which is the point of the test")
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "svc-conn")
class ServiceConnectionIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = SharedPostgres.get();

    @Autowired JdbcTemplate jdbc;

    @Test
    void dataSourceIsTheUsersContainer() throws Exception {
        String url;
        try (var connection = jdbc.getDataSource().getConnection()) {
            url = connection.getMetaData().getURL();
        }

        assertThat(url).contains(":" + POSTGRES.getMappedPort(5432)).doesNotContain("ptest_f");
        assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
    }
}
