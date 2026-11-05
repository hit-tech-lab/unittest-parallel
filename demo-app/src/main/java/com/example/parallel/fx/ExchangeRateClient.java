package com.example.parallel.fx;

import java.math.BigDecimal;

/** External dependency (a remote FX service). Always replaced by a mock/fake in tests. */
public interface ExchangeRateClient {
    BigDecimal rate(String fromCurrency, String toCurrency);
}
