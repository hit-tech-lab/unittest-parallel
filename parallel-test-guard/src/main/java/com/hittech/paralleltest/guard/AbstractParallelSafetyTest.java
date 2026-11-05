package com.hittech.paralleltest.guard;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Extend once in the test source set (e.g. {@code class ParallelSafetyTest extends AbstractParallelSafetyTest {}}):
 * a fast, Spring-free unit test that lists EVERY parallel-safety violation of the test code base.
 * The runtime listener enforces the same rules per class; this test reports them all together and
 * also covers test helper classes.
 */
public abstract class AbstractParallelSafetyTest {

    @Test
    void testCodeIsParallelSafe() {
        List<Violation> violations = ParallelSafetyScanner.scan(getClass());
        if (!violations.isEmpty()) {
            throw new ParallelGuardViolation("Test code base is not parallel-safe", violations);
        }
    }
}
