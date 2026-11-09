package demo.mockbean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

/** Cấu hình CÓ sẵn ba bean (qua {@code @Bean}, giữ {@code @Primary}/{@code @Qualifier}): test khai cấp class nên định nghĩa bị thay bằng mock. */
@Configuration
@EnableAsync
@EnableConfigurationProperties
public class ReplacedConfig {

    @Bean
    Helper helper() {
        return new Helper();
    }

    @Bean
    @Primary
    @Qualifier("codes")
    CodeProvider codeProvider() {
        return new CodeProvider();
    }

    @Bean
    SettableLimits settableLimits() {
        return new SettableLimits();
    }

    @Bean
    Wired wired() {
        return new Wired();
    }
}
