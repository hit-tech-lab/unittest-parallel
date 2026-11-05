package com.example.parallel.support;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-test unique data. The #1 rule for tests sharing a database or a singleton:
 * never rely on fixed keys or global counts -- only on data the test created itself.
 */
public final class TestData {

    // Starts at "BAA" so generated codes never clash with real ones used in tests (USD, EUR, ...).
    private static final AtomicInteger CURRENCY_SEQ = new AtomicInteger(26 * 26);

    private TestData() {
    }

    public static String uniqueOwner(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /** A 3-letter code unique within this JVM -> unique keys for the shared fake FX client. */
    public static String uniqueCurrency() {
        int n = CURRENCY_SEQ.getAndIncrement();
        char[] c = new char[3];
        for (int i = 2; i >= 0; i--) {
            c[i] = (char) ('A' + n % 26);
            n /= 26;
        }
        return new String(c);
    }
}
