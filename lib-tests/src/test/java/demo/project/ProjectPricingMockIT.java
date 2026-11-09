package demo.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** PricingService MOCK; cùng context với lớp REAL và SPY của cùng bean. */
class ProjectPricingMockIT extends ProjectBaseIT {

    @PerTestMock PricingService mock;

    @Test
    void mockedBean() {
        when(mock.price(BigDecimal.TEN)).thenReturn(new BigDecimal("7"));
        meetInSameContext("same-bean", 3);

        assertThat(pricing.price(BigDecimal.TEN)).isEqualByComparingTo("7");

        verify(mock).price(BigDecimal.TEN);
    }
}
