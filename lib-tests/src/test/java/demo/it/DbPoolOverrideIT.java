package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import demo.core.DemoApp;
import io.hit.ptest.ParallelSpringTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Người dùng đặt {@code spring.datasource.hikari.minimum-idle} thì thắng mặc định {@code 1} của thư viện (đo trên Hikari thật).
 *
 * <p>Giá trị 2 (khác mặc định 1) là giá trị lớn nhất luôn hợp lệ ở lib-tests: Hikari cắt minimum-idle xuống tối đa maximum-pool-size, và
 * pom của lib-tests đặt TƯỜNG MINH {@code ptest.db.pool-size = test.parallelism} (nên không áp mức tối thiểu 4 của pool MẶC ĐỊNH trong thư viện,
 * vốn chỉ dùng khi không đặt property này); nhỏ nhất bị stress thử là parallelism 2 => pool 2. Giá trị 3 làm test lỗi ở parallelism 2 chỉ vì bị cắt.
 */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.hikari.minimum-idle=2")
@ParallelSpringTest(context = "db-pool-user")
class DbPoolOverrideIT {

    @Autowired DataSource dataSource;

    @Test
    void userMinimumIdleOverridesTheLibraryDefault() throws Exception {
        assertThat(dataSource.unwrap(HikariDataSource.class).getMinimumIdle()).isEqualTo(2);
    }
}
