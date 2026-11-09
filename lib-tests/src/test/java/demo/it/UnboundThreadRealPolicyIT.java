package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hit.ptest.PerTestMock;
import demo.core.DemoApp;
import demo.core.ExchangeRateClient;
import com.hit.ptest.ParallelSpringTest;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/** Chính sách REAL tự chọn: thread không binding gọi bean THẬT (có log cảnh báo), mock của test không thấy lời gọi đó. */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "ptest.unbound-policy=real")
@ParallelSpringTest(context = "policy-real")
class UnboundThreadRealPolicyIT {

    @Autowired @Qualifier("primaryFx") ExchangeRateClient primaryFx;
    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    void unboundThreadGetsRealBean() throws Exception {
        assertThat(CompletableFuture.supplyAsync(() -> primaryFx.rate("USD", "EUR")).get()).isEqualByComparingTo("1.10");
        verifyNoInteractions(mock);
    }
}
