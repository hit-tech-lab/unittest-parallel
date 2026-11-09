package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import demo.core.DemoApp;
import io.hit.ptest.ParallelSpringTest;
import javax.sql.DataSource;
import io.hit.ptest.SuppressGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Người dùng chỉ đặt spring.datasource.url (không username/password): thư viện KHÔNG cấp DB thừa và không áp username,
 * password hay pool của nó; report phải ghi context này không có DB do thư viện cấp (xem check_reports.py).
 */
@SuppressGuard(rules = "PT11", reason = "asserts the user-owned pool (10) is NOT replaced by the library pool; one test method")
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:h2:mem:user_owned_db;DB_CLOSE_DELAY=-1")
@ParallelSpringTest(context = "user-url")
class UserUrlOnlyIT {

    @Autowired DataSource dataSource;

    @Test
    void connectionIsTheUsersAndHasNoLibrarySettings() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);

        assertThat(hikari.getJdbcUrl()).isEqualTo("jdbc:h2:mem:user_owned_db;DB_CLOSE_DELAY=-1");
        assertThat(hikari.getMaximumPoolSize()).as("not the library's ptest.db.pool-size (4)").isEqualTo(10);
        try (var c = dataSource.getConnection()) {
            assertThat(c.getMetaData().getURL()).doesNotContain("ptest_f");
        }
    }
}
