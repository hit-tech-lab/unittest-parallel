package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import demo.core.ExchangeRateClient;
import demo.support.Rendezvous;
import io.hit.ptest.ParallelTest;
import io.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

/** @Nested chạy trong pool song song: mỗi test vẫn thấy mock riêng, class binding được khôi phục (xem thêm BindingLifecycleTest). */
class NestedBindingIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient outerMock;

    @Test
    void outerTest() {
        when(outerMock.rate("USD", "EUR")).thenReturn(new BigDecimal("11"));
        Rendezvous.meet("nested", 3);
        assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("11");
        assertThat(ParallelTest.hasBinding()).isTrue();
    }

    @Nested
    class Inner {
        @PerTestMock @Qualifier("primaryFx") ExchangeRateClient innerMock;

        @Test
        void innerTestOne() {
            when(innerMock.rate("USD", "EUR")).thenReturn(new BigDecimal("22"));
            Rendezvous.meet("nested", 3);
            assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("22");
        }

        @Test
        void innerTestTwo() {
            when(innerMock.rate("USD", "EUR")).thenReturn(new BigDecimal("33"));
            Rendezvous.meet("nested", 3);
            assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("33");
        }
    }
}
