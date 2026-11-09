package demo.demo;

import demo.core.AccountRepository;
import demo.core.DemoApp;
import com.hit.ptest.ParallelSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;

/**
 * Guard PT07: gọi cả bảng trên repository / JdbcTestUtils dùng chung bị chặn TRƯỚC khi context khởi động. Mỗi lệnh ở
 * dưới phải được báo riêng (lớp không bao giờ chạy đến test).
 */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "guard-calls")
class GuardForbiddenCallDemo {

    @Autowired AccountRepository repo;
    @Autowired JdbcTemplate jdbc;

    @Test
    void wholeTableCalls() {
        repo.deleteAll();
        repo.count();
        repo.findAll();
        repo.deleteAllInBatch();
        JdbcTestUtils.deleteFromTables(jdbc, "account");
        repo.deleteById(1L); // control: gọi theo khóa KHÔNG bị báo
        repo.findById(1L);
    }
}
