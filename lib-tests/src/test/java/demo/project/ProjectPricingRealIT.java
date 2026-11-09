package demo.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** PricingService REAL (không khai báo gì) trong cùng context với lớp SPY và lớp MOCK của cùng bean. */
class ProjectPricingRealIT extends ProjectBaseIT {

    @Test
    void realBean() {
        meetInSameContext("same-bean", 3);

        assertThat(pricing.price(BigDecimal.TEN)).isEqualByComparingTo("21");
    }
}
