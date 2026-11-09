package io.hit.ptest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Miễn trừ CÓ KIỂM SOÁT cho các luật guard của một lớp test: luật tĩnh (PT01, PT03, PT06, PT07, PT08, PT13), PT11 (pool
 * của context) và PT12 (SQL cả bảng; chỉ có hiệu lực cho SQL chạy trên thread đã gắn với test của lớp đó qua binding, vì
 * mỗi context có DB riêng nên chỉ nên dùng khi DB chỉ phục vụ lớp đó). Bắt buộc ghi {@link #reason() lý do} để người
 * review thấy. PT10 (ngân sách context) không thuộc một lớp: dùng {@code ptest.guard.PT10=warn|off}. Có hiệu lực cho lớp khai báo, lớp con
 * và lớp {@code @Nested} bên trong.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface SuppressGuard {

    /** Mã luật, vd {@code "PT07"}. */
    String[] rules();

    /** Lý do (bắt buộc, không được rỗng). */
    String reason();
}
