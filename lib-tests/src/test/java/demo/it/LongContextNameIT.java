package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.Account;
import demo.core.AccountRepository;
import demo.core.DemoApp;
import com.hit.ptest.ParallelSpringTest;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** Tên context dài: phần duy nhất của tên DB đứng đầu nên không bị Postgres cắt (63 ký tự) thành trùng tên; DB vẫn dùng được. */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "a-context-name-much-longer-than-sixty-three-characters-so-that-postgres-would-truncate-the-identifier")
class LongContextNameIT {

    @Autowired AccountRepository accounts;
    @Autowired Environment env;

    @Test
    void databaseNameStaysWithinTheIdentifierLimitAndWorks() {
        String url = env.getProperty("spring.datasource.url");
        assertThat(Arrays.stream(url.split("[/?=:;,]")).filter(t -> t.startsWith("ptest_")))
                .isNotEmpty().allSatisfy(t -> assertThat(t).hasSizeLessThanOrEqualTo(63));
        Account saved = accounts.save(new Account("long-" + UUID.randomUUID(), "USD", BigDecimal.TEN));
        assertThat(accounts.findById(saved.getId())).isPresent();
    }
}
