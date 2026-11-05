package com.example.parallel.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field of a test class that receives a FRESH Mockito mock for every test method.
 * Requires {@link PerTestMockExtension} and a {@link MockRouter#routerFor} bean of the same type
 * in the Spring context. Unlike @MockitoBean, the mock is never shared between concurrent tests.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PerTestMock {
}
