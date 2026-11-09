package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.hit.ptest.PerTestMock;
import org.springframework.beans.factory.annotation.Qualifier;
import demo.core.ExchangeRateClient;
import demo.core.PrimaryFx;
import demo.core.Warmup;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** E: gọi bean can thiệp trong @PostConstruct lúc khởi động = REAL (chưa có binding), mock của test sau đó không liên quan. */
class PostConstructIT extends MainIT {

    @Autowired Warmup warmup;
    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    void startupCallWasRealAndMockSawNothing() {
        when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("99"));
        assertThat(warmup.startupRate()).isEqualByComparingTo("1.10");
        assertThat(PrimaryFx.CALLS.get()).isGreaterThanOrEqualTo(1);
        verifyNoInteractions(mock);
    }
}
