package demo.core;

import java.math.BigDecimal;

/** Phụ thuộc ngoài; có hai bean cùng kiểu (primaryFx, backupFx). */
public interface ExchangeRateClient {
    BigDecimal rate(String from, String to);
}
