package demo.project;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** Bean thật: REAL ở lớp này, SPY ở lớp khác, MOCK ở lớp khác nữa, cùng một context. {@code this.fee()} đi qua spy. */
@Service
public class PricingService {

    public BigDecimal price(BigDecimal base) {
        return base.multiply(BigDecimal.valueOf(2)).add(fee());
    }

    public BigDecimal fee() {
        return BigDecimal.ONE;
    }
}
