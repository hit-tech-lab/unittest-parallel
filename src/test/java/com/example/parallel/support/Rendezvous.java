package com.example.parallel.support;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * Forces a deterministic interleaving between tests that run concurrently:
 * every party blocks until all {@code parties} reached the same named point.
 * If the tests are NOT running in parallel, await() times out and the test fails loudly.
 */
public final class Rendezvous {

    public static final String NOT_CONCURRENT = "TESTS_DID_NOT_RUN_CONCURRENTLY";

    private static final Map<String, CyclicBarrier> BARRIERS = new ConcurrentHashMap<>();

    private Rendezvous() {
    }

    public static void await(String name, int parties) {
        CyclicBarrier barrier = BARRIERS.computeIfAbsent(name, n -> new CyclicBarrier(parties));
        try {
            barrier.await(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail(NOT_CONCURRENT + ": rendezvous '" + name + "' not reached by " + parties + " tests (" + e + ")");
        }
    }
}
