package com.hit.ptest.context;

import java.util.Comparator;
import org.springframework.core.ResolvableType;

/**
 * Một khai báo {@code @PerTestMock} CẤP CLASS: bean phải là mock cho cả context (thay định nghĩa đã có, hoặc tạo nếu chưa có).
 * Là một phần KHÓA CONTEXT nên chỉ chứa dữ liệu quyết định cấu hình (không chứa vị trí khai báo, để cùng khai báo ở lớp cha hay lớp con
 * vẫn cho cùng khóa).
 *
 * @param name tên bean: {@code name()} nếu có, ngược lại tên lớp viết thường đầu (dùng làm tên khi phải tạo bean)
 * @param qualified true nếu tên đến từ {@code name()} (bean phải có tên/qualifier đó); false thì chọn theo kiểu
 * @param type kiểu bean
 */
public record MockOnlyBean(String name, boolean qualified, ResolvableType type) {

    static final Comparator<MockOnlyBean> ORDER = Comparator.comparing(MockOnlyBean::name).thenComparing(b -> b.type().toString())
            .thenComparing(MockOnlyBean::qualified);

    /** Dạng ngắn gọn cho diff khóa context: {@code name(Type)} hoặc {@code Type} khi chọn theo kiểu. */
    String describe() {
        return qualified ? name + " (" + type + ")" : String.valueOf(type);
    }

    @Override
    public String toString() {
        return name + ":" + type;
    }
}
