package com.hit.ptest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Spy chuẩn Mockito RIÊNG của test đang chạy trên bean THẬT đã có trong context, như {@code @MockitoSpyBean} nhưng chỉ trong
 * test này. Cách chọn bean, phạm vi theo test và ràng buộc vòng đời giống {@link PerTestMock}; ngữ nghĩa spy (nằm bên trong proxy
 * AOP, {@code this.x()} qua spy, sao chép field): xem {@code com.hit.ptest.intercept.InterceptMode#SPY}.
 *
 * <p>Vị trí đặt: field (handle), tham số của method test (ghi đè field cùng bean cho method đó), hoặc CẤP CLASS
 * ({@code @PerTestSpy(A.class)}, {@code @PerTestSpies({...})} hoặc lặp trực tiếp): mỗi test của lớp có sẵn một spy riêng của bean đó;
 * lấy handle bằng field cùng bean ở lớp con. Khai báo cấp class KHÔNG đổi khóa context (bean thật đã có và đã được bọc).
 *
 * <pre>{@code
 * @PerTestSpy(PaymentGateway.class)
 * @ParallelSpringTest
 * abstract class SpyBase { }
 *
 * class PaymentTest extends SpyBase {
 *     @PerTestSpy PaymentGateway manager;      // handle của spy riêng của test này
 * }
 * }</pre>
 *
 * <p>Bean không tồn tại, hoặc đã bị {@link PerTestMock} cấp class thay/tạo, thì không có bản thật để spy:
 * {@code SPY_NO_BEAN}.
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(PerTestSpies.class)
public @interface PerTestSpy {

    /** Kiểu bean cần spy (CHỈ cấp class). Cùng nghĩa với {@link #classes()}; hai thuộc tính được gộp. */
    Class<?>[] value() default {};

    /** Kiểu bean cần spy (CHỈ cấp class). */
    Class<?>[] classes() default {};

    /**
     * Cấp class: tên bean (đi với đúng một kiểu). Field/tham số: tên bean (hoặc {@code @Qualifier}) cần chọn, thay cho
     * {@code @Qualifier} và ưu tiên hơn; dùng cho THAM SỐ của method test vì {@code @Qualifier} ở đó gây xung đột ParameterResolver.
     */
    String name() default "";
}
