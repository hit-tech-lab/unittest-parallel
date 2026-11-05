package com.hittech.paralleltest.mock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declared on a {@code @SharedContextBase}: the given bean types are replaced, in the shared context,
 * by a @Primary router bean. Tests then declare {@code @PerTestMock} fields of these types.
 * Part of the context configuration -> only allowed on the base class (rule PT02).
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RoutedMocks {
    Class<?>[] value();
}
