package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.DemoApp;
import demo.support.SharedPostgres;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.SuppressGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Người dùng đặt url/username/password bằng @DynamicPropertySource từ container Testcontainers của họ. */
@SuppressGuard(rules = "PT11", reason = "single test method: the user-owned DataSource keeps the Hikari default pool, which is the point of the test")
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "dyn-prop")
class DynamicPropertyContainerIT {

    static final PostgreSQLContainer POSTGRES = SharedPostgres.get();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

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
