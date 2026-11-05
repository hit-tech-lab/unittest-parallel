package com.hittech.paralleltest.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explicit, reviewed exception: this test class may define its OWN context configuration instead of
 * extending a {@link SharedContextBase} (waives PT01/PT02). The extra context still counts against
 * the context budget (PT10). A reason is mandatory so the exception is visible in code review.
 *
 * <p>It does NOT allow shared mutable state: for @MockitoBean, @DirtiesContext, static state or
 * whole-table operations, use JUnit's {@code @Isolated} (runs the class alone).
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface IsolatedContext {
    String reason();
}
