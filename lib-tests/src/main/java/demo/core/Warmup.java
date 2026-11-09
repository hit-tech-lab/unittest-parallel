package demo.core;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Gọi bean can thiệp ngay lúc khởi động (@PostConstruct): lúc đó binding không có, phải là bean thật. */
@Component
public class Warmup {

    private final ExchangeRateClient primary;
    private BigDecimal startupRate;

    public Warmup(@Qualifier("primaryFx") ExchangeRateClient primary) {
        this.primary = primary;
    }

    @PostConstruct
    void init() {
        startupRate = primary.rate("USD", "EUR");
    }

    public BigDecimal startupRate() {
        return startupRate;
    }
}
