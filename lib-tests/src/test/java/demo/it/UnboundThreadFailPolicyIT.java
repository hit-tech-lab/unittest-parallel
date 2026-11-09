package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import demo.core.DemoApp;
import demo.core.ExchangeRateClient;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.ParallelTestException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/** Chính sách FAIL: mọi thread không binding gọi bean can thiệp đều lỗi, kể cả khi không test nào khai báo. */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "ptest.unbound-policy=fail")
@ParallelSpringTest(context = "policy-fail")
class UnboundThreadFailPolicyIT {

    @Autowired @Qualifier("primaryFx") ExchangeRateClient primaryFx;

    @Test
    void unboundThreadAlwaysFails() {
        assertThatThrownBy(() -> CompletableFuture.supplyAsync(() -> primaryFx.rate("USD", "EUR")).get())
                .isInstanceOf(ExecutionException.class).hasRootCauseInstanceOf(ParallelTestException.UnboundThread.class);
        assertThat(primaryFx.rate("USD", "EUR")).as("the test thread itself is bound").isEqualByComparingTo("1.10");
    }
}
