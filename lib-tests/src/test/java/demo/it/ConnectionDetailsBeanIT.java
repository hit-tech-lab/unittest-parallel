package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.DemoApp;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.SuppressGuard;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.ClassUtils;

/**
 * Người dùng đăng ký bean {@code JdbcConnectionDetails} (cơ chế của @ServiceConnection/Docker Compose): thư viện KHÔNG cấp DB thừa.
 * Lớp {@code JdbcConnectionDetails} nằm ở package khác nhau trên Boot 3 và Boot 4 nên bean được dựng bằng reflection để cùng một
 * nguồn chạy được trên cả hai ma trận; report phải ghi context này không có DB do thư viện cấp (xem check_reports.py).
 */
@SuppressGuard(rules = "PT11", reason = "single test method: the user-owned connection keeps the Hikari default pool")
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = ConnectionDetailsBeanIT.DetailsInitializer.class)
@ParallelSpringTest(context = "details-bean")
class ConnectionDetailsBeanIT {

    private static final String URL = "jdbc:h2:mem:details_bean_owned;DB_CLOSE_DELAY=-1";

    /** Đăng ký bean trước khi refresh (như ContextCustomizer của @ServiceConnection), trước khi bất kỳ điều kiện nào hỏi spring.datasource.url. */
    static class DetailsInitializer implements ApplicationContextInitializer<GenericApplicationContext> {
        @Override
        public void initialize(GenericApplicationContext context) {
            Class<?> type = Stream.of("org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails",
                            "org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails")
                    .filter(n -> ClassUtils.isPresent(n, null)).map(n -> ClassUtils.resolveClassName(n, null)).findFirst().orElseThrow();
            Object details = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
                return switch (method.getName()) {
                    case "getJdbcUrl" -> URL;
                    case "getUsername" -> "sa";
                    case "getPassword" -> "";
                    case "toString" -> "userDetails";
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            });
            RootBeanDefinition definition = new RootBeanDefinition();
            definition.setBeanClass(type);
            definition.setInstanceSupplier(() -> details);
            definition.setPrimary(true);
            context.registerBeanDefinition("userConnectionDetailsBean", definition);
        }
    }

    @Autowired DataSource dataSource;

    @Test
    void connectionIsTheUsersAndTheLibraryProvisionedNothing() throws Exception {
        try (var c = dataSource.getConnection()) {
            assertThat(c.getMetaData().getURL()).startsWith("jdbc:h2:mem:details_bean_owned").doesNotContain("ptest_f");
        }
    }
}
