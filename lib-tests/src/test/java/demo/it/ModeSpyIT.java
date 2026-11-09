package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.hit.ptest.PerTestSpy;
import org.springframework.beans.factory.annotation.Qualifier;
import demo.core.ExchangeRateClient;
import demo.core.PrimaryFx;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ModeSpyIT extends MainIT {

    @PerTestSpy @Qualifier("primaryFx") ExchangeRateClient spy;

    @Test
    void spyCallsRealAndCountsOnlyOwnCalls() {
        doReturn(new BigDecimal("3.30")).when(spy).rate("USD", "JPY");
        int realBefore = PrimaryFx.CALLS.get();
        meetInSameContext("mix", 6);
        assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("1.10");   // that
        assertThat(primaryFx.rate("USD", "JPY")).isEqualByComparingTo("3.30");   // stub một phần
        verify(spy, times(1)).rate("USD", "EUR");                                // chỉ đếm lời gọi của test này
        assertThat(PrimaryFx.CALLS.get()).isGreaterThan(realBefore);
    }
}
