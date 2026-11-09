package demo.core;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Lớp final, tiêm theo lớp cụ thể. */
@Component
public final class FinalPricer {
    public BigDecimal price(BigDecimal base) {
        return base.multiply(new BigDecimal("1.2"));
    }

    /** Gọi nội bộ {@code this.price}: dùng để kiểm tra spy thấy lời gọi nội bộ trên lớp final. */
    public BigDecimal priceWithFee(BigDecimal base) {
        return price(base).add(BigDecimal.ONE);
    }
}
