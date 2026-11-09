package demo.project;

import static org.assertj.core.api.Assertions.assertThat;

import demo.support.Rendezvous;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.ParallelTest;
import io.hit.ptest.PerTestMock;
import io.hit.ptest.PerTestMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Mô phỏng lớp cơ sở của một ứng dụng nhiều module: 10 cổng ra ngoài không có bản thật + 3 bean cùng kiểu {@link PaymentGateway} chọn theo
 * {@code name}, tất cả khai CẤP CLASS như {@code @MockBean}: thư viện tạo bean mock (context không có) và đó là một phần khóa
 * context. Lớp con khai thêm field {@code @PerTestMock} chỉ ở chỗ cần stub. MỌI lớp con (mock/spy/real khác nhau cho PricingService,
 * ExceptionsProvider...) dùng chung MỘT context: report phải ghi đúng 1 context "project".
 */
@PerTestMocks({
        @PerTestMock(Ports.Mailer.class),
        @PerTestMock(Ports.SmsSender.class),
        @PerTestMock(Ports.DocumentDb.class),
        @PerTestMock(Ports.Cache.class),
        @PerTestMock(Ports.SearchIndex.class),
        @PerTestMock(Ports.FileStore.class),
        @PerTestMock(Ports.FeatureFlags.class),
        @PerTestMock(Ports.MetricsSink.class),
        @PerTestMock(Ports.AuditSink.class),
        @PerTestMock(Ports.MessageQueue.class),
        @PerTestMock(name = "gatewayA", classes = PaymentGateway.class),
        @PerTestMock(name = "gatewayB", classes = PaymentGateway.class),
        @PerTestMock(name = "gatewayC", classes = PaymentGateway.class),
})
@SpringBootTest(classes = ProjectConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "project", wrap = {"*", "limitProperties"})
public abstract class ProjectBaseIT {

    @Autowired protected ApplicationContext ctx;
    @Autowired protected CheckoutService checkout;
    @Autowired protected ReportService report;
    @Autowired protected PricingService pricing;
    @Autowired protected ErrorTranslator translator;
    @Autowired protected ProjectBootstrap bootstrap;

    /** Hẹn nhiều test (có thể khác lớp) và kiểm tra tất cả dùng CÙNG một ApplicationContext (cùng khóa context). */
    protected void meetInSameContext(String group, int parties) {
        Rendezvous.share(group + ":ctx", ParallelTest.contextKey(ctx));
        Rendezvous.meet(group, parties);
        assertThat(Rendezvous.shared(group + ":ctx")).as("all tests of group %s share one context", group).hasSize(1);
    }
}
