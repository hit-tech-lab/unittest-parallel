package com.hittech.paralleltest.data;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-test unique values. Tests sharing a database must never rely on fixed keys: unique
 * constraints collide between concurrent transactions even if both roll back.
 */
public final class TestData {

    private static final AtomicLong SEQ = new AtomicLong();

    private TestData() {
    }

    /** {@code prefix-<uuid>}: globally unique (across forks and machines sharing a DB). */
    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /** Upper-case code of {@code length} letters, unique within this JVM (until 26^length values). */
    public static String uniqueCode(int length) {
        long n = SEQ.getAndIncrement();
        char[] c = new char[length];
        for (int i = length - 1; i >= 0; i--) {
            c[i] = (char) ('A' + n % 26);
            n /= 26;
        }
        return new String(c);
    }
}
