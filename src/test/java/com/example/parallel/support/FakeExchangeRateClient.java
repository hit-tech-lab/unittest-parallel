package com.example.parallel.support;

import com.example.parallel.fx.ExchangeRateClient;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe fake used instead of a shared @MockitoBean in the common integration-test context.
 * Each test registers rates for its OWN currency pair (see TestData.uniqueCurrency), so concurrent
 * tests never overwrite each other's "stubbing", and nothing needs to be reset between tests.
 */
public class FakeExchangeRateClient implements ExchangeRateClient {

    private final Map<String, BigDecimal> rates = new ConcurrentHashMap<>();
    private final Map<String, Integer> calls = new ConcurrentHashMap<>();

    public void register(String from, String to, BigDecimal rate) {
        rates.put(key(from, to), rate);
    }

    public int calls(String from, String to) {
        return calls.getOrDefault(key(from, to), 0);
    }

    @Override
    public BigDecimal rate(String from, String to) {
        calls.merge(key(from, to), 1, Integer::sum);
        BigDecimal rate = rates.get(key(from, to));
        if (rate == null) {
            throw new IllegalStateException("No fake rate registered for " + key(from, to));
        }
        return rate;
    }

    private static String key(String from, String to) {
        return from + "->" + to;
    }
}
