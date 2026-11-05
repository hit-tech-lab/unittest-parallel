package com.hittech.paralleltest.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks THE base class that owns a Spring test configuration. Every Spring test class must extend
 * one (rule PT01), and classes below it must not change the configuration (rule PT02), so all of
 * them share one cached ApplicationContext and can run concurrently.
 *
 * <pre>
 * &#64;SharedContextBase
 * &#64;SpringBootTest(webEnvironment = RANDOM_PORT)
 * &#64;ActiveProfiles("test")
 * &#64;RoutedMocks(ExchangeRateClient.class)
 * public abstract class AbstractIntegrationTest { ... }
 * </pre>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SharedContextBase {
}
