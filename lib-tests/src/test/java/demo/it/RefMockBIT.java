package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import demo.core.AuditLog;
import demo.core.DemoApp;
import demo.core.ExchangeRateClient;
import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Mỗi lớp tự gắn {@code @ParallelSpringTest} (cùng cấu hình Spring với {@link MainIT}) kèm {@code @PerTestMock} cho bean ĐÃ có
 * nhưng tập mock khác nhau: field không cờ không thuộc khóa context nên cả hai dùng chung context "main" (check_reports.py
 * đòi đúng 1 context "main").
 */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.error.include-message=always", "spring.web.error.include-message=always"})
@ParallelSpringTest(context = "main")
class RefMockBIT {

    @PerTestMock @Qualifier("backupFx") ExchangeRateClient mock;
    @PerTestMock AuditLog audit;
    @Autowired ApplicationContext ctx;

    @Test
    void sharesTheMainContextDespiteDifferentMocks() {
        when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("1.5"));
        assertThat(ctx.getBean("backupFx", ExchangeRateClient.class).rate("USD", "EUR")).isEqualByComparingTo("1.5");
    }
}
