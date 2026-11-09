package demo.it;

import demo.core.DemoApp;
import com.hit.ptest.ParallelSpringTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** B: cấu trúc khác (bean ExtraBean tồn tại) => context profile riêng, khai báo có chủ ý bằng tên "alt". */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.extra-bean=true")
@ParallelSpringTest(context = "alt")
abstract class AltBaseIT {
    @Autowired protected ApplicationContext ctx;
}
