package com.example.parallel.unit;

import com.example.parallel.support.Rendezvous;
import org.junit.jupiter.api.Test;

/**
 * Positive proof that unit tests really run in parallel: both methods must be inside
 * the rendezvous at the same time, otherwise they time out and FAIL.
 * (If someone disables parallel execution, this test breaks instead of silently passing.)
 */
class UnitParallelismSanityTest {

    @Test
    void first() {
        Rendezvous.await("unit-sanity", 2);
    }

    @Test
    void second() {
        Rendezvous.await("unit-sanity", 2);
    }
}
