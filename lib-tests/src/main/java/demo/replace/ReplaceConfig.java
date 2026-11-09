package demo.replace;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Cấu hình có các bean "không được phép khởi tạo thật" trong test (kết nối ngoài lúc khởi động): bean @Component quét được,
 * bean @Bean method, hai bean cùng kiểu chọn bằng @Qualifier/@Primary. Test thay định nghĩa bằng {@code @PerTestMock} cấp class.
 */
@Configuration
@ComponentScan(basePackageClasses = ReplaceConfig.class)
public class ReplaceConfig {

    /** Số bean "thật" bị khởi tạo: phải là 0 với bean bị thay. */
    public static final AtomicInteger FORBIDDEN_CREATED = new AtomicInteger();

    public interface Broker {
        String send(String message);
    }

    public static class ForbiddenBroker implements Broker {
        public ForbiddenBroker() {
            FORBIDDEN_CREATED.incrementAndGet();
            throw new IllegalStateException("real broker connected at startup");
        }

        @Override
        public String send(String message) {
            return "real:" + message;
        }
    }

    public static class SafeBroker implements Broker {
        @Override
        public String send(String message) {
            return "safe:" + message;
        }
    }

    @Bean
    @Primary
    @Qualifier("safe")
    Broker safeBroker() {
        return new SafeBroker();
    }

    @Bean
    @Qualifier("remote")
    Broker remoteBroker() {
        return new ForbiddenBroker();
    }
}
