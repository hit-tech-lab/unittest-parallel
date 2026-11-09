package demo.mockbean;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Cấu hình KHÔNG có CodeProvider, SettableLimits, Wired: test khai chúng cấp class nên thư viện tạo bean mock mới. */
@Configuration
@EnableAsync
@EnableConfigurationProperties
public class MockOnlyConfig {

    @Bean
    Helper helper() {
        return new Helper();
    }
}
