package demo.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.DemoApp;
import demo.project.Ports;
import demo.support.Rendezvous;
import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.ParallelTest;
import com.hit.ptest.PerTestMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Tầng 1 của chuỗi kế thừa 3 tầng, mỗi tầng khai thêm một mock cấp class: A (Mailer) -> B (+FeatureFlags) -> C (+MetricsSink).
 * Khóa context hợp nhất cả ba; hai lớp cuối dưới C dùng chung một context, lớp chỉ dưới B ra context khác (report: 2 context "tiers").
 */
@PerTestMock(Ports.Mailer.class)
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "tiers")
abstract class TierABase {

    @Autowired ApplicationContext ctx;

    /** Hẹn 3 lớp lá, rồi kiểm: hai lá dưới tầng C cùng context, lá dưới tầng B khác. */
    void probe(String role) {
        Rendezvous.share("tiers:" + role, ParallelTest.contextKey(ctx));
        Rendezvous.meet("tiers", 3);
        assertThat(Rendezvous.shared("tiers:c1")).hasSize(1);
        assertThat(Rendezvous.shared("tiers:c2")).isEqualTo(Rendezvous.shared("tiers:c1"));
        assertThat(Rendezvous.shared("tiers:b")).hasSize(1).doesNotContainAnyElementsOf(Rendezvous.shared("tiers:c1"));
        assertThat(ctx.containsBean("mailer")).isTrue();
    }
}
