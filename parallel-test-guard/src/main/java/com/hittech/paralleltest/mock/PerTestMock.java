package com.hittech.paralleltest.mock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a FRESH Mockito mock for every test method, replacing {@code @MockitoBean} in parallel suites.
 * The bean in the shared context is a router (declared with {@link RoutedMocks} on the base class)
 * that forwards each call to the mock of the test that made it -- stubbing, verify() and reset
 * never cross concurrent tests.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PerTestMock {
}
