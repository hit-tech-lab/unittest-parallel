package io.hit.ptest.context;

import io.hit.ptest.intercept.InterceptMode;

import java.lang.reflect.Field;
import org.springframework.core.ResolvableType;

/**
 * Một yêu cầu mock/spy theo từng test: field, tham số hoặc khai báo spy cấp class mang {@code @PerTestMock}/{@code @PerTestSpy}.
 *
 * @param field field cần tiêm handle, null nếu là tham số hoặc khai báo cấp class
 * @param where vị trí để in thông điệp (lớp.field hoặc lớp.method(tham số))
 * @param member tên field/tham số (dùng để chọn bean khi nhiều bean cùng kiểu)
 * @param type kiểu field/tham số
 * @param qualifier giá trị {@code name()}/{@code @Qualifier}, null nếu không có
 * @param mode MOCK hoặc SPY
 */
public record BeanRequest(Field field, String where, String member, ResolvableType type, String qualifier, InterceptMode mode) {

    /** Khai báo cấp class (không phải field/tham số): không có field nào để đặt tên theo bean. */
    boolean classLevel() {
        return field == null && !where.contains("(parameter ");
    }

    String describe() {
        return "@" + (mode == InterceptMode.MOCK ? "PerTestMock" : "PerTestSpy") + " " + type + " (" + where
                + (qualifier == null ? "" : ", name/@Qualifier \"" + qualifier + "\"") + ")";
    }
}
