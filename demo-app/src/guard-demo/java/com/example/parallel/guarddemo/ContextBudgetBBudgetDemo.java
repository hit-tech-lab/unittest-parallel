package com.example.parallel.guarddemo;

import com.hittech.paralleltest.annotation.IsolatedContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * NEGATIVE CONTROL (expected: exactly one of A/B fails with PT10 when run with max-contexts=1).
 * Two legitimate-looking configurations differing by one property = two contexts.
 */
@IsolatedContext(reason = "negative control for the context budget")
@SpringBootTest(properties = "demo.variant=B")
@ActiveProfiles("test")
class ContextBudgetBBudgetDemo {

    @Test
    void anything() {
    }
}
