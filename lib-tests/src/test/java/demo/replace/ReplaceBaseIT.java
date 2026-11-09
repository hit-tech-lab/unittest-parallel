package demo.replace;

import static org.assertj.core.api.Assertions.assertThat;

import demo.replace.ReplaceConfig.Broker;
import demo.support.Rendezvous;
import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.ParallelTest;
import com.hit.ptest.PerTestMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Lớp cơ sở khai {@code @PerTestMock} cấp class như {@code @MockBean} ở lớp base: THAY định nghĩa của bean quét được và bean {@code @Bean} có @Qualifier. Bean thật
 * (ném lỗi nếu khởi tạo) không bao giờ được tạo; bean "safe" cùng kiểu không bị thay và vẫn là bean thật.
 */
@PerTestMock(ConnectedClient.class)
@PerTestMock(name = "remote", classes = Broker.class)
@SpringBootTest(classes = ReplaceConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "replace")
public abstract class ReplaceBaseIT {

    @Autowired protected ApplicationContext ctx;
    @Autowired protected Dispatcher dispatcher;

    protected void meetInSameContext(String group) {
        Rendezvous.share(group + ":ctx", ParallelTest.contextKey(ctx));
        Rendezvous.meet(group, 2);
        assertThat(Rendezvous.shared(group + ":ctx")).hasSize(1);
        assertThat(ConnectedClient.CONNECTED).hasValue(0);
        assertThat(ReplaceConfig.FORBIDDEN_CREATED).hasValue(0);
    }
}
