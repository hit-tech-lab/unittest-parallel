package demo.core;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component("primaryFx")
public class PrimaryFx implements ExchangeRateClient {
    /** Đếm lời gọi thật, dùng chung mọi test (state singleton). */
    public static final AtomicInteger CALLS = new AtomicInteger();

    @Override
    public BigDecimal rate(String from, String to) {
        CALLS.incrementAndGet();
        return new BigDecimal("1.10");
    }
}
