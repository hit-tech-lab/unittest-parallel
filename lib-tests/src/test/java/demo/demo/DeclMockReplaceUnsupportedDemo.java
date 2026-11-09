package demo.demo;

import demo.project.Ports;
import demo.project.ProjectConfig;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;

/**
 * Negative control: bean được đăng ký thẳng như singleton có sẵn (initializer, không có bean definition) rồi bị {@code @PerTestMock} cấp
 * class nhắm tới: không có định nghĩa để thay => MOCK_REPLACE_UNSUPPORTED.
 */
@PerTestMock(name = "manualMailer", classes = Ports.Mailer.class)
@SpringBootTest(classes = ProjectConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = DeclMockReplaceUnsupportedDemo.RegisterSingleton.class)
@ParallelSpringTest(context = "replace-unsupported")
class DeclMockReplaceUnsupportedDemo {

    static class RegisterSingleton implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            context.getBeanFactory().registerSingleton("manualMailer", (Ports.Mailer) to -> "real");
        }
    }

    @Test
    void test() {
    }
}
