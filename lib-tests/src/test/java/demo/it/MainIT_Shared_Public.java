package demo.it;

import demo.core.DemoApp;
import demo.support.SharedStateBase;
import com.hit.ptest.ParallelSpringTest;
import org.springframework.boot.test.context.SpringBootTest;

/** Cùng cấu hình với MainIT (cùng tên "main") để dùng chung context; cho phép kế thừa SharedStateBase. */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.error.include-message=always", "spring.web.error.include-message=always"})
@ParallelSpringTest(context = "main")
public abstract class MainIT_Shared_Public extends SharedStateBase {
}
