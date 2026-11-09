package demo.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.hit.ptest.PerTestSpy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** PricingService SPY: stub một phần, {@code this.fee()} bên trong price() đi qua spy; cùng context với lớp REAL và MOCK. */
class ProjectPricingSpyIT extends ProjectBaseIT {

    @PerTestSpy PricingService spy;

    @Test
    void spyBean() {
        doReturn(new BigDecimal("5")).when(spy).fee();
        meetInSameContext("same-bean", 3);

        assertThat(pricing.price(BigDecimal.TEN)).isEqualByComparingTo("25");

        verify(spy).fee();
    }
}
