package org.springframework.test.context.bean.override.mockito;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Chỉ để biên dịch fixture của guard trên Spring 6.1 (Boot 3.3.5): annotation thật có từ Spring 6.2. Guard nhận diện theo tên đầy đủ. */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MockitoBean {
}
