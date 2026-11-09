package demo.project;

import java.math.BigDecimal;

/** Ba bean cùng kiểu (gatewayA/B/C) không có bản thật: chọn bằng {@code @Qualifier}. */
public interface PaymentGateway {
    String charge(String order, BigDecimal amount);
}
