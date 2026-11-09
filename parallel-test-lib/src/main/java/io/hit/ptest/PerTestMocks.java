package io.hit.ptest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Gộp nhiều {@link PerTestMock} cấp class, như {@code @MockBeans}; lặp trực tiếp {@code @PerTestMock} cũng được. */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PerTestMocks {

    PerTestMock[] value();
}
