package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hit.ptest.ParallelSpringTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.extension.ExtendWith;
import com.hit.ptest.ParallelTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContextManager;

/**
 * WARN khi {@code ptest.max-contexts x ptest.db.pool-size} vượt {@code ptest.db.postgres.max-connections}. Chỉ chạy ở chế độ postgres (ở H2 bị bỏ qua, khai báo trong
 * check_reports.py); context KHÔNG có DataSource nên không ai hỏi URL và không phát sinh DB (cảnh báo được tính khi tạo provisioner). Mỗi context có giá trị riêng để không lẫn với log của test khác. Bắt đầu ra console (OutputCaptureExtension) thay vì gắn appender, vì
 * Spring Boot khởi tạo lại logback khi nạp context và gỡ appender gắn tay.
 *
 * <p>Các phương thức của lớp chạy tuần tự ({@code SAME_THREAD}): {@code OutputCaptureExtension} dùng chung MỘT {@code OutputCapture} (ArrayDeque không
 * an toàn đa luồng) cho cả lớp, hai phương thức chạy đồng thời đẩy/lấy ngăn xếp chồng chéo và {@code afterAll} có thể ném NoSuchElementException
 * (lỗi chập chờn quan sát được ở parallelism 48). Chỉ lớp này tuần tự nội bộ, vẫn chạy đồng thời với các lớp khác.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(OutputCaptureExtension.class)
class PostgresWarnIT {

    @Configuration
    static class NoDatabase {
    }

    @SpringBootTest(classes = NoDatabase.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = "ptest.db.postgres.max-connections=1")
    @ParallelSpringTest(context = "pg-warn")
    static class TooFewConnections {
    }

    @SpringBootTest(classes = NoDatabase.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = "ptest.db.postgres.max-connections=100000000")
    @ParallelSpringTest(context = "pg-nowarn")
    static class PlentyOfConnections {
    }

    @BeforeEach
    void onlyInPostgresMode() {
        assumeTrue("postgres".equals(System.getProperty("ptest.db.mode")), "the WARN belongs to the postgres provisioner");
    }

    /**
     * Nạp context của lớp phụ và trả về nó. WARN chỉ phát khi context được TẠO MỚI (customizer chạy lúc tạo), còn cache context của Spring là
     * toàn JVM: chạy lại test trong cùng JVM (rerun, lặp lớp) sẽ trúng cache và không còn WARN. Nên gỡ context đã cache (nếu có) trước khi nạp để test
     * không phụ thuộc thứ tự/lần chạy; lần chạy đầu không có gì để gỡ nên số context tạo ra vẫn đúng 1 cho mỗi khóa.
     */
    private static ApplicationContext load(Class<?> helper) {
        TestContextManager manager = new TestContextManager(helper);
        manager.getTestContext().markApplicationContextDirty(DirtiesContext.HierarchyMode.EXHAUSTIVE);
        return manager.getTestContext().getApplicationContext();
    }

    @Test
    void warnsWhenContextsTimesPoolExceedsServerMaxConnections(CapturedOutput output) {
        ApplicationContext context = load(TooFewConnections.class);

        assertThat(ParallelTest.contextKey(context)).startsWith("pg-warn@");
        assertThat(output.getAll()).contains("may exceed Postgres max_connections=1;").contains("ptest.db.postgres.max-connections");
    }

    @Test
    void staysSilentWhenTheBudgetFits(CapturedOutput output) {
        ApplicationContext context = load(PlentyOfConnections.class);

        // điều kiện dương: context THẬT SỰ được nạp bởi thư viện (khóa riêng của nó), nên việc không có WARN là kết quả của kiểm tra chứ không phải do không nạp gì
        assertThat(ParallelTest.contextKey(context)).startsWith("pg-nowarn@");
        assertThat(output.getAll()).doesNotContain("may exceed Postgres max_connections=100000000;");
    }
}
