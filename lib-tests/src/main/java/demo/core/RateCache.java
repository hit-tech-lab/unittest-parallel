package demo.core;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class RateCache {

    private final ExchangeRateClient primary;
    private final ConcurrentMap<String, AtomicInteger> computations = new ConcurrentHashMap<>();

    public RateCache(@Qualifier("primaryFx") ExchangeRateClient primary) {
        this.primary = primary;
    }

    @Cacheable("rates")
    public BigDecimal cachedRate(String from, String to) {
        computations.computeIfAbsent(from + "|" + to, k -> new AtomicInteger()).incrementAndGet();
        return primary.rate(from, to);
    }

    /** Hai lời gọi nội bộ {@code this.cachedRate}: không qua proxy cache (như mọi lần tự gọi), nhưng qua spy. */
    public BigDecimal cachedRateTwice(String from, String to) {
        return cachedRate(from, to).add(cachedRate(from, to));
    }

    public int computations(String from, String to) {
        AtomicInteger n = computations.get(from + "|" + to);
        return n == null ? 0 : n.get();
    }
}
