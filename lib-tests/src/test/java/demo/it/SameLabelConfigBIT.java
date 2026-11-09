package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.DemoApp;
import demo.support.Rendezvous;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.ParallelTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Tên chỉ là nhãn: cùng tên "label", KHÁC cấu hình Spring => hai context riêng với khóa khác nhau, không lỗi. */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "label.config=b")
@ParallelSpringTest(context = "label")
class SameLabelConfigBIT {

    @Autowired ApplicationContext ctx;

    @Test
    void sameNameDifferentConfigurationIsASeparateContext() {
        Rendezvous.share("label:keys", ParallelTest.contextKey(ctx));
        Rendezvous.meet("label", 2);
        assertThat(Rendezvous.shared("label:keys")).hasSize(2).allMatch(k -> k.startsWith("label@"));
    }
}
