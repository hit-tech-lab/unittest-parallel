package demo.demo;

import demo.core.DemoApp;
import io.hit.ptest.ParallelSpringTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Guard PT06: field static không final bị báo; field static final (bất biến) là control, KHÔNG được báo. */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "guard-static")
class GuardStaticFieldDemo {

    static int sharedCounter;
    static final List<String> IMMUTABLE_CONTROL = List.of("ok");

    @Test
    void usesStaticState() {
        sharedCounter++;
    }
}
